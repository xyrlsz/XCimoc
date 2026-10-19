package com.xyrlsz.xcimocob.ui.activity;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.ui.adapter.ReaderAdapter;
import com.xyrlsz.xcimocob.ui.widget.rvp.RecyclerViewPager;
import com.xyrlsz.xcimocob.ui.widget.rvp.RecyclerViewPager.OnPageChangedListener;
import com.xyrlsz.xcimocob.utils.HintUtils;

import com.google.android.material.slider.Slider;

import java.util.List;

/**
 * Created by Hiroshi on 2016/7/7.
 */
public class PageReaderActivity extends ReaderActivity implements OnPageChangedListener {

    @Override
    protected void initView() {
        super.initView();
        mLoadPrev = mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_LOAD_PREV, true);
        mLoadNext = mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_LOAD_NEXT, true);
        int offset = mPreference.getNumber(PreferenceManager.PREF_READER_PAGE_TRIGGER, 10).intValue();
        mReaderAdapter.setReaderMode(ReaderAdapter.READER_PAGE);
        if (mPreference.getBoolean(PreferenceManager.PREF_READER_PAGE_QUICK_TURN, false)) {
            ((RecyclerViewPager) mRecyclerView).setScrollSpeed(0.000001f);
        } else {
            ((RecyclerViewPager) mRecyclerView).setScrollSpeed(0.12f);
        }
        ((RecyclerViewPager) mRecyclerView).setTriggerOffset(0.01f * offset);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mRecyclerView.setForceDarkAllowed(false);
        }
        ((RecyclerViewPager) mRecyclerView).setOnPageChangedListener(this);
        mRecyclerView.setItemAnimator(null);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideControl();
                }
            }
        });
    }

    @Override
    public void OnPageChanged(int oldPosition, int newPosition) {
        if (newPosition < 0) {
            return;
        }

        ImageUrl newImage = mReaderAdapter.getItem(newPosition);

        // oldPosition 合法（非初始 -1，mCurView 非空）时才进行预加载与章节切换检测；
        // 初始回调（oldPosition = -1）只更新进度，避免自动加载上一章/下一章
        if (oldPosition >= 0) {
            if (mLoadPrev && newPosition == 0) {
                mPresenter.loadPrev();
            }
            if (mLoadNext && newPosition == mReaderAdapter.getItemCount() - 1) {
                mPresenter.loadNext();
            }

            // 仅当位置真正变化时才检测章节切换，避免 mCurView 刷新时重复切换
            if (oldPosition != newPosition) {
                ImageUrl oldImage = mReaderAdapter.getItem(oldPosition);
                if (!oldImage.getChapter().equals(newImage.getChapter())) {
                    if (newPosition > oldPosition) {
                        mPresenter.toNextChapter();
                    } else {
                        mPresenter.toPrevChapter();
                    }
                }
            }
        }

        progress = newImage.getNum();
        updateProgress();
    }

    @Override
    public void OnPageChangeAttempted(int position, boolean previous) {
        if (previous && mLoadPrev && position == 0) {
            mPresenter.loadPrev();
        } else if (!previous && mLoadNext && position == mReaderAdapter.getItemCount() - 1) {
            mPresenter.loadNext();
        }
    }

    @Override
    public void onPrevLoadSuccess(List<ImageUrl> list) {
        mReaderAdapter.addAll(0, list);
        ((RecyclerViewPager) mRecyclerView).refreshPosition();
        HintUtils.showToast(this, R.string.reader_load_success);
    }

    @Override
    public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
        if (fromUser) {
            int intValue = Math.max(Math.round(value), 1);
            int current = getCurPosition() + intValue - progress;
            int pos = mReaderAdapter.getPositionByNum(current, intValue, intValue < progress);
            mRecyclerView.scrollToPosition(pos);
        }
    }

    @Override
    protected void prevPage() {
        hideControl();
        int position = getCurPosition();
        if (position == 0) {
            mPresenter.loadPrev();
        } else {
            mRecyclerView.smoothScrollToPosition(position - 1);
        }
    }

    @Override
    protected void nextPage() {
        hideControl();
        int position = getCurPosition();
        if (position == mReaderAdapter.getItemCount() - 1) {
            mPresenter.loadNext();
        } else {
            mRecyclerView.smoothScrollToPosition(position + 1);
        }
    }

    @Override
    protected int getCurPosition() {
        return ((RecyclerViewPager) mRecyclerView).getCurrentPosition();
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_page_reader;
    }

}
