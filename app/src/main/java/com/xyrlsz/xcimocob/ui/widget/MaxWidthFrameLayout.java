package com.xyrlsz.xcimocob.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.utils.ScreenUtils;

/**
 * 内容区限宽容器：横屏大屏时列表/设置项不再整行铺满，避免一行过长。
 * 比 contentMaxWidth 窄的屏幕（手机竖屏）不受影响；配合 layout_gravity="center_horizontal" 居中。
 */
public class MaxWidthFrameLayout extends FrameLayout {

    private int mContentMaxWidth;

    public MaxWidthFrameLayout(@NonNull Context context) {
        this(context, null);
    }

    public MaxWidthFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaxWidthFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs,
                               int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            TypedArray array = context.obtainStyledAttributes(
                    attrs, R.styleable.MaxWidthFrameLayout);
            mContentMaxWidth = array.getDimensionPixelSize(
                    R.styleable.MaxWidthFrameLayout_contentMaxWidth, 0);
            array.recycle();
        }
    }

    public void setContentMaxWidth(int px) {
        if (mContentMaxWidth == px) {
            return;
        }
        mContentMaxWidth = px;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        // 只在平板/折叠屏展开这类大屏上限制宽度，手机（含横屏，宽度可能超过 840dp）保持原样
        if (mContentMaxWidth > 0 && width > mContentMaxWidth
                && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
                && ScreenUtils.isLargeScreen(getContext())) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(mContentMaxWidth, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
