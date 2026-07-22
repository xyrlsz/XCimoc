package com.xyrlsz.xcimocob.ui.activity;

import static com.xyrlsz.xcimocob.Constants.GITEE_RELEASE_URL;
import static com.xyrlsz.xcimocob.Constants.GITHUB_RELEASE_URL;
import static com.xyrlsz.xcimocob.Constants.REQUEST_CODE_MANAGE_STORAGE;
import static com.xyrlsz.xcimocob.Constants.REQUEST_CODE_STORAGE;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.util.ArrayList;
import java.util.List;

import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.google.android.material.navigation.NavigationView;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.ThemeResponsive;
import com.xyrlsz.xcimocob.core.Update;
import com.xyrlsz.xcimocob.fresco.ControllerBuilderProvider;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.MainPresenter;
import com.xyrlsz.xcimocob.ui.fragment.BaseFragment;
import com.xyrlsz.xcimocob.ui.fragment.CategoryFragment;
import com.xyrlsz.xcimocob.ui.fragment.ComicFragment;
import com.xyrlsz.xcimocob.ui.fragment.dialog.MessageDialogFragment;
import com.xyrlsz.xcimocob.ui.fragment.recyclerview.SourceFragment;
import com.xyrlsz.xcimocob.ui.view.MainView;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.PermissionUtils;
import com.xyrlsz.xcimocob.utils.STConvertUtils;

import java.io.IOException;
import java.util.Objects;


import okhttp3.Request;
import okhttp3.Response;


/**
 * Created by Hiroshi on 2016/7/1.
 * fixed by Haleydu on 2020/8/8.
 */
public class MainActivity extends BaseActivity implements MainView, NavigationView.OnNavigationItemSelectedListener {

    private static final int DIALOG_REQUEST_NOTICE = 0;
    private static final int DIALOG_REQUEST_PERMISSION = 1;
    //private static final int DIALOG_REQUEST_LOGOUT = 2;

    private static final int REQUEST_ACTIVITY_SETTINGS = 0;

    private static final int FRAGMENT_NUM = 3;
    private static final String SAVED_STATE_CHECK_ITEM = "saved_state_check_item";

    // Fragment 标签，用于配置变化后恢复 Fragment
    private static final String TAG_FRAGMENT_COMIC = "fragment_comic";
    private static final String TAG_FRAGMENT_SOURCE = "fragment_source";
    private static final String TAG_FRAGMENT_CATEGORY = "fragment_category";

    private final Update update = new Update();
    private final long mExitTime = 0;
    DrawerLayout mDrawerLayout;
    NavigationView mNavigationView;
    FrameLayout mFrameLayout;
    private TextView mLastText;
    private SimpleDraweeView mDraweeView;
    private ControllerBuilderProvider mControllerBuilderProvider;
    private MainPresenter mPresenter;
    private ActionBarDrawerToggle mDrawerToggle;
    private long mLastId = -1;
    private int mLastSource = -1;
    private String mLastCid;
    private int mCheckItem;
    private SparseArray<BaseFragment> mFragmentArray;
    private BaseFragment mCurrentFragment;
    private ComicFragment mComicFragment;
    private boolean night;
    private String versionName, content, mUrl, md5;
    private int versionCode;

