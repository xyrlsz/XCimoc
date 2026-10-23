package com.xyrlsz.xcimocob.ui.widget.rvp;

import android.content.Context;
import android.graphics.PointF;
import android.os.Build;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * RecyclerViewPager
 *
 * @author Green
 * <p>
 * https://github.com/lsjwzh/RecyclerViewPager
 */
public class RecyclerViewPager extends RecyclerView {

    private static final float FLING_FACTOR = 0.10f;
    boolean mNeedAdjust;
    int mFirstLeftWhenDragging;
    int mFirstTopWhenDragging;
    View mCurView;
    int mMaxLeftWhenDragging = Integer.MIN_VALUE;
    int mMinLeftWhenDragging = Integer.MAX_VALUE;
    int mMaxTopWhenDragging = Integer.MIN_VALUE;
    int mMinTopWhenDragging = Integer.MAX_VALUE;
    private RecyclerViewPagerAdapter<?> mViewPagerAdapter;
    private float mTouchSpan;
    private OnPageChangedListener mOnPageChangedListener;
    private GlobalLayoutListener mGlobalLayoutListener = new GlobalLayoutListener();
    private int mSmoothScrollTargetPosition = -1;
    private int mPositionBeforeScroll = -1;
    private float mTriggerOffset = 0.05f;
    private Float mScrollSpeed;
    private int mPositionOnTouchDown = -1;
    private boolean mHasCalledOnPageChanged = true;
    private boolean reverseLayout = false;
    /**
     * 手势起点坐标，用于判断是否在首/末页继续向外滑动
     */
    private float mDownX;
    private float mDownY;
    private boolean mBoundaryAttemptNotified;

    public RecyclerViewPager(Context context) {
        this(context, null);
    }

    public RecyclerViewPager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecyclerViewPager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setNestedScrollingEnabled(false);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        try {
            Field fLayoutState = state.getClass().getDeclaredField("mLayoutState");
            fLayoutState.setAccessible(true);
            Object layoutState = fLayoutState.get(state);
            Field fAnchorOffset = Objects.requireNonNull(layoutState).getClass().getDeclaredField("mAnchorOffset");
            Field fAnchorPosition = layoutState.getClass().getDeclaredField("mAnchorPosition");
            fAnchorPosition.setAccessible(true);
            fAnchorOffset.setAccessible(true);
            if (fAnchorOffset.getInt(layoutState) > 0) {
                fAnchorPosition.set(layoutState, fAnchorPosition.getInt(layoutState) - 1);
            } else if (fAnchorOffset.getInt(layoutState) < 0) {
                fAnchorPosition.set(layoutState, fAnchorPosition.getInt(layoutState) + 1);
            }
            fAnchorOffset.setInt(layoutState, 0);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        super.onRestoreInstanceState(state);
    }

    @Override
    public void swapAdapter(Adapter adapter, boolean removeAndRecycleExistingViews) {
        mViewPagerAdapter = ensureRecyclerViewPagerAdapter(adapter);
        super.swapAdapter(mViewPagerAdapter, removeAndRecycleExistingViews);
    }

    @Override
    public Adapter getAdapter() {
        if (mViewPagerAdapter != null) {
            return mViewPagerAdapter.mAdapter;
        }
        return null;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        mViewPagerAdapter = ensureRecyclerViewPagerAdapter(adapter);
        super.setAdapter(mViewPagerAdapter);
    }

    @Override
    public void setLayoutManager(LayoutManager layout) {
        super.setLayoutManager(layout);
        if (layout instanceof LinearLayoutManager) {
            reverseLayout = ((LinearLayoutManager) layout).getReverseLayout();
        }
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        boolean flinging = super.fling((int) (velocityX * FLING_FACTOR), (int) (velocityY * FLING_FACTOR));
        // 首/末页时内容无法继续滚动，只能靠速度方向判断用户是否想继续翻到上/下一章
        checkBoundaryAttemptByVelocity(velocityX, velocityY);
        if (flinging) {
            if (Objects.requireNonNull(getLayoutManager()).canScrollHorizontally()) {
                adjustPositionX(velocityX);
            } else {
                adjustPositionY(velocityY);
            }
        }
        return flinging;
    }

