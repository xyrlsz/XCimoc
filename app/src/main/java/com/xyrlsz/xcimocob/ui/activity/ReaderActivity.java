package com.xyrlsz.xcimocob.ui.activity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.imagepipeline.core.ImagePipelineFactory;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.fresco.ControllerBuilderSupplierFactory;
import com.xyrlsz.xcimocob.fresco.ImagePipelineFactoryBuilder;
import com.xyrlsz.xcimocob.global.ClickEvents;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.ReaderPresenter;
import com.xyrlsz.xcimocob.ui.activity.settings.ReaderConfigActivity;
import com.xyrlsz.xcimocob.ui.adapter.ReaderAdapter;
import com.xyrlsz.xcimocob.ui.adapter.ReaderAdapter.OnLazyLoadListener;
import com.xyrlsz.xcimocob.ui.view.ReaderView;
import com.xyrlsz.xcimocob.ui.widget.OnTapGestureListener;
import com.xyrlsz.xcimocob.ui.widget.PreCacheLayoutManager;
import com.xyrlsz.xcimocob.ui.widget.RetryDraweeView;
import com.xyrlsz.xcimocob.ui.widget.SeekBar;
import com.xyrlsz.xcimocob.utils.FrescoUtils;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.STConvertUtils;
import com.xyrlsz.xcimocob.utils.StringUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Headers;

/**
 * Created by Hiroshi on 2016/8/6.
 */
