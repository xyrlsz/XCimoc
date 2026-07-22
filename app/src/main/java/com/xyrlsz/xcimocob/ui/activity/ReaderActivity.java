package com.xyrlsz.xcimocob.ui.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.imagepipeline.core.ImagePipelineFactory;
import com.google.android.material.slider.Slider;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.fresco.ComicFrescoHeaders;
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
import com.xyrlsz.xcimocob.ui.adapter.ReaderAdapter;
import com.xyrlsz.xcimocob.ui.adapter.ReaderAdapter.OnLazyLoadListener;
import com.xyrlsz.xcimocob.ui.view.ReaderView;
import com.xyrlsz.xcimocob.ui.widget.OnTapGestureListener;
import com.xyrlsz.xcimocob.ui.widget.PreCacheLayoutManager;
import com.xyrlsz.xcimocob.ui.widget.RetryDraweeView;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;


import okhttp3.Headers;

/**
 * Created by Hiroshi on 2016/8/6.
 */
public abstract class ReaderActivity extends BaseActivity implements OnTapGestureListener,
        Slider.OnChangeListener, OnLazyLoadListener, ReaderView {

    private static final String SAVED_STATE_PROGRESS = "saved_state_progress";
    private static final String SAVED_STATE_MAX = "saved_state_max";

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
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra("level", 0);
                int scale = intent.getIntExtra("scale", 100);
                String text = (level * 100 / scale) + "%";
                mBatteryText.setText(text);
            }
        }
    };
    View mProgressLayout;
    View mBackLayout;
    View mInfoLayout;
    Slider mSeekBar;
    TextView mLoadingText;
    RecyclerView mRecyclerView;
    RelativeLayout mReaderBox;
    private boolean isSavingPicture = false;
    private boolean mHideInfo;
    private boolean mHideNav;
    private boolean mShowTopbar;
    private int[] mClickArray;
    private int[] mLongClickArray;
    private int _source;
    private boolean _local;
    private float mControllerTrigThreshold = 0.3f;

    public static Intent createIntent(Context context, long id, List<Chapter> list, int mode) {
        Intent intent = getIntent(context, mode);
        intent.putExtra(Extra.EXTRA_ID, id);
        intent.putExtra(Extra.EXTRA_CHAPTER, new ArrayList<>(list));
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

    @Override
    protected void initTheme() {
        super.initTheme();
        mHideNav = mPreference.getBoolean(PreferenceManager.PREF_READER_HIDE_NAV, false);
        mShowTopbar = mPreference.getBoolean(PreferenceManager.PREF_OTHER_SHOW_TOPBAR, false);
        if (!mHideNav) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
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
        mProgressLayout = findViewById(R.id.reader_progress_layout);
        mBackLayout = findViewById(R.id.reader_back_layout);
        mInfoLayout = findViewById(R.id.reader_info_layout);
        mSeekBar = findViewById(R.id.reader_seek_bar);
        mLoadingText = findViewById(R.id.reader_loading);
        mRecyclerView = findViewById(R.id.reader_recycler_view);
        mReaderBox = findViewById(R.id.reader_box);
    }

    @Override
    protected void initView() {
        boolean isWhiteBackground = App.getPreferenceManager().getBoolean(PreferenceManager.PREF_READER_WHITE_BACKGROUND, false);
        if (isWhiteBackground) {
            mLoadingText.setTextColor(getResources().getColor(R.color.black));
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
        if (mPreference.getBoolean(PreferenceManager.PREF_READER_WHITE_BACKGROUND, false)) {
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
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (mHideNav) {
            int options = getWindow().getDecorView().getSystemUiVisibility();

            options |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                options |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                options |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }

            getWindow().getDecorView().setSystemUiVisibility(options);
        }

        if (mShowTopbar) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    private void initSeekBar(int primaryColorResId) {
        mSeekBar.setLayoutDirection(turn == PreferenceManager.READER_TURN_RTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mSeekBar.addOnChangeListener(this);
        if (primaryColorResId != 0) {
            int primaryColor = getColor(primaryColorResId);
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
            List<Chapter> list = getIntent().getParcelableArrayListExtra(Extra.EXTRA_CHAPTER);
            mPresenter.loadInit(id, Objects.requireNonNull(list).toArray(new Chapter[list.size()]));
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
            Animation upAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, -1.0f);
            upAction.setDuration(300);
            Animation downAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 1.0f);
            downAction.setDuration(300);
            mProgressLayout.startAnimation(downAction);
            mProgressLayout.setVisibility(View.INVISIBLE);
            mBackLayout.startAnimation(upAction);
            mBackLayout.setVisibility(View.INVISIBLE);
            if (mHideInfo) {
                mInfoLayout.startAnimation(upAction);
                mInfoLayout.setVisibility(View.INVISIBLE);
            }
        }
    }

    protected void showControl() {
        Animation upAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.0f);
        upAction.setDuration(300);
        Animation downAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, -1.0f,
                Animation.RELATIVE_TO_SELF, 0.0f);
        downAction.setDuration(300);
        if ((int) mSeekBar.getValueTo() != max) {
            mSeekBar.setValueTo(max);
        }
        mSeekBar.setValue(progress);
        mProgressLayout.startAnimation(upAction);
        mProgressLayout.setVisibility(View.VISIBLE);
        mBackLayout.startAnimation(downAction);
        mBackLayout.setVisibility(View.VISIBLE);
        if (mHideInfo) {
            mInfoLayout.startAnimation(downAction);
            mInfoLayout.setVisibility(View.VISIBLE);
        }
    }

    protected void updateProgress() {
        mChapterPage.setText(StringUtils.getProgress(progress, max));
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
        ComicFrescoHeaders.setHeaders(headers);
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
        mLoadingText.setVisibility(View.GONE);
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

    private int getValue(float x, float y, boolean isLong) {
        Point point = new Point();
        getWindowManager().getDefaultDisplay().getSize(point);
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
        if (mode == PreferenceManager.READER_MODE_PAGE) {
            intent.setClass(this, StreamReaderActivity.class);
            intent.putExtra(Extra.EXTRA_MODE, PreferenceManager.READER_MODE_STREAM);
        } else {
            intent.setClass(this, PageReaderActivity.class);
            intent.putExtra(Extra.EXTRA_MODE, PreferenceManager.READER_MODE_PAGE);
        }
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

}