    public void startSmoothScroll(int position) {
        mSmoothScrollTargetPosition = position;
        LinearSmoothScroller linearSmoothScroller =
                new LinearSmoothScroller(getContext()) {
                    @Override
                    public PointF computeScrollVectorForPosition(int targetPosition) {
                        if (getLayoutManager() == null) {
                            return null;
                        }
                        return ((LinearLayoutManager) getLayoutManager())
                                .computeScrollVectorForPosition(targetPosition);
                    }

                    @Override
                    protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                        return getContext().getResources().getDisplayMetrics().density
                                * mScrollSpeed / displayMetrics.density;
                    }
                };
        linearSmoothScroller.setTargetPosition(position);
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        Objects.requireNonNull(getLayoutManager()).startSmoothScroll(linearSmoothScroller);
    }

    @Override
    public void smoothScrollToPosition(int position) {
        if (mPositionBeforeScroll < 0) {
            mPositionBeforeScroll = getCurrentPosition();
        }
        startSmoothScroll(position);

        if (mSmoothScrollTargetPosition >= 0 && mSmoothScrollTargetPosition < getItemCount() &&
                mSmoothScrollTargetPosition != mPositionBeforeScroll && mOnPageChangedListener != null) {
            getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
        }
    }

    @Override
    public void scrollToPosition(int position) {
        mPositionBeforeScroll = getCurrentPosition();
        mSmoothScrollTargetPosition = position;
        super.scrollToPosition(position);

        if (mSmoothScrollTargetPosition >= 0 && mSmoothScrollTargetPosition < getItemCount() &&
                mSmoothScrollTargetPosition != mPositionBeforeScroll && mOnPageChangedListener != null) {
            getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
        }
    }

    private int getItemCount() {
        return mViewPagerAdapter == null ? 0 : mViewPagerAdapter.getItemCount();
    }

    /**
     * get item position in center of viewpager
     */
    public int getCurrentPosition() {
        int curPosition;
        if (Objects.requireNonNull(getLayoutManager()).canScrollHorizontally()) {
            curPosition = RecyclerViewUtils.getCenterXChildPosition(this);
        } else {
            curPosition = RecyclerViewUtils.getCenterYChildPosition(this);
        }
        if (curPosition < 0) {
            curPosition = mSmoothScrollTargetPosition;
        }
        return curPosition;
    }

    /***
     * adjust position before Touch event complete and fling action start.
     */
    protected void adjustPositionX(int velocityX) {
        if (reverseLayout) velocityX *= -1;

        int childCount = getChildCount();
        if (childCount > 0) {
            int curPosition = RecyclerViewUtils.getCenterXChildPosition(this);
            int childWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            int flingCount = Math.max(-1, Math.min(1, getFlingCount(velocityX, childWidth)));
            int targetPosition = flingCount == 0 ? curPosition : mPositionOnTouchDown + flingCount;
            targetPosition = Math.max(targetPosition, 0);
            targetPosition = Math.min(targetPosition, getItemCount() - 1);
            if (targetPosition == curPosition && mPositionOnTouchDown == curPosition) {
                View centerXChild = RecyclerViewUtils.getCenterXChild(this);
                if (centerXChild != null) {
                    if (mTouchSpan > centerXChild.getWidth() * mTriggerOffset * mTriggerOffset && targetPosition != 0) {
                        if (!reverseLayout) targetPosition--;
                        else targetPosition++;
                    } else if (mTouchSpan < centerXChild.getWidth() * -mTriggerOffset && targetPosition != getItemCount() - 1) {
                        if (!reverseLayout) targetPosition++;
                        else targetPosition--;
                    }
                }
            }
            startSmoothScroll(safeTargetPosition(targetPosition, getItemCount()));
        }
    }

    /***
     * adjust position before Touch event complete and fling action start.
     */
    protected void adjustPositionY(int velocityY) {
        if (reverseLayout) velocityY *= -1;

        int childCount = getChildCount();
        if (childCount > 0) {
            int curPosition = RecyclerViewUtils.getCenterYChildPosition(this);
            int childHeight = getHeight() - getPaddingTop() - getPaddingBottom();
            int flingCount = Math.max(-1, Math.min(1, getFlingCount(velocityY, childHeight)));
            int targetPosition = flingCount == 0 ? curPosition : mPositionOnTouchDown + flingCount;
            targetPosition = Math.max(targetPosition, 0);
            targetPosition = Math.min(targetPosition, getItemCount() - 1);
            if (targetPosition == curPosition && mPositionOnTouchDown == curPosition) {
                View centerYChild = RecyclerViewUtils.getCenterYChild(this);
                if (centerYChild != null) {
                    if (mTouchSpan > centerYChild.getHeight() * mTriggerOffset && targetPosition != 0) {
                        if (!reverseLayout) targetPosition--;
                        else targetPosition++;
                    } else if (mTouchSpan < centerYChild.getHeight() * -mTriggerOffset && targetPosition != getItemCount() - 1) {
                        if (!reverseLayout) targetPosition++;
                        else targetPosition--;
                    }
                }
            }
            startSmoothScroll(safeTargetPosition(targetPosition, getItemCount()));
        }
    }

    public void setScrollSpeed(float speed) {
        mScrollSpeed = speed;
    }

    public void setTriggerOffset(float offset) {
        mTriggerOffset = offset;
    }

    public void setOnPageChangedListener(OnPageChangedListener listener) {
        mOnPageChangedListener = listener;
    }

    /**
     * 直接跳转到指定位置并重置内部翻页状态。
     * 用于列表项位置含义发生变化（如切换左右双页显示、向前插入章节）后定位。
     */
    public void resetToPosition(int position) {
        mPositionBeforeScroll = position;
        mPositionOnTouchDown = position;
        mSmoothScrollTargetPosition = position;
        mHasCalledOnPageChanged = true;
        mCurView = null;
        mNeedAdjust = false;
        super.scrollToPosition(position);
    }

    public void refreshPosition() {
        int pos = getCurrentPosition();
        if (mPositionBeforeScroll != -1) {
            mPositionBeforeScroll += pos;
        }
        mPositionOnTouchDown += pos;
        mSmoothScrollTargetPosition += pos;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mDownX = ev.getX();
            mDownY = ev.getY();
            mBoundaryAttemptNotified = false;
            if (getLayoutManager() != null) {
                mPositionOnTouchDown = getLayoutManager().canScrollHorizontally()
                        ? RecyclerViewUtils.getCenterXChildPosition(this)
                        : RecyclerViewUtils.getCenterYChildPosition(this);
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            if (mCurView != null) {
                mMaxLeftWhenDragging = Math.max(mCurView.getLeft(), mMaxLeftWhenDragging);
                mMaxTopWhenDragging = Math.max(mCurView.getTop(), mMaxTopWhenDragging);
                mMinLeftWhenDragging = Math.min(mCurView.getLeft(), mMinLeftWhenDragging);
                mMinTopWhenDragging = Math.min(mCurView.getTop(), mMinTopWhenDragging);
            }
        } else if (action == MotionEvent.ACTION_UP) {
            checkBoundaryAttemptByDrag(e.getX() - mDownX, e.getY() - mDownY);
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent event) {
        try {
            return super.onInterceptHoverEvent(event);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);
        if (state == SCROLL_STATE_DRAGGING) {
            mNeedAdjust = true;
            mCurView = Objects.requireNonNull(getLayoutManager()).canScrollHorizontally() ? RecyclerViewUtils.getCenterXChild(this) :
                    RecyclerViewUtils.getCenterYChild(this);
            if (mCurView != null) {
                if (mHasCalledOnPageChanged) {
                    // While rvp is scrolling, mPositionBeforeScroll will be previous value.
                    mPositionBeforeScroll = getChildLayoutPosition(mCurView);
                    mHasCalledOnPageChanged = false;
                }
                mFirstLeftWhenDragging = mCurView.getLeft();
                mFirstTopWhenDragging = mCurView.getTop();
            } else {
                mPositionBeforeScroll = -1;
            }
            mTouchSpan = 0;
        } else if (state == SCROLL_STATE_SETTLING) {
            mNeedAdjust = false;
            if (mCurView != null) {
                if (Objects.requireNonNull(getLayoutManager()).canScrollHorizontally()) {
                    mTouchSpan = mCurView.getLeft() - mFirstLeftWhenDragging;
                } else {
                    mTouchSpan = mCurView.getTop() - mFirstTopWhenDragging;
                }
            } else {
                mTouchSpan = 0;
            }
            mCurView = null;
        } else if (state == SCROLL_STATE_IDLE) {
            if (mNeedAdjust) {
                int targetPosition = Objects.requireNonNull(getLayoutManager()).canScrollHorizontally() ? RecyclerViewUtils.getCenterXChildPosition(this) :
                        RecyclerViewUtils.getCenterYChildPosition(this);
                if (mCurView != null) {
                    targetPosition = getChildAdapterPosition(mCurView);
                    if (getLayoutManager().canScrollHorizontally()) {
                        int spanX = mCurView.getLeft() - mFirstLeftWhenDragging;
                        // if user is tending to cancel paging action, don't perform position changing
                        if (spanX > mCurView.getWidth() * mTriggerOffset && mCurView.getLeft() >= mMaxLeftWhenDragging) {
                            if (!reverseLayout) targetPosition--;
                            else targetPosition++;
                        } else if (spanX < mCurView.getWidth() * -mTriggerOffset && mCurView.getLeft() <= mMinLeftWhenDragging) {
                            if (!reverseLayout) targetPosition++;
                            else targetPosition--;
                        }
                    } else {
                        int spanY = mCurView.getTop() - mFirstTopWhenDragging;
                        if (spanY > mCurView.getHeight() * mTriggerOffset && mCurView.getTop() >= mMaxTopWhenDragging) {
                            if (!reverseLayout) targetPosition--;
                            else targetPosition++;
                        } else if (spanY < mCurView.getHeight() * -mTriggerOffset && mCurView.getTop() <= mMinTopWhenDragging) {
                            if (!reverseLayout) targetPosition++;
                            else targetPosition--;
                        }
                    }
                }
                startSmoothScroll(safeTargetPosition(targetPosition, getItemCount()));
                mCurView = null;
            } else if (mSmoothScrollTargetPosition != mPositionBeforeScroll) {
                if (mOnPageChangedListener != null) {
                    mOnPageChangedListener.OnPageChanged(mPositionBeforeScroll, getCurrentPosition());
                }
                mHasCalledOnPageChanged = true;
                mPositionBeforeScroll = mSmoothScrollTargetPosition;
            }
            // reset
            mMaxLeftWhenDragging = Integer.MIN_VALUE;
            mMinLeftWhenDragging = Integer.MAX_VALUE;
            mMaxTopWhenDragging = Integer.MIN_VALUE;
            mMinTopWhenDragging = Integer.MAX_VALUE;
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    protected RecyclerViewPagerAdapter ensureRecyclerViewPagerAdapter(Adapter adapter) {
        return (adapter instanceof RecyclerViewPagerAdapter)
                ? (RecyclerViewPagerAdapter) adapter
                : new RecyclerViewPagerAdapter(this, adapter);
    }

    private int getFlingCount(int velocity, int cellSize) {
        if (velocity == 0) {
            return 0;
        }
        int sign = velocity > 0 ? 1 : -1;
        return (int) (sign * Math.ceil((velocity * sign * FLING_FACTOR / cellSize)
                - mTriggerOffset));
    }

    /**
     * fling 时依据速度判断是否在首/末页继续向外翻页。
     * <p>
     * fling 收到的速度与手指方向相反，且 {@link #adjustPositionX}/{@link #adjustPositionY}
     * 内部会按 reverseLayout 再翻转一次，这里沿用同样的算法保证方向一致。
     */
    private void checkBoundaryAttemptByVelocity(int velocityX, int velocityY) {
        LayoutManager layoutManager = getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        boolean horizontal = layoutManager.canScrollHorizontally();
        int velocity = horizontal ? velocityX : velocityY;
        if (reverseLayout) {
            velocity = -velocity;
        }
        int cellSize = horizontal
                ? getWidth() - getPaddingLeft() - getPaddingRight()
                : getHeight() - getPaddingTop() - getPaddingBottom();
        if (cellSize <= 0) {
            return;
        }
        int flingCount = Math.max(-1, Math.min(1, getFlingCount(velocity, cellSize)));
        if (flingCount == 0) {
            return;
        }
        notifyBoundaryAttempt(flingCount > 0);
    }

    /**
     * 松手时依据手指实际位移判断是否在首/末页继续向外翻页。
     * 首/末页时内容无法滚动，只能靠手指位移判断方向。
     */
    private void checkBoundaryAttemptByDrag(float deltaX, float deltaY) {
        if (mBoundaryAttemptNotified) {
            return;
        }
        LayoutManager layoutManager = getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        boolean horizontal = layoutManager.canScrollHorizontally();
        float size = horizontal
                ? getWidth() - getPaddingLeft() - getPaddingRight()
                : getHeight() - getPaddingTop() - getPaddingBottom();
        if (size <= 0) {
            return;
        }
        // 触发距离取用户的翻页阈值，并限制在 5% ~ 25% 之间，避免阈值过大导致永远无法触发
        float triggerRatio = Math.min(Math.max(mTriggerOffset, 0.05f), 0.25f);
        float delta = horizontal ? deltaX : deltaY;
        if (Math.abs(delta) <= size * triggerRatio) {
            return;
        }
        // 手指位移方向与翻页方向相反：向右拖动是上一页，reverseLayout 时相反
        delta = reverseLayout ? delta : -delta;
        notifyBoundaryAttempt(delta > 0);
    }

    /**
     * 仅在首/末页时通知监听者：用户正试图继续向外翻页。
     *
     * @param next true 表示试图翻到下一页（即末尾），false 表示试图翻到上一页（即开头）
     */
    private void notifyBoundaryAttempt(boolean next) {
        if (mBoundaryAttemptNotified) {
            return;
        }
        int itemCount = getItemCount();
        int position = getCurrentPosition();
        if (itemCount <= 0 || position < 0) {
            return;
        }
        if (next ? position != itemCount - 1 : position != 0) {
            return;
        }
        mBoundaryAttemptNotified = true;
        if (mOnPageChangedListener != null) {
            mOnPageChangedListener.OnPageChangeAttempted(position, !next);
        }
    }

    private int safeTargetPosition(int position, int count) {
        if (position < 0) {
            return 0;
        }
        if (position >= count) {
            return count - 1;
        }
        return position;
    }

    public interface OnPageChangedListener {
        void OnPageChanged(int oldPosition, int newPosition);

        void OnPageChangeAttempted(int position, boolean previous);
    }

    private class GlobalLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
        @Override
        public void onGlobalLayout() {
            if (Build.VERSION.SDK_INT < 16) {
                getViewTreeObserver().removeGlobalOnLayoutListener(this);
            } else {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }

            if (mSmoothScrollTargetPosition >= 0 && mSmoothScrollTargetPosition < getItemCount()) {
                if (mOnPageChangedListener != null) {
                    mOnPageChangedListener.OnPageChanged(mPositionBeforeScroll, getCurrentPosition());
                }
            }
        }
    }

}