public abstract class ReaderActivity extends BaseActivity implements OnTapGestureListener,
        Slider.OnChangeListener, OnLazyLoadListener, ReaderView {

    private static final String SAVED_STATE_PROGRESS = "saved_state_progress";
    private static final String SAVED_STATE_MAX = "saved_state_max";

    /**
     * 章节列表内存缓存。
     * <p>
     * 章节列表可能包含成百上千个 Chapter（每个含 title/path 字符串），
     * 若直接通过 Intent 的 ParcelableArrayList 传递，会触发 Binder 事务过大
     * （Large outgoing transaction of ~600KB），导致系统杀死进程并重启。
     * 改为：调用方将列表放入此缓存，Intent 仅传递一个 long 型 key，
     * ReaderActivity 在 initData 中取出（缓存保留到 Activity 真正结束，兼容系统重建）。
     * 若缓存缺失（如进程被回收），则回退到从数据库按漫画 ID 加载章节列表。
     */
    private static final ConcurrentHashMap<Long, List<Chapter>> sChapterCache = new ConcurrentHashMap<>();
    private static final AtomicLong sChapterKeyGenerator = new AtomicLong(0);

    private final boolean[] JoyLock = {false, false};
    private final int[] JoyEvent = {7, 8};
    protected PreCacheLayoutManager mLayoutManager;
    protected ReaderAdapter mReaderAdapter;
    protected ImagePipelineFactory mImagePipelineFactory;
    protected ImagePipelineFactory mLargeImagePipelineFactory;
    protected ReaderPresenter mPresenter;
    protected int mLastDx = 0;
    protected int mLastDy = 0;
    protected int progress = 1;
    protected int max = 1;
    protected int turn;
    protected int orientation;
    protected int mode;
    protected boolean mLoadPrev;
    protected boolean mLoadNext;
    TextView mChapterTitle;
    TextView mChapterPage;
    TextView mBatteryText;
    View mBatteryIconFill;
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra("level", 0);
                int scale = intent.getIntExtra("scale", 100);
                if (scale <= 0) {
                    scale = 100;
                }
                int percent = level * 100 / scale;
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                String text = percent + "%";
                mBatteryText.setText(text);
                updateBatteryIcon(percent, charging);
            }
        }
    };
    View mProgressLayout;
    View mBackLayout;
    View mInfoLayout;
    SeekBar mSeekBar;
    TextView mLoadingText;
    View mLoadingLayout;
    RecyclerView mRecyclerView;
    RelativeLayout mReaderBox;
    ProgressBar mLoadingIcon;
    private boolean isSavingPicture = false;
    private boolean mHideInfo;
    private boolean mHideNav;
    private boolean mShowTopbar;
    private int[] mClickArray;
    private int[] mLongClickArray;
    private int _source;
    private boolean _local;
    private float mControllerTrigThreshold = 0.3f;
    private BottomSheetDialog mSettingsSheet;
    private float mSwipeDownY = 0f;
    private long mChapterKey = -1;

    public static Intent createIntent(Context context, long id, List<Chapter> list, int mode) {
        Intent intent = getIntent(context, mode);
        intent.putExtra(Extra.EXTRA_ID, id);
        // 不再通过 Intent 传递整个 List<Chapter>（会导致 Binder 事务过大），
        // 改为放入进程内缓存，Intent 仅传递 key。
        long key = sChapterKeyGenerator.incrementAndGet();
        sChapterCache.put(key, list);
        intent.putExtra(Extra.EXTRA_CHAPTER_KEY, key);
        intent.putExtra(Extra.EXTRA_MODE, mode);
        return intent;
    }

    private static Intent getIntent(Context context, int mode) {
        if (mode == PreferenceManager.READER_MODE_PAGE) {
            return new Intent(context, PageReaderActivity.class);
        } else {
            return new Intent(context, StreamReaderActivity.class);
        }
    }

    /**
     * 更新电池图标填充量（0~100，竖向从底部向上）与颜色：
     * 充电→绿；<10%→红；<20%→橙；其余→白
     */
    private void updateBatteryIcon(int percent, boolean charging) {
        if (mBatteryIconFill == null) {
            return;
        }
        // 图标容器固定 18dp，填充左右各缩进 5.5dp、底部缩进 2.85dp，最大填充高度 11.5dp
        float density = getResources().getDisplayMetrics().density;
        int maxFillPx = Math.round(11.5f * density);
        int fillH = Math.max(0, maxFillPx * percent / 100);
        ViewGroup.LayoutParams lp = mBatteryIconFill.getLayoutParams();
        if (lp != null && lp.height != fillH) {
            lp.height = fillH;
            mBatteryIconFill.setLayoutParams(lp);
        }
        // 填充颜色：充电优先，其次按低电量阈值
        int color;
        if (charging) {
            color = 0xFF4CAF50;      // 绿
        } else if (percent < 10) {
            color = 0xFFF44336;      // 红
        } else if (percent < 20) {
            color = 0xFFFFA726;      // 橙
        } else {
            color = 0xFFFFFFFF;      // 白
        }
        Drawable bg = mBatteryIconFill.getBackground();
        if (bg instanceof GradientDrawable) {
            ((GradientDrawable) bg).setColor(color);
        }
    }

    @Override
    protected void initTheme() {
        super.initTheme();
        mHideNav = mPreference.getBoolean(PreferenceManager.PREF_READER_HIDE_NAV, false);
        mShowTopbar = mPreference.getBoolean(PreferenceManager.PREF_OTHER_SHOW_TOPBAR, false);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // 提前应用系统栏可见性，避免首帧闪烁（用 WindowInsetsControllerCompat 替代已废弃的 FLAG_FULLSCREEN）
        applySystemBarVisibility();
        if (mPreference.getBoolean(PreferenceManager.PREF_READER_KEEP_BRIGHT, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        mode = getIntent().getIntExtra(Extra.EXTRA_MODE, PreferenceManager.READER_MODE_PAGE);
        String key = mode == PreferenceManager.READER_MODE_PAGE ?
                PreferenceManager.PREF_READER_PAGE_ORIENTATION : PreferenceManager.PREF_READER_STREAM_ORIENTATION;
        orientation = mPreference.getNumber(key, PreferenceManager.READER_ORIENTATION_PORTRAIT).intValue();
        final int[] oArray = {ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED};
        setRequestedOrientation(oArray[orientation]);
    }

    @Override
    protected BasePresenter initPresenter() {
        mPresenter = new ReaderPresenter();
        mPresenter.attachView(this);

        return mPresenter;
    }

    @Override
    protected void initViewById() {
        super.initViewById();
        mChapterTitle = findViewById(R.id.reader_chapter_title);
        mChapterPage = findViewById(R.id.reader_chapter_page);
        mBatteryText = findViewById(R.id.reader_battery);
        mBatteryIconFill = findViewById(R.id.reader_battery_icon_fill);
        mProgressLayout = findViewById(R.id.reader_progress_layout);
        mBackLayout = findViewById(R.id.reader_back_layout);
        mInfoLayout = findViewById(R.id.reader_info_layout);
        mSeekBar = findViewById(R.id.reader_seek_bar);
        mLoadingText = findViewById(R.id.reader_loading);
        mLoadingLayout = findViewById(R.id.reader_loading_layout);
        mRecyclerView = findViewById(R.id.reader_recycler_view);
        mReaderBox = findViewById(R.id.reader_box);
        mLoadingIcon = findViewById(R.id.reader_loading_icon);
    }

    @Override
    protected void initView() {
        // 手动处理系统栏 insets（替代布局里的 fitsSystemWindows）：
        // 状态栏 / 导航栏可见时给 reader_box 留出对应高度的 padding；
        // 隐藏时 insets 归零、padding 归零，避免隐藏导航栏后底部残留一大段空白。
        ViewCompat.setOnApplyWindowInsetsListener(mReaderBox, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, systemBars.bottom);
            return insets;
        });
        boolean isWhiteBackground = App.getPreferenceManager().getBoolean(PreferenceManager.PREF_READER_WHITE_BACKGROUND, true);
        if (isWhiteBackground) {
            mLoadingText.setTextColor(getResources().getColor(R.color.black));
            Drawable customDrawable = AppCompatResources.getDrawable(this, R.drawable.reader_progress_2);
            mLoadingIcon.setIndeterminateDrawable(customDrawable);
        }
        mHideInfo = mPreference.getBoolean(PreferenceManager.PREF_READER_HIDE_INFO, false);
        mControllerTrigThreshold = mPreference.getNumber(PreferenceManager.PREF_READER_CONTROLLER_TRIG_THRESHOLD, 30).intValue() * 0.01f;
        mInfoLayout.setVisibility(mHideInfo ? View.INVISIBLE : View.VISIBLE);
        // 在切换主题前保存当前主题的颜色，防止 setTheme 后无法正确解析
        int savedPrimaryColorResId = ThemeUtils.getResourceId(this, R.attr.colorPrimary);
        // 防止miui及其他魔改ROM启用反色
        setTheme(R.style.AppThemeNoDark);
        String key = mode == PreferenceManager.READER_MODE_PAGE ?
                PreferenceManager.PREF_READER_PAGE_TURN : PreferenceManager.PREF_READER_STREAM_TURN;
        turn = mPreference.getNumber(key, PreferenceManager.READER_TURN_LTR).intValue();
        if (mPreference.getBoolean(PreferenceManager.PREF_READER_WHITE_BACKGROUND, true)) {
            mReaderBox.setBackgroundResource(R.color.white);
        }
        initSeekBar(savedPrimaryColorResId);

        initLayoutManager();
        initReaderAdapter();
        mRecyclerView.setItemAnimator(null);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mReaderAdapter);


        mRecyclerView.setItemViewCacheSize(2);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NotNull RecyclerView recyclerView, int dx, int dy) {
                mLastDx = dx;
                mLastDy = dy;
            }
        });
        findViewById(R.id.reader_back_btn).setOnClickListener(v -> onBackClick());
        initSwipeUpSettingsHint();
    }

    /**
     * 底部栏上滑手柄：点击或向上滑动打开快捷设置面板
     */
    @SuppressLint("ClickableViewAccessibility")
    private void initSwipeUpSettingsHint() {
        View swipeHint = findViewById(R.id.reader_swipe_hint);
        if (swipeHint == null) {
            return;
        }
        swipeHint.setOnClickListener(v -> showReaderSettings());
        swipeHint.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mSwipeDownY = event.getRawY();
                    break;
                case MotionEvent.ACTION_UP:
                    float distance = mSwipeDownY - event.getRawY();
                    if (distance > ViewConfiguration.get(this).getScaledTouchSlop() * 2) {
                        showReaderSettings();
                    }
                    break;
            }
            return false;
        });
    }


    private void applySystemBarVisibility() {
        if (getWindow() == null) {
            return;
        } else {
            getWindow().getDecorView();
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (mHideNav) {
            // 隐藏底部导航栏
            controller.hide(WindowInsetsCompat.Type.navigationBars());
            // 边缘滑动可临时唤出，几秒后自动隐藏（对应原来的 IMMERSIVE_STICKY 效果）
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            controller.show(WindowInsetsCompat.Type.navigationBars());
        }
        if (mShowTopbar) {
            // 显示状态栏
            controller.show(WindowInsetsCompat.Type.statusBars());
        } else {
            // 隐藏状态栏
            controller.hide(WindowInsetsCompat.Type.statusBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // 重新获得焦点时（进入页面 / 从弹窗返回）重新应用系统栏可见性；
        // 失去焦点时不做处理，避免在弹窗、输入法弹出时强制隐藏系统栏
        if (hasFocus) {
            applySystemBarVisibility();
        }
    }

    private void initSeekBar(int primaryColorResId) {
        mSeekBar.setLayoutDirection(turn == PreferenceManager.READER_TURN_RTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mSeekBar.addOnChangeListener(this);
        if (primaryColorResId != 0) {
            int primaryColor = ContextCompat.getColor(this, primaryColorResId);
            mSeekBar.setTrackActiveTintList(ColorStateList.valueOf(primaryColor));
            mSeekBar.setThumbTintList(ColorStateList.valueOf(primaryColor));
            mSeekBar.setHaloTintList(ColorStateList.valueOf(primaryColor));
        }
        mSeekBar.setTrackInactiveTintList(ColorStateList.valueOf(android.graphics.Color.WHITE));
    }

    private void initReaderAdapter() {
        mReaderAdapter = new ReaderAdapter(this, new LinkedList<>());
        mReaderAdapter.setTapGestureListener(this);
        mReaderAdapter.setLazyLoadListener(this);
        mReaderAdapter.setScaleFactor(mPreference.getNumber(PreferenceManager.PREF_READER_SCALE_FACTOR, 200).intValue() * 0.01f);
        mReaderAdapter.setDoubleTap(!mPreference.getBoolean(PreferenceManager.PREF_READER_BAN_DOUBLE_CLICK, false));
        mReaderAdapter.setVertical(turn == PreferenceManager.READER_TURN_ATB);
        if (App.getPreferenceManager().getNumber(PreferenceManager.PREF_READER_MODE, PreferenceManager.READER_MODE_PAGE).intValue() == PreferenceManager.READER_MODE_STREAM
                &&
                App.getPreferenceManager().getBoolean(PreferenceManager.PREF_READER_PAGING_STREAM_OFF, false)) {
            mReaderAdapter.setPaging(false);
            mReaderAdapter.setPagingReverse(false);
        } else {
            mReaderAdapter.setPaging(mPreference.getBoolean(PreferenceManager.PREF_READER_PAGING, false));
            mReaderAdapter.setPagingReverse(mPreference.getBoolean(PreferenceManager.PREF_READER_PAGING_REVERSE, false));
        }
        mReaderAdapter.setCloseAutoResizeImage(mPreference.getBoolean(PreferenceManager.PREF_READER_CLOSEAUTORESIZEIMAGE, false));
        mReaderAdapter.setWhiteEdge(mPreference.getBoolean(PreferenceManager.PREF_READER_WHITE_EDGE, false));
        mReaderAdapter.setBanTurn(mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_BAN_TURN, false));
    }

    private void initLayoutManager() {
        mLayoutManager = new PreCacheLayoutManager(this);
        mLayoutManager.setOrientation(turn == PreferenceManager.READER_TURN_ATB ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL);
        mLayoutManager.setReverseLayout(turn == PreferenceManager.READER_TURN_RTL);
        mLayoutManager.setExtraSpace(2);

    }

    @Override
    protected void initData() {
        try {
            mClickArray = mode == PreferenceManager.READER_MODE_PAGE ?
                    ClickEvents.getPageClickEventChoice(mPreference) : ClickEvents.getStreamClickEventChoice(mPreference);
            mLongClickArray = mode == PreferenceManager.READER_MODE_PAGE ?
                    ClickEvents.getPageLongClickEventChoice(mPreference) : ClickEvents.getStreamLongClickEventChoice(mPreference);
            long id = getIntent().getLongExtra(Extra.EXTRA_ID, -1);
            long key = getIntent().getLongExtra(Extra.EXTRA_CHAPTER_KEY, -1);
            // 读取但不移除缓存：Activity 被系统重建（「不保留活动」/内存回收）后仍能读到同一列表；
            // 缓存改在 Activity 真正结束时（onDestroy）清理。
            mChapterKey = key;
            List<Chapter> list = key != -1 ? sChapterCache.get(key) : null;
            Chapter[] array = (list != null && !list.isEmpty())
                    ? list.toArray(new Chapter[0]) : null;
            mPresenter.loadInit(id, array);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void saveState(Bundle outState) {
        super.saveState(outState);
        outState.putInt(SAVED_STATE_PROGRESS, progress);
        outState.putInt(SAVED_STATE_MAX, max);
    }

    @Override
    protected void restoreData(Bundle savedInstanceState) {
        super.restoreData(savedInstanceState);
        if (savedInstanceState.containsKey(SAVED_STATE_PROGRESS)) {
            progress = savedInstanceState.getInt(SAVED_STATE_PROGRESS);
            max = savedInstanceState.getInt(SAVED_STATE_MAX);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPresenter != null) {
            mPresenter.updateComic(progress);
        }
        unregisterReceiver(batteryReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mImagePipelineFactory != null) {
            mImagePipelineFactory.getImagePipeline().clearMemoryCaches();
        }
        if (mLargeImagePipelineFactory != null) {
            mLargeImagePipelineFactory.getImagePipeline().clearMemoryCaches();
        }
        // 正常结束（isFinishing）才清理章节列表缓存；被系统重建销毁时（isFinishing=false）保留，
        // 使重建后的实例仍能通过同一 key 取到章节列表。
        if (isFinishing() && mChapterKey != -1) {
            sChapterCache.remove(mChapterKey);
        }
    }

    void onBackClick() {
        getOnBackPressedDispatcher().onBackPressed();
    }

    @Override
    public void onLoad(ImageUrl imageUrl) {
        mPresenter.lazyLoad(imageUrl);
    }

    protected void hideControl() {
        if (mProgressLayout.isShown()) {
            // 底部进度栏：向下滑出并淡出
            playHideAnimation(mProgressLayout, 1f);
            // 顶部栏：向上滑出并淡出
            playHideAnimation(mBackLayout, -1f);
            if (mHideInfo) {
                playHideAnimation(mInfoLayout, -1f);
            }
        }
    }

    protected void showControl() {
        // 确保值不小于 1（页面从 1 开始计数）
        mSeekBar.setRangeSafe(1, Math.max(max, 1), Math.max(progress, 1));
        // 底部进度栏：从下方滑入并淡入
        playShowAnimation(mProgressLayout, 1f);
        // 顶部栏：从上方滑入并淡入
        playShowAnimation(mBackLayout, -1f);
        if (mHideInfo) {
            playShowAnimation(mInfoLayout, -1f);
        }
    }

    /**
     * 播放“滑出并淡出”动画，动画结束后将视图置为不可见
     */
    private void playHideAnimation(final View view, float direction) {
        AnimationSet set = new AnimationSet(true);
        set.setInterpolator(new AccelerateInterpolator());
        AlphaAnimation alpha = new AlphaAnimation(1f, 0f);
        alpha.setDuration(220);
        TranslateAnimation translate = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, direction);
        translate.setDuration(220);
        set.addAnimation(alpha);
        set.addAnimation(translate);
        set.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        view.startAnimation(set);
    }

    /**
     * 播放“滑入并淡入”动画
     */
    private void playShowAnimation(final View view, float direction) {
        view.setVisibility(View.VISIBLE);
        AnimationSet set = new AnimationSet(true);
        set.setInterpolator(new DecelerateInterpolator());
        AlphaAnimation alpha = new AlphaAnimation(0f, 1f);
        alpha.setDuration(220);
        TranslateAnimation translate = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, direction, Animation.RELATIVE_TO_SELF, 0f);
        translate.setDuration(220);
        set.addAnimation(alpha);
        set.addAnimation(translate);
        view.startAnimation(set);
    }

    protected void updateProgress() {
        mChapterPage.setText(StringUtils.getProgress(progress, max));
        // 进度栏可见时同步 SeekBar 滑块位置，避免快速滑动后滑块与实际进度不一致
        if (mProgressLayout != null && mProgressLayout.isShown() && mSeekBar != null) {
            mSeekBar.setValue(Math.max(progress, 1));
        }
    }

    @Override
    public void onPicturePaging(ImageUrl image) {
        int pos = mReaderAdapter.getPositionById(image.getId());
        String[] urls = image.getUrls().toArray(new String[0]);
        mReaderAdapter.add(pos + 1, new ImageUrl(image.getId() + 900, image.getComicChapter(), image.getNum(), urls,
                image.getChapter(), ImageUrl.STATE_PAGE_2, false));
    }

    @Override
    public void onParseError() {
        HintUtils.showToast(this, R.string.common_parse_error);
    }

    private void setReaderAdapter(List<ImageUrl> list) {
        setReaderAdapter(list, _source, _local);
    }

    private void setReaderAdapter(List<ImageUrl> list, int source, boolean local) {
        _source = source;
        _local = local;
        Headers headers = SourceManager.getInstance(this).getParser(source).getHeader(list);
        mImagePipelineFactory = ImagePipelineFactoryBuilder
                .build(this, local ? null : headers, false);
        mLargeImagePipelineFactory = ImagePipelineFactoryBuilder
                .build(this, local ? null : headers, true);
        mReaderAdapter.setControllerSupplier(ControllerBuilderSupplierFactory.get(this, mImagePipelineFactory),
                ControllerBuilderSupplierFactory.get(this, mLargeImagePipelineFactory));
    }

    @Override
    public void onNextLoadSuccess(List<ImageUrl> list) {
        setReaderAdapter(list);
        mReaderAdapter.addAll(list);
        HintUtils.showToast(this, R.string.reader_load_success);
    }

    @Override
    public void onPrevLoadSuccess(List<ImageUrl> list) {
        setReaderAdapter(list);
        mReaderAdapter.addAll(0, list);
        HintUtils.showToast(this, R.string.reader_load_success);
    }

    @Override
    public void onInitLoadSuccess(List<ImageUrl> list, int progress, int source, boolean local) {
        setReaderAdapter(list, source, local);
        mReaderAdapter.addAll(list);
        if (progress != 1) {
            mRecyclerView.scrollToPosition(progress - 1);
        }
        mLoadingLayout.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
        updateProgress();
    }

    @Override
    public void onChapterChange(Chapter chapter) {
        max = chapter.getCount();
        final String title = chapter.getTitle();
        final int titleLengthMax = 15;
        mChapterTitle.setText(
                STConvertUtils.convert(title.length() > titleLengthMax ?
                        title.substring(0, titleLengthMax).concat("...") :
                        title)
        );
    }

    @Override
    public void onImageLoadSuccess(Long id, String url) {
        mReaderAdapter.update(id, url);
    }

    @Override
    public void onImageLoadFail(Long id) {
        mReaderAdapter.update(id, null);
    }

    @Override
    public void onPictureSaveSuccess(Uri uri) {
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
        isSavingPicture = false;
        HintUtils.showToast(this, R.string.reader_picture_save_success);
    }

    @Override
    public void onPictureSaveFail() {
        isSavingPicture = false;
        HintUtils.showToast(this, R.string.reader_picture_save_fail);
    }

    @Override
    public void onPrevLoading() {
        HintUtils.showToast(this, R.string.reader_load_prev);
    }

    @Override
    public void onPrevLoadNone() {
        HintUtils.showToast(this, R.string.reader_prev_none);
    }

    @Override
    public void onNextLoading() {
        HintUtils.showToast(this, R.string.reader_load_next);
    }

    @Override
    public void onNextLoadNone() {
        HintUtils.showToast(this, R.string.reader_next_none);
    }

    /**
     * Click Event Function
     */

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mReaderAdapter.getItemCount() != 0) {
            int value = ClickEvents.EVENT_NULL;
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    value = mClickArray[5];
                    break;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    value = mClickArray[6];
                    break;

                case KeyEvent.KEYCODE_BUTTON_L1:
                case KeyEvent.KEYCODE_BUTTON_L2:
                    value = mClickArray[7];
                    break;
                case KeyEvent.KEYCODE_BUTTON_R1:
                case KeyEvent.KEYCODE_BUTTON_R2:
                    value = mClickArray[8];
                    break;
                case KeyEvent.KEYCODE_BUTTON_A:
                    value = mClickArray[14];
                    break;
                case KeyEvent.KEYCODE_BUTTON_B:
                    value = mClickArray[13];
                    break;
                case KeyEvent.KEYCODE_BUTTON_X:
                    value = mClickArray[15];
                    break;
                case KeyEvent.KEYCODE_BUTTON_Y:
                    value = mClickArray[16];
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    value = mClickArray[9];
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    value = mClickArray[10];
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    value = mClickArray[11];
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    value = mClickArray[12];
                    break;

            }
            if (value != ClickEvents.EVENT_NULL) {
                doClickEvent(value);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {

        // Check that the event came from a game controller
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) ==
                InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {

            // Process all historical movement samples in the batch
            final int historySize = event.getHistorySize();

            // Process the movements starting from the
            // earliest historical position in the batch
            for (int i = 0; i < historySize; i++) {
                // Process the event at historical position i
                processJoystickInput(event);
            }

            // Process the current movement sample in the batch (position -1)
            processJoystickInput(event);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void checkKey(float val, ClickEvents.JoyLocks joy) {
        //unlock
        if (JoyLock[joy.ordinal()] && val < this.mControllerTrigThreshold) {
            JoyLock[joy.ordinal()] = false;
        }
        //lock
        if (!JoyLock[joy.ordinal()] && val > this.mControllerTrigThreshold) {
            JoyLock[joy.ordinal()] = true;
            doClickEvent(mClickArray[JoyEvent[joy.ordinal()]]);
        }
    }

    private void processJoystickInput(MotionEvent event) {
        checkKey(event.getAxisValue(MotionEvent.AXIS_GAS), ClickEvents.JoyLocks.RT);
        checkKey(event.getAxisValue(MotionEvent.AXIS_BRAKE), ClickEvents.JoyLocks.LT);
    }

    @Override
    public void onSingleTap(float x, float y) {
        doClickEvent(getValue(x, y, false));
    }

    @Override
    public void onLongPress(float x, float y) {
        doClickEvent(getValue(x, y, true));
    }

    @SuppressWarnings("deprecation")
    private int getValue(float x, float y, boolean isLong) {
        Point point = new Point();
        WindowManager wm = getWindowManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = wm.getCurrentWindowMetrics().getBounds();
            point.set(bounds.width(), bounds.height());
        } else {
            wm.getDefaultDisplay().getSize(point);
        }
        int position = getCurPosition();
        if (position == -1) {
            position = mLayoutManager.findFirstVisibleItemPosition();
        }

        RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(position);
        if (holder == null) {
            return 0;
        }

        RetryDraweeView draweeView = null;

        // 根据 holder 类型获取 draweeView
        if (holder instanceof ReaderAdapter.PageHolder) {
            draweeView = ((ReaderAdapter.PageHolder) holder).draweeView;
        } else if (holder instanceof ReaderAdapter.StreamHolder) {
            draweeView = ((ReaderAdapter.StreamHolder) holder).draweeView;
        }

        if (draweeView == null) {
            return 0;
        }

        float limitX = point.x / 3.0f;
        float limitY = point.y / 3.0f;
        if (x < limitX) {
            return isLong ? mLongClickArray[0] : mClickArray[0];
        } else if (x > 2 * limitX) {
            return isLong ? mLongClickArray[4] : mClickArray[4];
        } else if (y < limitY) {
            return isLong ? mLongClickArray[1] : mClickArray[1];
        } else if (y > 2 * limitY) {
            return isLong ? mLongClickArray[3] : mClickArray[3];
        } else if (!draweeView.retry()) {
            return isLong ? mLongClickArray[2] : mClickArray[2];
        }
        return 0;
    }

    private void doClickEvent(int value) {
        switch (value) {
            case ClickEvents.EVENT_PREV_PAGE:
                prevPage();
                break;
            case ClickEvents.EVENT_NEXT_PAGE:
                nextPage();
                break;
            case ClickEvents.EVENT_SAVE_PICTURE:
                savePicture();
                break;
            case ClickEvents.EVENT_LOAD_PREV:
                loadPrev();
                break;
            case ClickEvents.EVENT_LOAD_NEXT:
                loadNext();
                break;
            case ClickEvents.EVENT_EXIT_READER:
                exitReader();
                break;
            case ClickEvents.EVENT_TO_FIRST:
                toFirst();
                break;
            case ClickEvents.EVENT_TO_LAST:
                toLast();
                break;
            case ClickEvents.EVENT_SWITCH_SCREEN:
                switchScreen();
                break;
            case ClickEvents.EVENT_SWITCH_MODE:
                switchMode();
                break;
            case ClickEvents.EVENT_SWITCH_CONTROL:
                switchControl();
                break;
            case ClickEvents.EVENT_RELOAD_IMAGE:
                reloadImage();
                break;
            case ClickEvents.EVENT_SWITCH_NIGHT:
                switchNight();
                break;
        }
    }

    protected abstract int getCurPosition();

    protected abstract void prevPage();

    protected abstract void nextPage();

    protected void switchNight() {
        boolean night = !mPreference.getBoolean(PreferenceManager.PREF_NIGHT, false);
        mPreference.putBoolean(PreferenceManager.PREF_NIGHT, night);
        if (mNightMask != null) {
            mNightMask.setVisibility(night ? View.VISIBLE : View.INVISIBLE);
        }
        mPresenter.switchNight();
    }

    protected void reloadImage() {
        int position = getCurPosition();
        if (position == -1) {
            position = mLayoutManager.findFirstVisibleItemPosition();
        }
        ImageUrl image = mReaderAdapter.getItem(position);
        String rawUrl = image.getUrl();
        String postUrl = StringUtils.format("%s-post-%d", image.getUrl(), image.getId());
        ImagePipelineFactory factory = image.getSize() > App.mLargePixels ?
                mLargeImagePipelineFactory : mImagePipelineFactory;
        factory.getImagePipeline().evictFromCache(Uri.parse(rawUrl));
        factory.getImagePipeline().evictFromCache(Uri.parse(postUrl));
        mReaderAdapter.notifyItemChanged(position);
    }

    protected void savePicture() {
        if (isSavingPicture) {
            return;
        }
        isSavingPicture = true;

        int position = getCurPosition();
        if (position == -1) {
            position = mLayoutManager.findFirstVisibleItemPosition();
        }
        ImageUrl imageUrl = mReaderAdapter.getItem(position);
        String[] urls = imageUrl.getUrls().toArray(new String[0]);
        try {
            String title = mChapterTitle.getText().toString();
            for (String url : urls) {
                if (url.startsWith("file")) {
                    mPresenter.savePicture(new FileInputStream(new File(Objects.requireNonNull(Uri.parse(url).getPath()))), url, title, progress);
                    return;
                } else if (url.startsWith("content")) {
                    mPresenter.savePicture(getContentResolver().openInputStream(Uri.parse(url)), url, title, progress);
                    return;
                } else {
//                    ImagePipelineFactory factory = imageUrl.getSize() > App.mLargePixels ?
//                            mLargeImagePipelineFactory : mImagePipelineFactory;
//                    BinaryResource resource = factory
//                            .getDiskCachesStoreSupplier()
//                            .get()
//                            .getMainFileCache().getResource(new SimpleCacheKey(url));
//                    if (resource != null) {
//                        mPresenter.savePicture(resource.openStream(), url, title, progress);
//                        return;
//                    }
                    InputStream inputStream = FrescoUtils.getCacheFileInputStream(url);
                    mPresenter.savePicture(inputStream, url, title, progress);
                    return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        onPictureSaveFail();
    }

    protected void loadPrev() {
        mPresenter.loadPrev();
    }

    protected void loadNext() {
        mPresenter.loadNext();
    }

    protected void exitReader() {
        finish();
    }

    protected void toFirst() {
        mRecyclerView.scrollToPosition(0);
    }

    protected void toLast() {
        mRecyclerView.scrollToPosition(mReaderAdapter.getItemCount() - 1);
    }

    protected void switchScreen() {
        final int[] oArray = {ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT};
        setRequestedOrientation(oArray[this.getResources().getConfiguration().orientation]);
    }

    protected void switchMode() {
        Intent intent = getIntent();
        int newMode;
        if (mode == PreferenceManager.READER_MODE_PAGE) {
            intent.setClass(this, StreamReaderActivity.class);
            newMode = PreferenceManager.READER_MODE_STREAM;
        } else {
            intent.setClass(this, PageReaderActivity.class);
            newMode = PreferenceManager.READER_MODE_PAGE;
        }
        // 同步更新默认阅读模式，下次打开漫画时沿用新选择的模式
        mPreference.putNumber(PreferenceManager.PREF_READER_MODE, newMode);
        // 切换模式会重建 Activity，需将章节列表重新放入缓存（原缓存已在 initData 中取出）
        Chapter[] array = mPresenter.getChapterArray();
        if (array != null && array.length > 0) {
            long newKey = sChapterKeyGenerator.incrementAndGet();
            sChapterCache.put(newKey, Arrays.asList(array));
            intent.putExtra(Extra.EXTRA_CHAPTER_KEY, newKey);
        }
        intent.putExtra(Extra.EXTRA_MODE, newMode);
        finish();
        startActivity(intent);
    }

    protected void switchControl() {
        if (mProgressLayout.isShown()) {
            hideControl();
        } else {
            showControl();
        }
    }

    /**
     * 底部上滑弹出的阅读快捷设置面板
     */
    private void showReaderSettings() {
        if (mSettingsSheet != null && mSettingsSheet.isShowing()) {
            mSettingsSheet.dismiss();
            return;
        }
        boolean dark = ThemeUtils.isDarkMode(this);
        BottomSheetDialog dialog = new BottomSheetDialog(this, dark ?
                R.style.ReaderBottomSheetDark : R.style.ReaderBottomSheetLight);
        // 必须用 dialog 的 context（已应用所选主题）来 inflate，
        // 否则 ?android:attr/textColorPrimary 会按 Activity 的浅色主题解析，深色模式下文字仍是黑色
        // 注：BottomSheetDialog.setContentView 内部会把 view 放到 design_bottom_sheet 容器，
        // 但 inflate 时不传 parent 会导致 layout_width/layout_height/margin 等 layout_* 属性丢失，
        // 最终 sheet 被压缩成 WRAP_CONTENT，影响显示高度；因此传一个临时 FrameLayout parent
        // （attachToRoot=false）仅用于生成 LayoutParams。
        FrameLayout sheetContainer = new FrameLayout(dialog.getContext());
        View view = LayoutInflater.from(dialog.getContext())
                .inflate(R.layout.layout_reader_settings_sheet, sheetContainer, false);
        dialog.setContentView(view);
        mSettingsSheet = dialog;

        MaterialButtonToggleGroup turnGroup = view.findViewById(R.id.reader_turn_group);
        MaterialButtonToggleGroup orientationGroup = view.findViewById(R.id.reader_orientation_group);
        MaterialButtonToggleGroup modeGroup = view.findViewById(R.id.reader_mode_group);
        SwitchCompat nightSwitch = view.findViewById(R.id.reader_switch_night);
        SwitchCompat whiteBgSwitch = view.findViewById(R.id.reader_switch_white_background);
        SwitchCompat keepBrightSwitch = view.findViewById(R.id.reader_switch_keep_bright);
        SwitchCompat hideInfoSwitch = view.findViewById(R.id.reader_switch_hide_info);
        SwitchCompat volumeKeySwitch = view.findViewById(R.id.reader_switch_volume_key);
        MaterialButton fullSettingsBtn = view.findViewById(R.id.reader_settings_full);

        // 按钮文本（复用现有选项数组）
        String[] turnItems = getResources().getStringArray(R.array.reader_turn_items);
        String[] orientationItems = getResources().getStringArray(R.array.reader_orientation_items);
        String[] modeItems = getResources().getStringArray(R.array.reader_mode_items);
        for (int i = 0; i < turnGroup.getChildCount() && i < turnItems.length; i++) {
            ((MaterialButton) turnGroup.getChildAt(i)).setText(turnItems[i]);
        }
        for (int i = 0; i < orientationGroup.getChildCount() && i < orientationItems.length; i++) {
            ((MaterialButton) orientationGroup.getChildAt(i)).setText(orientationItems[i]);
        }
        for (int i = 0; i < modeGroup.getChildCount() && i < modeItems.length; i++) {
            ((MaterialButton) modeGroup.getChildAt(i)).setText(modeItems[i]);
        }

        // 初始化选中状态
        String turnKey = mode == PreferenceManager.READER_MODE_PAGE ?
                PreferenceManager.PREF_READER_PAGE_TURN : PreferenceManager.PREF_READER_STREAM_TURN;
        int currentTurn = mPreference.getNumber(turnKey, PreferenceManager.READER_TURN_LTR).intValue();
        if (currentTurn < turnGroup.getChildCount()) {
            turnGroup.check(turnGroup.getChildAt(currentTurn).getId());
        }

        String orientationKey = mode == PreferenceManager.READER_MODE_PAGE ?
                PreferenceManager.PREF_READER_PAGE_ORIENTATION : PreferenceManager.PREF_READER_STREAM_ORIENTATION;
        int currentOrientation = mPreference.getNumber(orientationKey, PreferenceManager.READER_ORIENTATION_PORTRAIT).intValue();
        if (currentOrientation < orientationGroup.getChildCount()) {
            orientationGroup.check(orientationGroup.getChildAt(currentOrientation).getId());
        }

        if (mode < modeGroup.getChildCount()) {
            modeGroup.check(modeGroup.getChildAt(mode).getId());
        }

        nightSwitch.setChecked(mPreference.getBoolean(PreferenceManager.PREF_NIGHT, false));
        whiteBgSwitch.setChecked(mPreference.getBoolean(PreferenceManager.PREF_READER_WHITE_BACKGROUND, true));
        keepBrightSwitch.setChecked(mPreference.getBoolean(PreferenceManager.PREF_READER_KEEP_BRIGHT, false));
        hideInfoSwitch.setChecked(mPreference.getBoolean(PreferenceManager.PREF_READER_HIDE_INFO, false));
        volumeKeySwitch.setChecked(mPreference.getBoolean(PreferenceManager.PREF_READER_VOLUME_KEY_CONTROLS_PAGE_TURNING, false));

        // 阅读方向
        turnGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int index = group.indexOfChild(group.findViewById(checkedId));
            applyTurn(index);
        });
        // 屏幕方向
        orientationGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int index = group.indexOfChild(group.findViewById(checkedId));
            applyOrientation(index);
        });
        // 阅读模式（切换需要重启阅读器）
        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int index = group.indexOfChild(group.findViewById(checkedId));
            if (index != mode) {
                // switchMode 内部依据当前 mode 判断目标模式，此处不要修改 mode
                switchMode();
            }
        });
        // 夜间模式
        nightSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != mPreference.getBoolean(PreferenceManager.PREF_NIGHT, false)) {
                switchNight();
            }
        });
        // 白色背景
        whiteBgSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPreference.putBoolean(PreferenceManager.PREF_READER_WHITE_BACKGROUND, isChecked);
            applyWhiteBackground(isChecked);
        });
        // 保持常亮
        keepBrightSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPreference.putBoolean(PreferenceManager.PREF_READER_KEEP_BRIGHT, isChecked);
            applyKeepBright(isChecked);
        });
        // 隐藏信息
        hideInfoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPreference.putBoolean(PreferenceManager.PREF_READER_HIDE_INFO, isChecked);
            applyHideInfo(isChecked);
        });
        // 音量键翻页
        volumeKeySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPreference.putBoolean(PreferenceManager.PREF_READER_VOLUME_KEY_CONTROLS_PAGE_TURNING, isChecked);
            applyVolumeKey(isChecked);
        });
        // 完整设置
        fullSettingsBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, ReaderConfigActivity.class));
        });

        dialog.show();
    }

    /**
     * 应用阅读方向（从左到右 / 从右到左 / 从上到下）
     */
    private void applyTurn(int newTurn) {
        if (newTurn == turn) {
            return;
        }
        turn = newTurn;
        String key = mode == PreferenceManager.READER_MODE_PAGE ?
                PreferenceManager.PREF_READER_PAGE_TURN : PreferenceManager.PREF_READER_STREAM_TURN;
        mPreference.putNumber(key, newTurn);
        mLayoutManager.setOrientation(turn == PreferenceManager.READER_TURN_ATB ?
                LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL);
        mLayoutManager.setReverseLayout(turn == PreferenceManager.READER_TURN_RTL);
        mReaderAdapter.setVertical(turn == PreferenceManager.READER_TURN_ATB);
        mSeekBar.setLayoutDirection(turn == PreferenceManager.READER_TURN_RTL ?
                View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mReaderAdapter.notifyDataSetChanged();
        int pos = getCurPosition();
        mRecyclerView.scrollToPosition(Math.max(pos, 0));
        updateProgress();
    }

    /**
     * 应用屏幕方向（竖屏 / 横屏 / 跟随系统）
     */
    private void applyOrientation(int newOrientation) {
        String key = mode == PreferenceManager.READER_MODE_PAGE ?
                PreferenceManager.PREF_READER_PAGE_ORIENTATION : PreferenceManager.PREF_READER_STREAM_ORIENTATION;
        mPreference.putNumber(key, newOrientation);
        orientation = newOrientation;
        final int[] oArray = {ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED};
        setRequestedOrientation(oArray[newOrientation]);
    }

    /**
     * 应用白色/黑色背景
     */
    private void applyWhiteBackground(boolean white) {
        if (white) {
            mReaderBox.setBackgroundResource(R.color.white);
            mLoadingText.setTextColor(getResources().getColor(R.color.black));
            Drawable customDrawable = AppCompatResources.getDrawable(this, R.drawable.reader_progress_2);
            mLoadingIcon.setIndeterminateDrawable(customDrawable);
        } else {
            mReaderBox.setBackgroundResource(R.color.black);
            mLoadingText.setTextColor(getResources().getColor(R.color.white));
            Drawable customDrawable = AppCompatResources.getDrawable(this, R.drawable.reader_progress);
            mLoadingIcon.setIndeterminateDrawable(customDrawable);
        }
        mReaderAdapter.notifyDataSetChanged();
    }

    /**
     * 应用保持屏幕常亮
     */
    private void applyKeepBright(boolean keep) {
        if (keep) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
     * 应用隐藏阅读信息
     */
    private void applyHideInfo(boolean hide) {
        mHideInfo = hide;
        mInfoLayout.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
    }

    /**
     * 应用音量键翻页（重新读取点击事件配置）
     */
    private void applyVolumeKey(boolean enable) {
        mClickArray = mode == PreferenceManager.READER_MODE_PAGE ?
                ClickEvents.getPageClickEventChoice(mPreference) :
                ClickEvents.getStreamClickEventChoice(mPreference);
    }

}