    //auth0
//    private Auth0 auth0;
    @Override
    protected BasePresenter initPresenter() {
        mPresenter = new MainPresenter();
        mPresenter.attachView(this);
        return mPresenter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            private long mExitTime = 0;

            @Override
            public void handleOnBackPressed() {
                if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    mDrawerLayout.closeDrawer(GravityCompat.START);
                } else if (System.currentTimeMillis() - mExitTime > 2000) {
                    HintUtils.showToast(MainActivity.this, R.string.main_double_click);
                    mExitTime = System.currentTimeMillis();
                } else {
                    App.setIsNormalExited(true);
                    finishAffinity();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    System.exit(0);
                }
            }
        };

        // 注册回调
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从 MANAGE_EXTERNAL_STORAGE 设置页面返回后重新检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            if (!PermissionUtils.hasAllPermissions(this)) {
                // MANAGE_EXTERNAL_STORAGE 已授权，但可能还需要其他运行时权限
                requestAppPermissions(this);
            } else if (((App) getApplication()).getDocumentFile() == null) {
                // 权限已就绪但文档根目录未初始化
                ((App) getApplication()).initRootDocumentFile();
            }
        }
    }

    @Override
    protected void initViewById() {
        super.initViewById();
        mDrawerLayout = findViewById(R.id.main_layout);
        mNavigationView = findViewById(R.id.main_navigation_view);
        mFrameLayout = findViewById(R.id.main_fragment_container);
    }

    @Override
    protected void initView() {
        initDrawerToggle();
        initNavigation();
        initFragment();
    }

    @Override
    protected void initData() {
        mPresenter.loadLast();

        //检查App更新
        String updateUrl;
        if (mPreference.getBoolean(PreferenceManager.PREF_UPDATE_APP_AUTO, true)) {
            if ((updateUrl = App.getPreferenceManager().getString(PreferenceManager.PREF_UPDATE_CURRENT_URL)) != null) {
                App.setUpdateCurrentUrl(updateUrl);
            }
            checkUpdate();
        }
//        mPresenter.getSourceBaseUrl();

        showPermission();

    }


    private void initDrawerToggle() {
        android.util.Log.d("MainActivity", "mDrawerLayout = " + mDrawerLayout);
        android.util.Log.d("MainActivity", "mToolbar = " + mToolbar);

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, mToolbar, 0, 0) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (refreshCurrentFragment()) {
                    getSupportFragmentManager().beginTransaction().show(mCurrentFragment).commit();
                } else {
                    getSupportFragmentManager().beginTransaction().add(R.id.main_fragment_container, mCurrentFragment).commit();
                }
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }

    private void goToLastComic() {
        if (mPresenter.checkLocal(mLastId)) {
            Intent intent = TaskActivity.createIntent(MainActivity.this, mLastId);
            startActivity(intent);
        } else if (mLastSource != -1 && mLastCid != null) {
            Intent intent = DetailActivity.createIntent(MainActivity.this, null, mLastSource, mLastCid);
            startActivity(intent);
        } else {
            HintUtils.showToast(MainActivity.this, R.string.common_execute_fail);
        }
    }

    private void initNavigation() {
        night = mPreference.getBoolean(PreferenceManager.PREF_NIGHT, false);
        mNavigationView.getMenu().findItem(R.id.drawer_night).setTitle(night ? R.string.drawer_light : R.string.drawer_night);
        mNavigationView.setNavigationItemSelectedListener(this);
        View header = mNavigationView.getHeaderView(0);
        mLastText = header.findViewById(R.id.drawer_last_title);
        mDraweeView = header.findViewById(R.id.drawer_last_cover);
        mDraweeView.setOnClickListener(v -> {
            goToLastComic();
        });
        mLastText.setOnClickListener(v -> {
            goToLastComic();
        });
        mControllerBuilderProvider = new ControllerBuilderProvider(this,
                SourceManager.getInstance(this).new HeaderGetter(), false);
    }

    private void initFragment() {
        // 如果是重新创建（如旋转屏幕），先尝试从 FragmentManager 恢复已有 Fragment
        mFragmentArray = new SparseArray<>(FRAGMENT_NUM);
        tryRestoreFragment(R.id.drawer_comic, TAG_FRAGMENT_COMIC);
        tryRestoreFragment(R.id.drawer_source, TAG_FRAGMENT_SOURCE);
        tryRestoreFragment(R.id.drawer_category, TAG_FRAGMENT_CATEGORY);

        // 如果 mCheckItem 没有被 restoreData 恢复，则根据首页设置确定
        if (mCheckItem == 0) {
            int home = mPreference.getNumber(PreferenceManager.PREF_OTHER_LAUNCH, PreferenceManager.HOME_FAVORITE).intValue();
            switch (home) {
                default:
                case PreferenceManager.HOME_FAVORITE:
                case PreferenceManager.HOME_HISTORY:
                case PreferenceManager.HOME_DOWNLOAD:
                    mCheckItem = R.id.drawer_comic;
                    break;
                case PreferenceManager.HOME_SOURCE:
                    mCheckItem = R.id.drawer_source;
                    break;
            }
        }

        mNavigationView.setCheckedItem(mCheckItem);
        refreshCurrentFragment();
        // 只在 Fragment 尚未添加时执行添加操作
        if (!isFragmentRestored(mCheckItem)) {
            String tag = getFragmentTag(mCheckItem);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.main_fragment_container, mCurrentFragment, tag)
                    .commit();
        }
    }

    /**
     * 尝试从 FragmentManager 中按 tag 恢复已存在的 Fragment
     */
    private void tryRestoreFragment(int checkItem, String tag) {
        BaseFragment fragment = (BaseFragment) getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment != null) {
            mFragmentArray.put(checkItem, fragment);
            if (checkItem == R.id.drawer_comic) {
                mComicFragment = (ComicFragment) fragment;
            }
        }
    }

    /**
     * 判断指定 checkItem 对应的 Fragment 是否已从 FragmentManager 中恢复
     */
    private boolean isFragmentRestored(int checkItem) {
        return getSupportFragmentManager().findFragmentByTag(getFragmentTag(checkItem)) != null;
    }

    private String getFragmentTag(int checkItem) {
        int __id = checkItem;
        if (__id == R.id.drawer_comic) {
                return TAG_FRAGMENT_COMIC;
        } else if (__id == R.id.drawer_source) {
                return TAG_FRAGMENT_SOURCE;
        } else if (__id == R.id.drawer_category) {
                return TAG_FRAGMENT_CATEGORY;
        } else {
                return TAG_FRAGMENT_COMIC;
        }
    }

    private boolean refreshCurrentFragment() {
        mCurrentFragment = mFragmentArray.get(mCheckItem);
        if (mCurrentFragment == null) {
            String tag = getFragmentTag(mCheckItem);
            int __id = mCheckItem;
            if (__id == R.id.drawer_comic) {
                    mComicFragment = new ComicFragment();
                    mCurrentFragment = mComicFragment;
            } else if (__id == R.id.drawer_source) {
                    mCurrentFragment = new SourceFragment();
            } else if (__id == R.id.drawer_category) {
                    mCurrentFragment = new CategoryFragment();
            }
            mFragmentArray.put(mCheckItem, mCurrentFragment);
            return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mControllerBuilderProvider.clear();
        ((App) getApplication()).getBuilderProvider().clear();
        ((App) getApplication()).getGridRecycledPool().clear();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    protected void saveState(Bundle outState) {
        super.saveState(outState);
        outState.putInt(SAVED_STATE_CHECK_ITEM, mCheckItem);
    }

    @Override
    protected void restoreData(Bundle savedInstanceState) {
        super.restoreData(savedInstanceState);
        if (savedInstanceState.containsKey(SAVED_STATE_CHECK_ITEM)) {
            mCheckItem = savedInstanceState.getInt(SAVED_STATE_CHECK_ITEM);
        }
    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId != mCheckItem) {
            int __id = itemId;
            if (__id == R.id.drawer_comic) {
                    String title = "";
                    if (mComicFragment == null) {
                        mComicFragment = new ComicFragment(getBaseContext());
                        title = mComicFragment.getCurrTitle();
                    }
                    Objects.requireNonNull(mToolbarTitle).setText(title.isEmpty() ? mComicFragment.getCurrTitle() : title);
                    mCheckItem = itemId;
                    getSupportFragmentManager().beginTransaction().hide(mCurrentFragment).commit();
                    mDrawerLayout.closeDrawer(GravityCompat.START);
            } else if (__id == R.id.drawer_source) {
//                case R.id.drawer_tag:
                    mCheckItem = itemId;
                    getSupportFragmentManager().beginTransaction().hide(mCurrentFragment).commit();
                    if (itemId == R.id.drawer_source) {
                        Objects.requireNonNull(mToolbarTitle).setText(Objects.requireNonNull(item.getTitle()).toString());
                    }
                    mDrawerLayout.closeDrawer(GravityCompat.START);
            } else if (__id == R.id.drawer_category) {
                    mCheckItem = itemId;
                    getSupportFragmentManager().beginTransaction().hide(mCurrentFragment).commit();
                    Objects.requireNonNull(mToolbarTitle).setText(Objects.requireNonNull(item.getTitle()).toString());
                    mDrawerLayout.closeDrawer(GravityCompat.START);
            } else if (__id == R.id.drawer_comiclist) {
                    Intent intentBaidu = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.home_page_comiclist_url)));
                    try {
                        startActivity(intentBaidu);
                    } catch (Exception e) {
                        showSnackbar(R.string.about_resource_fail);
                    }
            } else if (__id == R.id.drawer_comicUpdate) {
//                    update.startUpdate(versionName, content, mUrl, versionCode, md5);
                    new Thread(() -> {
                        boolean checkGithubOk = false;
                        try {
                            Request request = new Request.Builder().url(GITHUB_RELEASE_URL).build();
                            Response response = App.getHttpClient().newCall(request).execute();
                            if (response.isSuccessful()) {
                                checkGithubOk = true;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        String releaseUrl;
                        if (checkGithubOk) {
                            releaseUrl = GITHUB_RELEASE_URL;
                        } else {
                            releaseUrl = GITEE_RELEASE_URL;
                        }
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(releaseUrl));
                        startActivity(intent);
                    }).start();
            } else if (__id == R.id.drawer_night) {
                    onNightSwitch();
                    mPreference.putBoolean(PreferenceManager.PREF_NIGHT, night);
            } else if (__id == R.id.drawer_settings) {
                    startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), REQUEST_ACTIVITY_SETTINGS);
            } else if (__id == R.id.drawer_about) {
                    startActivity(new Intent(MainActivity.this, AboutActivity.class));
            } else if (__id == R.id.drawer_backup) {
                    startActivity(new Intent(MainActivity.this, BackupActivity.class));
//                case R.id.user_info:
//                    loginout();
//                    break;
            }
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mCurrentFragment.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_ACTIVITY_SETTINGS:
                    int[] result = data.getIntArrayExtra(Extra.EXTRA_RESULT);
                    if (Objects.requireNonNull(result)[0] == 1) {
                        changeTheme(result[1], result[2], result[3]);
                    }
                    if (result[4] == 1 && mNightMask != null) {
                        mNightMask.setBackgroundColor(result[5] << 24);
                    }
                    // 深色模式变更 → 重建 Activity 使新主题生效
                    if (result.length > 6 && result[6] == 1) {
                        recreate();
                    }
                    break;
            }
        }
        // 处理 API 30+ MANAGE_EXTERNAL_STORAGE 设置返回
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            onStoragePermissionSettingsReturn();
        }
    }

    /**
     * 从 MANAGE_EXTERNAL_STORAGE 或应用详情设置返回后，重新检查权限
     */
    private void onStoragePermissionSettingsReturn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            // MANAGE_EXTERNAL_STORAGE 已授权
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+ 还需要检查是否有 READ_MEDIA_IMAGES
                if (PermissionUtils.hasAllPermissions(this)) {
                    ((App) getApplication()).initRootDocumentFile();
                    HintUtils.showToast(this, R.string.main_permission_success);
                } else {
                    requestAppPermissions(this);
                }
            } else {
                // API 30-32: MANAGE_EXTERNAL_STORAGE 已足够
                ((App) getApplication()).initRootDocumentFile();
                HintUtils.showToast(this, R.string.main_permission_success);
            }
        }
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        switch (requestCode) {
            case DIALOG_REQUEST_NOTICE:
                mPreference.putBoolean(PreferenceManager.PREF_MAIN_NOTICE, true);
                //showPermission();
                break;
            case DIALOG_REQUEST_PERMISSION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    // API 30+ 需要 MANAGE_EXTERNAL_STORAGE → 跳转系统设置页面
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
                } else if (PermissionUtils.shouldShowPermissionRationale(this)) {
                    // 可以显示 rationale → 正常请求权限
                    requestAppPermissions(this);
                } else if (!PermissionUtils.hasAllPermissions(this)) {
                    // 权限被永久拒绝（或 MIUI 等 ROM 拦截）→ 引导去应用详情设置
                    HintUtils.showToast(this, R.string.main_permission_guide_settings);
                    openAppSettings(this);
                } else {
                    requestAppPermissions(this);
                }
                break;

