package com.xyrlsz.xcimocob.ui.activity;

import android.graphics.Point;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.ui.adapter.ReaderAdapter;
import com.xyrlsz.xcimocob.ui.widget.ZoomableRecyclerView;

import com.google.android.material.slider.Slider;

import java.util.List;

/**
 * Created by Hiroshi on 2016/8/5.
 */
public class StreamReaderActivity extends ReaderActivity {

    private int mLastPosition = 0;

    @Override
    protected void initView() {
        super.initView();
        mLoadPrev = mPreference.getBoolean(PreferenceManager.PREF_READER_STREAM_LOAD_PREV, false);
        mLoadNext = mPreference.getBoolean(PreferenceManager.PREF_READER_STREAM_LOAD_NEXT, true);
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
                    case RecyclerView.SCROLL_STATE_SETTLING:
                        if (mLoadPrev) {
                            int item = mLayoutManager.findFirstVisibleItemPosition();
                            if (item == 0) {
                                mPresenter.loadPrev();
                            }
                        }
                        if (mLoadNext) {
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
                int target = mLayoutManager.findFirstVisibleItemPosition();
                if (target != mLastPosition) {
                    ImageUrl newImage = mReaderAdapter.getItem(target);
                    ImageUrl oldImage = mReaderAdapter.getItem(mLastPosition);

                    if (!oldImage.getChapter().equals(newImage.getChapter())) {
                        switch (turn) {
                            case PreferenceManager.READER_TURN_ATB:
                                if (dy > 0) {
                                    mPresenter.toNextChapter();
                                } else if (dy < 0) {
                                    mPresenter.toPrevChapter();
                                }
                                break;
                            case PreferenceManager.READER_TURN_LTR:
                                if (dx > 0) {
                                    mPresenter.toNextChapter();
                                } else if (dx < 0) {
                                    mPresenter.toPrevChapter();
                                }
                                break;
                            case PreferenceManager.READER_TURN_RTL:
                                if (dx > 0) {
                                    mPresenter.toPrevChapter();
                                } else if (dx < 0) {
                                    mPresenter.toNextChapter();
                                }
                                break;
                        }
                    }
                    progress = mReaderAdapter.getItem(target).getNum();
                    mLastPosition = target;
                    updateProgress();
                }
            }
        });
    }

    @Override
    public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
        if (fromUser) {
            int intValue = Math.round(value);
            int current = mLastPosition + intValue - progress;
            int pos = mReaderAdapter.getPositionByNum(current, intValue, intValue < progress);
            mLayoutManager.scrollToPositionWithOffset(pos, 0);
        }
    }

    @Override
    protected void prevPage() {
        Point point = new Point();
        getWindowManager().getDefaultDisplay().getSize(point);
        if (turn == PreferenceManager.READER_TURN_ATB) {
            mRecyclerView.smoothScrollBy(0, -point.y + point.y / 5);
        } else {
            mRecyclerView.smoothScrollBy(-point.x, 0);
        }
        if (mLayoutManager.findFirstVisibleItemPosition() == 0) {
            loadPrev();
        }
    }

    @Override
    protected void nextPage() {
        Point point = new Point();
        getWindowManager().getDefaultDisplay().getSize(point);
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
            mLastPosition = list.size();
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
