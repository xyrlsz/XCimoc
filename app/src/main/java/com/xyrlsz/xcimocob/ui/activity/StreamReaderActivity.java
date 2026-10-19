package com.xyrlsz.xcimocob.ui.activity;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.slider.Slider;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.ui.adapter.ReaderAdapter;
import com.xyrlsz.xcimocob.ui.widget.ZoomableRecyclerView;

import java.util.List;

/**
 * Created by Hiroshi on 2016/8/5.
 */
public class StreamReaderActivity extends ReaderActivity {

    private int mLastPosition = 0;

    @Override
    protected void initView() {
        super.initView();
        mReaderAdapter.setReaderMode(ReaderAdapter.READER_STREAM);
        if (App.getPreferenceManager().getBoolean(PreferenceManager.PREF_READER_PAGING_STREAM_OFF, false)) {
            mReaderAdapter.setPaging(false);
            mReaderAdapter.setPagingReverse(false);
        }
        if (mPreference.getBoolean(PreferenceManager.PREF_READER_STREAM_INTERVAL, false)) {
            mRecyclerView.addItemDecoration(mReaderAdapter.getItemDecoration());
        }
        ((ZoomableRecyclerView) mRecyclerView).setScaleFactor(
                mPreference.getNumber(PreferenceManager.PREF_READER_SCALE_FACTOR, 200).intValue() * 0.01f);
        ((ZoomableRecyclerView) mRecyclerView).setVertical(turn == PreferenceManager.READER_TURN_ATB);
        ((ZoomableRecyclerView) mRecyclerView).setDoubleTap(
                !mPreference.getBoolean(PreferenceManager.PREF_READER_BAN_DOUBLE_CLICK, false));
        ((ZoomableRecyclerView) mRecyclerView).setTapListenerListener(this);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                switch (newState) {
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        hideControl();
                        break;
                    case RecyclerView.SCROLL_STATE_IDLE:
                        // 快速滑动结束时再次同步进度，避免最后一次 onScrolled 因布局
                        // 未完成（findFirstVisibleItemPosition 滞后）导致进度信息不更新
                        syncStreamProgress(mLayoutManager.findFirstVisibleItemPosition(), 0, 0);
                        if (isLoadPrevEnabled()) {
                            int item = mLayoutManager.findFirstVisibleItemPosition();
                            if (item == 0) {
                                mPresenter.loadPrev();
                            }
                        }
                        if (isLoadNextEnabled()) {
                            int item = mLayoutManager.findLastVisibleItemPosition();
                            if (item == mReaderAdapter.getItemCount() - 1) {
                                mPresenter.loadNext();
                            }
                        }
                        break;
                    case RecyclerView.SCROLL_STATE_SETTLING:
                        if (isLoadPrevEnabled()) {
                            int item = mLayoutManager.findFirstVisibleItemPosition();
                            if (item == 0) {
                                mPresenter.loadPrev();
                            }
                        }
                        if (isLoadNextEnabled()) {
                            int item = mLayoutManager.findLastVisibleItemPosition();
                            if (item == mReaderAdapter.getItemCount() - 1) {
                                mPresenter.loadNext();
                            }
                        }
                        break;
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                syncStreamProgress(mLayoutManager.findFirstVisibleItemPosition(), dx, dy);
            }
        });
    }

    /**
     * 同步当前页进度。滑动过程中（dx/dy 非 0）按手势方向判定章节切换；
     * 滑动停止（dx/dy 均为 0，如 IDLE 时兜底同步）则按位置前后判定方向。
     * 仅当 target 与 mLastPosition 不同时才更新，避免重复触发章节切换。
     */
    private void syncStreamProgress(int target, int dx, int dy) {
        if (target == RecyclerView.NO_POSITION || target == mLastPosition) {
            return;
        }
        int count = mReaderAdapter.getItemCount();
        if (target < 0 || target >= count || mLastPosition < 0 || mLastPosition >= count) {
            return;
        }
        ImageUrl newImage = mReaderAdapter.getItem(target);
        ImageUrl oldImage = mReaderAdapter.getItem(mLastPosition);

        if (!oldImage.getChapter().equals(newImage.getChapter())) {
            boolean forward = switch (turn) {
                case PreferenceManager.READER_TURN_ATB -> dy != 0 ? dy > 0 : target > mLastPosition;
                case PreferenceManager.READER_TURN_LTR -> dx != 0 ? dx > 0 : target > mLastPosition;
                case PreferenceManager.READER_TURN_RTL -> dx != 0 ? dx < 0 : target > mLastPosition;
                default -> target > mLastPosition;
            };
            if (forward) {
                mPresenter.toNextChapter();
            } else {
                mPresenter.toPrevChapter();
            }
        }
        progress = newImage.getNum();
        mLastPosition = target;
        updateProgress();
    }

    @Override
    public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
        if (fromUser) {
            int intValue = Math.max(Math.round(value), 1);
            int current = mLastPosition + intValue - progress;
            int pos = mReaderAdapter.getPositionByNum(current, intValue, intValue < progress);
            mLayoutManager.scrollToPositionWithOffset(pos, 0);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void prevPage() {
        Point point = new Point();
        WindowManager wm = getWindowManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = wm.getCurrentWindowMetrics().getBounds();
            point.set(bounds.width(), bounds.height());
        } else {
            wm.getDefaultDisplay().getSize(point);
        }
        if (turn == PreferenceManager.READER_TURN_ATB) {
            mRecyclerView.smoothScrollBy(0, -point.y + point.y / 5);
        } else {
            mRecyclerView.smoothScrollBy(-point.x, 0);
        }
        if (mLayoutManager.findFirstVisibleItemPosition() == 0) {
            loadPrev();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void nextPage() {
        Point point = new Point();
        WindowManager wm = getWindowManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = wm.getCurrentWindowMetrics().getBounds();
            point.set(bounds.width(), bounds.height());
        } else {
            wm.getDefaultDisplay().getSize(point);
        }
        if (turn == PreferenceManager.READER_TURN_ATB) {
            mRecyclerView.smoothScrollBy(0, point.y - point.y / 5);
        } else {
            mRecyclerView.smoothScrollBy(point.x, 0);
        }
        if (mLayoutManager.findLastVisibleItemPosition() == mReaderAdapter.getItemCount() - 1) {
            loadNext();
        }
    }

    @Override
    public void onPrevLoadSuccess(List<ImageUrl> list) {
        super.onPrevLoadSuccess(list);
        if (mLastPosition == 0) {
            // 用户滑到顶部触发的预加载：插入后 position 0 变为上一章第一页。
            // 校正 mLastPosition 并主动同步一次，立即完成章节切换与进度更新，
            // 避免用户停留（不继续滑动）时章节切换/进度迟迟不生效
            mLastPosition = list.size();
            syncStreamProgress(0, 0, 0);
        }
    }

    @Override
    protected int getCurPosition() {
        return mLastPosition;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_stream_reader;
    }

}
