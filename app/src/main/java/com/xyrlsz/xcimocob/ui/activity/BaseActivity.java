package com.xyrlsz.xcimocob.ui.activity;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.ui.fragment.dialog.ProgressDialogFragment;
import com.xyrlsz.xcimocob.ui.view.BaseView;
import com.xyrlsz.xcimocob.ui.widget.ViewUtils;
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
        if (isNavTranslation() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
    }

    protected void initNight() {
        if (mNightMask != null) {
            boolean night = mPreference.getBoolean(PreferenceManager.PREF_NIGHT, false);
            int color = mPreference.getNumber(PreferenceManager.PREF_OTHER_NIGHT_ALPHA, 0xB0).intValue() << 24;
            mNightMask.setBackgroundColor(color);
            mNightMask.setVisibility(night ? View.VISIBLE : View.INVISIBLE);
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
                mToolbar.setPadding(0, ViewUtils.getStatusBarHeight(this), 0, mToolbar.getPaddingBottom());
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