//            case DIALOG_REQUEST_LOGOUT:
//                logout();
//                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                ((App) getApplication()).initRootDocumentFile();
                HintUtils.showToast(this, R.string.main_permission_success);
            } else {
                HintUtils.showToast(this, R.string.main_permission_fail);
            }
        }
    }

    @Override
    public void onNightSwitch() {
        night = !night;
        mNavigationView.getMenu().findItem(R.id.drawer_night).setTitle(night ? R.string.drawer_light : R.string.drawer_night);
        if (mNightMask != null) {
            mNightMask.setVisibility(night ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    public void onUpdateReady() {
        HintUtils.showToast(this, R.string.main_ready_update);
        if (mPreference.getBoolean(PreferenceManager.PREF_OTHER_CHECK_SOFTWARE_UPDATE, true)) {
            mNavigationView.getMenu().findItem(R.id.drawer_comicUpdate).setVisible(true);
        }
//        Update.update(this);
    }

    @Override
    public void onUpdateReady(String versionName, String content, String mUrl, int versionCode, String md5) {
        this.versionName = versionName;
        this.content = content;
        this.mUrl = mUrl;
        this.md5 = md5;
        this.versionCode = versionCode;
        if (mPreference.getBoolean(PreferenceManager.PREF_OTHER_CHECK_SOFTWARE_UPDATE, true)) {
            mNavigationView.getMenu().findItem(R.id.drawer_comicUpdate).setVisible(true);
            update.startUpdate(versionName, content, mUrl, versionCode, md5);
        } else {
            HintUtils.showToast(this, R.string.main_ready_update);
        }
    }

    @Override
    public void onLastLoadSuccess(long id, int source, String cid, String title, String cover) {
        onLastChange(id, source, cid, title, cover);
    }

    @Override
    public void onLastLoadFail() {
        HintUtils.showToast(this, R.string.main_last_read_fail);
    }

    @Override
    public void onLastChange(long id, int source, String cid, String title, String cover) {
        mLastId = id;
        mLastSource = source;
        mLastCid = cid;
        mLastText.setText(STConvertUtils.convert(title));
        ImageRequest request = ImageRequestBuilder
                .newBuilderWithSource(Uri.parse(cover))
                .setResizeOptions(new ResizeOptions(App.mWidthPixels, App.mHeightPixels))
                .build();
        DraweeController controller = mControllerBuilderProvider.get(source)
                .setOldController(mDraweeView.getController())
                .setImageRequest(request)
                .build();
        mDraweeView.setController(controller);
    }

    private void changeTheme(@StyleRes int theme, @ColorRes int primary, @ColorRes int accent) {
        setTheme(theme);
        ColorStateList itemList = new ColorStateList(new int[][]{{-android.R.attr.state_checked},
                {android.R.attr.state_checked}},
                new int[]{Color.BLACK, ContextCompat.getColor(this, accent)});
        mNavigationView.setItemTextColor(itemList);
        ColorStateList iconList = new ColorStateList(new int[][]{{-android.R.attr.state_checked},
                {android.R.attr.state_checked}},
                new int[]{0x8A000000, ContextCompat.getColor(this, accent)});
        mNavigationView.setItemIconTintList(iconList);
        mNavigationView.getHeaderView(0).setBackgroundColor(ContextCompat.getColor(this, primary));
        if (mToolbar != null) {
            mToolbar.setBackgroundColor(ContextCompat.getColor(this, primary));
        }

        for (int i = 0; i < mFragmentArray.size(); ++i) {
            ((ThemeResponsive) mFragmentArray.valueAt(i)).onThemeChange(primary, accent);
        }
    }

    private void showPermission() {
        if (!PermissionUtils.hasAllPermissions(this)) {
            MessageDialogFragment fragment = MessageDialogFragment.newInstance(R.string.main_permission,
                    R.string.main_permission_content, false, DIALOG_REQUEST_PERMISSION);
            fragment.show(getSupportFragmentManager(), null);
        }
    }

    public static void requestAppPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+): 使用细粒度媒体权限
            // 不需要 MANAGE_EXTERNAL_STORAGE，只需要 READ_MEDIA_IMAGES 读取漫画图片
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.POST_NOTIFICATIONS
            }, REQUEST_CODE_STORAGE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 (API 30-32): 需要 MANAGE_EXTERNAL_STORAGE
            // 注意：MANAGE_EXTERNAL_STORAGE 通过 Settings Intent 申请，不在 requestPermissions 中
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivity(intent);
            }
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.READ_PHONE_STATE
            }, REQUEST_CODE_STORAGE);
        } else {
            // Android 10 及以下 (API < 30): 传统存储权限
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE
            }, REQUEST_CODE_STORAGE);
        }
    }

    public static void openAppSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(intent);
    }

    private void checkUpdate() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
//            mPresenter.checkGiteeUpdate(info.versionCode);
            mPresenter.checkUpdate(info.versionName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected String getDefaultTitle() {
        int home = mPreference.getNumber(PreferenceManager.PREF_OTHER_LAUNCH, PreferenceManager.HOME_FAVORITE).intValue();
        switch (home) {
            default:
            case PreferenceManager.HOME_FAVORITE:
                return getString(R.string.comic_tab_favorite);
            case PreferenceManager.HOME_HISTORY:
                return getString(R.string.comic_tab_history);
            case PreferenceManager.HOME_DOWNLOAD:
                return getString(R.string.comic_tab_download);
            case PreferenceManager.HOME_LOCAL:
//                return getString(R.string.drawer_comic);
                return getString(R.string.comic_tab_local);
            case PreferenceManager.HOME_SOURCE:
                return getString(R.string.drawer_source);
//            case PreferenceManager.HOME_TAG:
//                return getString(R.string.drawer_tag);
        }
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_main;
    }

    @Override
    protected View getLayoutView() {
        return mDrawerLayout;
    }
}
