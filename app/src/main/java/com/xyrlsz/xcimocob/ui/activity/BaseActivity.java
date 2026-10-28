package com.xyrlsz.xcimocob.ui.activity;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.ui.fragment.dialog.ProgressDialogFragment;
import com.xyrlsz.xcimocob.ui.view.BaseView;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;
import com.xyrlsz.xcimocob.utils.ThreadRunUtils;




/**
 * Created by Hiroshi on 2016/7/1.
 */
public abstract class BaseActivity extends AppCompatActivity implements BaseView {

    protected static final String SAVED_STATE_NIGHT_MODE = "saved_state_night_mode";

    protected PreferenceManager mPreference;
    @Nullable
    View mNightMask;
    @Nullable
    Toolbar mToolbar;
    @Nullable
    TextView mToolbarTitle;
    private ProgressDialogFragment mProgressDialog;
    private BasePresenter mBasePresenter;
    /** 是否已把内容绘制到系统导航栏下方（见 {@link #applyTranslucentNavigationBar()}） */
    private boolean mEdgeToEdgeNavigationBar = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initAdMob();
        mPreference = App.getPreferenceManager();
        initTheme();
        setContentView(getLayoutRes());
        initViewById();
        initNight();
        initToolbar();
        mBasePresenter = initPresenter();
        mProgressDialog = ProgressDialogFragment.newInstance();
        initView();
        // 配置变化时（旋转屏幕等），先恢复已保存的状态再加载数据
        if (savedInstanceState != null) {
            restoreData(savedInstanceState);
        }
        initData();
        initUser();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_STATE_NIGHT_MODE,
                mPreference.getBoolean(PreferenceManager.PREF_NIGHT, false));
        saveState(outState);
    }

    /**
     * 子类重写此方法保存额外状态
     */
    protected void saveState(Bundle outState) {
    }

    /**
     * 子类重写此方法恢复额外状态
     * 注意：仅在 {@code savedInstanceState != null} 时调用
     */
    protected void restoreData(Bundle savedInstanceState) {
    }

    @Override
    protected void onDestroy() {
        if (mBasePresenter != null) {
            mBasePresenter.detachView();
        }
        super.onDestroy();
    }

    @Override
    public App getAppInstance() {
        return App.getApp();
    }

    @Override
    public void onNightSwitch() {
        initNight();
    }

    protected void initViewById() {
        mNightMask = findViewById(R.id.custom_night_mask);
        mToolbar = findViewById(R.id.custom_toolbar);
        mToolbarTitle = findViewById(R.id.toolbar_title);
    }

    protected void initTheme() {
        int theme = ThemeUtils.getThemeId();
        setTheme(ThemeUtils.getThemeById(theme));
        if (isNavTranslation()) {
            applyTranslucentNavigationBar();
        }
    }

    /**
     * 内容延伸到系统导航栏下方，并关闭系统自动叠加的深色遮罩。
     * 不再使用已废弃的 FLAG_TRANSLUCENT_NAVIGATION：从 Android 10 起，系统会为半透明的
     * 导航栏再绘制一层半透明黑色遮罩，导致页面底部多出一条灰黑色横条。
     */
    protected void applyTranslucentNavigationBar() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Android 8.0 以下不支持深色导航栏图标，此时浅色背景下会看不见手势条，沿用旧行为
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            return;
        }
        mEdgeToEdgeNavigationBar = true;
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    protected void initNight() {
        boolean night = mPreference.getBoolean(PreferenceManager.PREF_NIGHT, false);
        if (mNightMask != null) {
            int color = mPreference.getNumber(PreferenceManager.PREF_OTHER_NIGHT_ALPHA, 0xB0).intValue() << 24;
            mNightMask.setBackgroundColor(color);
            mNightMask.setVisibility(night ? View.VISIBLE : View.INVISIBLE);
        }
        if (mEdgeToEdgeNavigationBar) {
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            // 导航栏已透明，图标颜色需跟随明暗模式，否则浅色背景下看不到手势条
            controller.setAppearanceLightNavigationBars(!night);
        }
    }

    protected void initToolbar() {
        if (mToolbar != null) {
            mToolbar.setTitle("");
            if (mToolbarTitle != null) {
                String title = getDefaultTitle();
                if (title != null) {
                    mToolbarTitle.setText(title);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                ViewCompat.setOnApplyWindowInsetsListener(mToolbar, (v, insets) -> {
                    // 一并扣除挖孔安全区：横屏时前置摄像头可能在侧边，会盖住标题栏右侧的菜单
                    Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
                    v.setPadding(safe.left, safe.top, safe.right, v.getPaddingBottom());
                    return insets;
                });
            }
//            mToolbar.setFitsSystemWindows(true);
            setSupportActionBar(mToolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }

        }
    }

    protected View getLayoutView() {
        return null;
    }

    protected String getDefaultTitle() {
        return null;
    }


    protected BasePresenter initPresenter() {
        return null;
    }

    protected void initView() {
    }

    protected void initData() {
    }

    protected void initUser() {
    }

    protected void initAdMob() {
    }

    protected abstract int getLayoutRes();

    protected boolean isNavTranslation() {
        return false;
    }

    protected void showSnackbar(String msg) {
        HintUtils.showSnackbar(getLayoutView(), msg);
    }

    protected void showSnackbar(int resId) {
        showSnackbar(getString(resId));
    }

    public void showProgressDialog() {
        ThreadRunUtils.runOnMainThread(() -> {
            if (!isFinishing() && !mProgressDialog.isAdded()) {
                mProgressDialog.show(getSupportFragmentManager(), null);
            }
        });
    }

    public void hideProgressDialog() {
        ThreadRunUtils.runOnMainThread(() -> {
            try {
                // dismissAllowingStateLoss 在 Fragment 未 added 时也能安全处理：
                // - 如果 show() 已调用但事务未执行，getParentFragmentManager() 仍有效，
                //   会提交一个移除事务，等添加执行后立即移除对话框
                // - 如果 Fragment 从未添加，getParentFragmentManager() 返回 null，无操作
                // - 在 onSaveInstanceState 后调用 commitAllowingStateLoss 也是安全的
                mProgressDialog.dismissAllowingStateLoss();
            } catch (Exception e) {
                // 极少数情况下（如 Activity 已销毁）忽略异常
            }
        });
    }

}
