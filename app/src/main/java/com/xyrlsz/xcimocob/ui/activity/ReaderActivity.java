package com.xyrlsz.xcimocob.ui.activity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
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

import com.facebook.imagepipeline.core.ImagePipeline;
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
import com.xyrlsz.xcimocob.ui.widget.ZoomableRecyclerView;
import com.xyrlsz.xcimocob.ui.widget.rvp.RecyclerViewPager;
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
    protected boolean mDualPage; // 大横屏左右双页
    private boolean mTapOnRightHalf; // 最近一次点击是否落在屏幕右半边（双页时用于判断操作的是哪一页）
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
    private int mPendingPictureSaves = 0; // 等待写盘的图片数量（双页时一屏两张）
    private int mPictureSaveFailCount = 0;
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


        mRecyclerView.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                 oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                mReaderAdapter.setViewSize(right - left, bottom - top);
            }
        });
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
        // 图片尺寸只有解码后才知道，识别出需要单独占屏的图后重建列表项
        mReaderAdapter.setOnPageGroupChangedListener(this::onPageGroupChanged);
        mReaderAdapter.setScaleFactor(mPreference.getNumber(PreferenceManager.PREF_READER_SCALE_FACTOR, 200).intValue() * 0.01f);
        mReaderAdapter.setDoubleTap(!mPreference.getBoolean(PreferenceManager.PREF_READER_BAN_DOUBLE_CLICK, false));
        mReaderAdapter.setVertical(turn == PreferenceManager.READER_TURN_ATB);
        mDualPage = isDualPageEnabled();
        mReaderAdapter.setDualPage(mDualPage);
        applyDualPageOptions();
        applyPaging();
        applyCloseAutoResizeImage();
        mReaderAdapter.setWhiteEdge(mPreference.getBoolean(PreferenceManager.PREF_READER_WHITE_EDGE, false));
        mReaderAdapter.setBanTurn(mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_BAN_TURN, false));
    }

    private void initLayoutManager() {
        mLayoutManager = new PreCacheLayoutManager(this);
        mLayoutManager.setOrientation(turn == PreferenceManager.READER_TURN_ATB ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL);
        mLayoutManager.setReverseLayout(turn == PreferenceManager.READER_TURN_RTL);
        mLayoutManager.setExtraSpace(2);

    }

    /**
     * 大横屏（折叠屏展开 / 平板）且开启开关时，左右各显示一页
     */
    private boolean isDualPageEnabled() {
        return mode == PreferenceManager.READER_MODE_PAGE
                && turn != PreferenceManager.READER_TURN_ATB
                && mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE, false)
                && isLargeLandscape();
    }

    private boolean isLargeLandscape() {
        Configuration configuration = getResources().getConfiguration();
        return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                && configuration.smallestScreenWidthDp >= 600;
    }

    /**
     * 双页的两个子设置（从右往左 / 左右无间隔）只在双页实际生效时才应用，
     * 关闭「大横屏左右双页」后不再对阅读器产生任何影响
     *
     * @return 设置是否发生了变化（调用方据此决定是否需要刷新列表）
     */
    protected boolean applyDualPageOptions() {
        if (mReaderAdapter == null) {
            return false;
        }
        boolean rtl = mDualPage
                && mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE_RTL, false);
        boolean noGap = mDualPage
                && mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE_NO_GAP, false);
        boolean changed = rtl != mReaderAdapter.isDualPageRtl()
                || noGap != mReaderAdapter.isDualPageNoGap();
        mReaderAdapter.setDualPageRtl(rtl);
        mReaderAdapter.setDualPageNoGap(noGap);
        return changed;
    }

    /**
     * 同步自动切割大图设置；双页本身已是左右两页，不再对单张图做横向拆页
     *
     * @return 设置是否发生了变化（后处理方式变了，缓存里的图也不能再用）
     */
    private boolean applyPaging() {
        boolean streamOff = mPreference.getNumber(PreferenceManager.PREF_READER_MODE,
                PreferenceManager.READER_MODE_PAGE).intValue() == PreferenceManager.READER_MODE_STREAM
                && mPreference.getBoolean(PreferenceManager.PREF_READER_PAGING_STREAM_OFF, true);
        boolean paging = !mDualPage && !streamOff
                && mPreference.getBoolean(PreferenceManager.PREF_READER_PAGING, false);
        boolean pagingReverse = paging
                && mPreference.getBoolean(PreferenceManager.PREF_READER_PAGING_REVERSE, false);
        boolean changed = paging != mReaderAdapter.isPaging()
                || pagingReverse != mReaderAdapter.isPagingReverse();
        mReaderAdapter.setPaging(paging);
        mReaderAdapter.setPagingReverse(pagingReverse);
        return changed;
    }

    /**
     * 同步「关闭自动调整图片大小」设置
     *
     * @return 设置是否发生了变化（解码尺寸变了，需要重新解码）
     */
    private boolean applyCloseAutoResizeImage() {
        boolean close = mPreference.getBoolean(PreferenceManager.PREF_READER_CLOSEAUTORESIZEIMAGE, false);
        boolean changed = close != mReaderAdapter.isCloseAutoResizeImage();
        mReaderAdapter.setCloseAutoResizeImage(close);
        return changed;
    }

    /**
     * 应用大横屏双页（切换开关、屏幕尺寸变化时调用），保持当前阅读位置
     *
     * @return 是否发生了模式切换（已内部重建列表项）
     */
    protected boolean applyDualPage() {
        if (mReaderAdapter == null) {
            return false;
        }
        boolean dualPage = isDualPageEnabled();
        if (dualPage == mDualPage) {
            return false;
        }
        int dataPosition = mReaderAdapter.toDataPosition(Math.max(getActionAdapterPosition(), 0));
        mDualPage = dualPage;
        mReaderAdapter.setDualPage(dualPage);
        applyDualPageOptions();
        applyPaging();
        rebindAfterLayoutChange(dataPosition);
        return true;
    }

    /**
     * 每屏图片数或视图类型改变后重建列表项并回到指定数据位置。
     * 只发 notifyDataSetChanged 时 RecyclerView 会复用旧视图，旧图片/矩阵状态会残留，
     * 表现为「图片没刷新，点一下才重新加载」，因此这里重挂 adapter 强制重新创建视图。
     *
     * @param dataPosition 需要保持可见的数据位置
     */
    private void rebindAfterLayoutChange(int dataPosition) {
        // 先停掉进行中的惯性/平滑滚动，避免重建后被旧动画拉回旧位置
        mRecyclerView.stopScroll();
        // 解码尺寸按当前阅读区重新计算：单页与双页每页占的宽度不同，
        // 沿用旧的尺寸会让图片按上一次的大小解码（看起来要再点一下才会「resize」）
        mReaderAdapter.setViewSize(mRecyclerView.getWidth(), mRecyclerView.getHeight());
        mRecyclerView.setAdapter(mReaderAdapter);
        // 复用池里的旧视图带着上一次的缩放矩阵和已绑定的图片，留着会被再次复用，一并丢弃
        mRecyclerView.getRecycledViewPool().clear();
        int count = mReaderAdapter.getItemCount();
        if (count == 0) {
            return;
        }
        int position = mReaderAdapter.toAdapterPosition(dataPosition);
        position = Math.max(0, Math.min(position, count - 1));
        if (mRecyclerView instanceof RecyclerViewPager) {
            ((RecyclerViewPager) mRecyclerView).resetToPosition(position);
        } else {
            mRecyclerView.scrollToPosition(position);
        }
        // 视图重建与布局不在同一帧，布局完成后再补一次绑定，确保新视图拿到最新图片
        mRecyclerView.post(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            // 旧模式下解码/后处理过的图（可能是另一种尺寸或被切割过的半张）必须丢弃，
            // 否则重新解码时会直接命中缓存，屏幕上看不出变化
            evictScreenImageCache();
            int itemCount = mReaderAdapter.getItemCount();
            if (itemCount > 0) {
                mReaderAdapter.notifyItemRangeChanged(0, itemCount);
            }
        });
    }

    /**
     * 双页分组随图片尺寸信息更新而变化（识别出需要单独占屏的图）后重建列表项，保持当前阅读位置
     */
    private void onPageGroupChanged() {
        if (mReaderAdapter == null || !mDualPage || isFinishing() || isDestroyed()) {
            return;
        }
        rebindAfterLayoutChange(mReaderAdapter.toDataPosition(Math.max(getActionAdapterPosition(), 0)));
    }

    /**
     * 丢弃当前屏图片在 Fresco 内存缓存中的解码结果，使其按当前模式的尺寸重新解码。
     * 只用内存版：切换模式不该把磁盘缓存也清掉，否则会重新下载
     */
    private void evictScreenImageCache() {
        List<ImageUrl> list = mReaderAdapter.getDateSet();
        for (int dataPosition : getScreenDataPositions(getActionAdapterPosition())) {
            if (dataPosition >= 0 && dataPosition < list.size()) {
                ImageUrl image = list.get(dataPosition);
                getImagePipeline(image).evictFromMemoryCache(Uri.parse(image.getUrl()));
            }
        }
    }

    /**
     * 手动「重新加载图片」：内存缓存和磁盘缓存一起清，确保重新下载
     */
    private void evictImageCache(ImageUrl image) {
        ImagePipeline pipeline = getImagePipeline(image);
        pipeline.evictFromCache(Uri.parse(image.getUrl()));
        pipeline.evictFromCache(Uri.parse(StringUtils.format("%s-post-%d",
                image.getUrl(), image.getId())));
    }

    private ImagePipeline getImagePipeline(ImageUrl image) {
        ImagePipelineFactory factory = image.getSize() > App.mLargePixels ?
                mLargeImagePipelineFactory : mImagePipelineFactory;
        return factory.getImagePipeline();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 折叠屏展开 / 旋转都会走到这里，需要重新判断是否使用双页
        applyDualPage();
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
        // 完整设置页是直接写偏好的，返回阅读器时需重新应用这些影响图片显示方式的设置
        boolean turnChanged = applyTurnFromPreference();
        boolean dualPageChanged = applyDualPage();
        boolean pagingChanged = applyPaging();
        boolean resizeChanged = applyCloseAutoResizeImage();
        boolean optionsChanged = applyDualPageOptions();
        // 方向切换、双页切换内部已经重建过列表项，其余情况只要解码方式变了也要重建，
        // 否则图片会一直是旧尺寸
        if (!turnChanged && !dualPageChanged && (pagingChanged || resizeChanged || optionsChanged)) {
            rebindAfterLayoutChange(mReaderAdapter.toDataPosition(Math.max(getActionAdapterPosition(), 0)));
        }
    }

    /**
     * 完整设置页同样可以改阅读方向，返回阅读器时按偏好重新应用
     *
     * @return 是否发生了方向切换（已内部重建列表项）
     */
    private boolean applyTurnFromPreference() {
        if (mReaderAdapter == null) {
            return false;
        }
        String key = mode == PreferenceManager.READER_MODE_PAGE ?
                PreferenceManager.PREF_READER_PAGE_TURN : PreferenceManager.PREF_READER_STREAM_TURN;
        int newTurn = mPreference.getNumber(key, PreferenceManager.READER_TURN_LTR).intValue();
        if (newTurn == turn) {
            return false;
        }
        applyTurn(newTurn);
        return true;
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
        if (mDualPage) {
            // 双页模式下图片已左右分屏显示，不再拆分单张图片
            return;
        }
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
            mRecyclerView.scrollToPosition(mReaderAdapter.toAdapterPosition(progress - 1));
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
        if (--mPendingPictureSaves > 0) {
            return;
        }
        isSavingPicture = false;
        HintUtils.showToast(this, mPictureSaveFailCount == 0 ?
                R.string.reader_picture_save_success : R.string.reader_picture_save_fail);
    }

    @Override
    public void onPictureSaveFail() {
        ++mPictureSaveFailCount;
        if (--mPendingPictureSaves > 0) {
            return;
        }
        isSavingPicture = false;
        HintUtils.showToast(this, mPictureSaveFailCount == 0 ?
                R.string.reader_picture_save_success : R.string.reader_picture_save_fail);
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
        } else if (holder instanceof ReaderAdapter.DualPageHolder) {
            // 双页模式取被点击的那一侧，用于判断是否处于重试状态
            ReaderAdapter.DualPageHolder dualHolder = (ReaderAdapter.DualPageHolder) holder;
            mTapOnRightHalf = isTapOnRightHalf(x);
            draweeView = mTapOnRightHalf ? dualHolder.rightView : dualHolder.leftView;
            if (draweeView.getVisibility() != View.VISIBLE) {
                // 该屏只有一张图（长图/宽图独占一屏）时另一侧是隐藏的，改用可见的那一张
                draweeView = mTapOnRightHalf ? dualHolder.leftView : dualHolder.rightView;
            }
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

    /**
     * 点击点是否落在阅读区右半边（以 RecyclerView 的实际位置为准，避免系统栏留白造成偏差）
     */
    private boolean isTapOnRightHalf(float x) {
        int[] location = new int[2];
        mRecyclerView.getLocationOnScreen(location);
        return x >= location[0] + mRecyclerView.getWidth() / 2.0f;
    }

    /**
     * 当前屏的列表项位置（无法确定时回退到第一个可见项）
     */
    private int getActionAdapterPosition() {
        int position = getCurPosition();
        return position == -1 ? mLayoutManager.findFirstVisibleItemPosition() : position;
    }

    /**
     * 当前屏包含的数据位置：单页时一张，双页时 1 张（长图独占一屏）或 2 张（顺序即数据顺序）
     */
    private int[] getScreenDataPositions(int adapterPosition) {
        int size = mReaderAdapter.getDateSet().size();
        if (size == 0) {
            return new int[0];
        }
        if (!mDualPage) {
            return new int[]{Math.max(0, Math.min(adapterPosition, size - 1))};
        }
        int first = mReaderAdapter.getGroupDataPosition(adapterPosition, 0);
        if (first < 0) {
            first = 0;
        }
        int second = mReaderAdapter.getGroupDataPosition(adapterPosition, 1);
        return second >= 0 ? new int[]{first, second} : new int[]{first};
    }

    /**
     * 保存图片、重新加载图片等只针对单张图片的操作，换算成要操作的数据位置。
     * 双页模式下按最近一次点击落在哪一侧决定是左边还是右边那一页。
     *
     * @return 数据位置，数据集为空时返回 -1
     */
    private int getDataPositionForAction(int adapterPosition) {
        int size = mReaderAdapter.getDateSet().size();
        if (size == 0) {
            return -1;
        }
        if (!mDualPage) {
            return Math.max(0, Math.min(adapterPosition, size - 1));
        }
        // 从左往右排列时右侧是后一页，从右往左排列时右侧是前一页
        boolean secondPage = mTapOnRightHalf != mReaderAdapter.isDualPageRtl();
        int dataPosition = mReaderAdapter.getGroupDataPosition(adapterPosition, secondPage ? 1 : 0);
        if (dataPosition < 0) {
            dataPosition = mReaderAdapter.getGroupDataPosition(adapterPosition, 0);
        }
        return Math.max(0, Math.min(dataPosition, size - 1));
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
        int dataPosition = getDataPositionForAction(getActionAdapterPosition());
        if (dataPosition < 0) {
            return;
        }
        evictImageCache(mReaderAdapter.getDateSet().get(dataPosition));
        mReaderAdapter.notifyItemChanged(mReaderAdapter.toAdapterPosition(dataPosition));
    }

    protected void savePicture() {
        if (isSavingPicture) {
            return;
        }
        int[] dataPositions = getScreenDataPositions(getActionAdapterPosition());
        if (dataPositions.length == 0) {
            return;
        }
        mPendingPictureSaves = 0;
        mPictureSaveFailCount = 0;

        String title = mChapterTitle.getText().toString();
        // 先把所有要保存的图片流准备好，避免保存一半才发现另一张取不到
        List<InputStream> streams = new ArrayList<>(dataPositions.length);
        List<String> urls = new ArrayList<>(dataPositions.length);
        List<Integer> pages = new ArrayList<>(dataPositions.length);
        for (int dataPosition : dataPositions) {
            ImageUrl imageUrl = mReaderAdapter.getDateSet().get(dataPosition);
            int page = imageUrl.getNum() > 0 ? imageUrl.getNum() : progress;
            // 切割成两半的长图两半页码相同、文件名也相同，只需保存一次
            if (pages.contains(page)) {
                continue;
            }
            for (String url : imageUrl.getUrls()) {
                InputStream stream;
                try {
                    stream = openPictureStream(url);
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                // 缓存里还没有这张图时继续尝试同一页的候选 URL
                if (stream != null) {
                    streams.add(stream);
                    urls.add(url);
                    pages.add(page);
                    break;
                }
            }
        }
        if (streams.isEmpty()) {
            onPictureSaveFail();
            return;
        }

        isSavingPicture = true;
        mPendingPictureSaves = streams.size();
        for (int i = 0; i < streams.size(); ++i) {
            mPresenter.savePicture(streams.get(i), urls.get(i), title, pages.get(i));
        }
    }

    /**
     * 打开图片对应的输入流：本地文件/文档直接读，网络图片从 Fresco 磁盘缓存取原始文件
     *
     * @return 取不到时返回 null
     */
    private InputStream openPictureStream(String url) throws IOException {
        if (url.startsWith("file")) {
            return new FileInputStream(new File(Objects.requireNonNull(Uri.parse(url).getPath())));
        }
        if (url.startsWith("content")) {
            return getContentResolver().openInputStream(Uri.parse(url));
        }
        return FrescoUtils.getCacheFileInputStream(url);
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
        SwitchCompat dualPageSwitch = view.findViewById(R.id.reader_switch_dual_page);
        SwitchCompat dualPageRtlSwitch = view.findViewById(R.id.reader_switch_dual_page_rtl);
        SwitchCompat dualPageNoGapSwitch = view.findViewById(R.id.reader_switch_dual_page_no_gap);
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
        // 左右双页仅在分页模式下有效，排列顺序/无间隔仅在开启左右双页后才有效
        boolean pageMode = mode == PreferenceManager.READER_MODE_PAGE;
        boolean dualPageOn = mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE, false);
        int dualPageSubVisibility = pageMode && dualPageOn ? View.VISIBLE : View.GONE;
        dualPageSwitch.setVisibility(pageMode ? View.VISIBLE : View.GONE);
        dualPageRtlSwitch.setVisibility(dualPageSubVisibility);
        dualPageNoGapSwitch.setVisibility(dualPageSubVisibility);
        dualPageSwitch.setChecked(dualPageOn);
        dualPageRtlSwitch.setChecked(mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE_RTL, false));
        dualPageNoGapSwitch.setChecked(mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE_NO_GAP, false));
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
        // 大横屏左右双页
        dualPageSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPreference.putBoolean(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE, isChecked);
            int visibility = isChecked && mode == PreferenceManager.READER_MODE_PAGE
                    ? View.VISIBLE : View.GONE;
            dualPageRtlSwitch.setVisibility(visibility);
            dualPageNoGapSwitch.setVisibility(visibility);
            applyDualPage();
        });
        // 双页从右往左排列（只改变左右顺序，不改变分页位置）
        dualPageRtlSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPreference.putBoolean(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE_RTL, isChecked);
            applyDualPageOptions();
            mReaderAdapter.notifyDataSetChanged();
        });
        // 左右图片无间隔
        dualPageNoGapSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPreference.putBoolean(PreferenceManager.PREF_READER_PAGE_DUAL_PAGE_NO_GAP, isChecked);
            applyDualPageOptions();
            mReaderAdapter.notifyDataSetChanged();
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
        // 先按当前双页状态换算数据位置，再修改状态
        int dataPosition = mReaderAdapter.toDataPosition(Math.max(getCurPosition(), 0));
        turn = newTurn;
        String key = mode == PreferenceManager.READER_MODE_PAGE ?
                PreferenceManager.PREF_READER_PAGE_TURN : PreferenceManager.PREF_READER_STREAM_TURN;
        mPreference.putNumber(key, newTurn);
        boolean vertical = turn == PreferenceManager.READER_TURN_ATB;
        boolean reverse = turn == PreferenceManager.READER_TURN_RTL;
        mLayoutManager.setOrientation(vertical ?
                LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL);
        mLayoutManager.setReverseLayout(reverse);
        // 翻页器缓存了 reverseLayout、流式阅读的缩放容器缓存了方向，
        // 直接改 LayoutManager 不会同步过去
        if (mRecyclerView instanceof RecyclerViewPager) {
            ((RecyclerViewPager) mRecyclerView).setReverseLayout(reverse);
        } else if (mRecyclerView instanceof ZoomableRecyclerView) {
            ((ZoomableRecyclerView) mRecyclerView).setVertical(vertical);
        }
        mReaderAdapter.setVertical(vertical);
        mSeekBar.setLayoutDirection(reverse ?
                View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        // 上下滚动时不再左右双页
        mDualPage = isDualPageEnabled();
        mReaderAdapter.setDualPage(mDualPage);
        applyDualPageOptions();
        applyPaging();
        rebindAfterLayoutChange(dataPosition);
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
