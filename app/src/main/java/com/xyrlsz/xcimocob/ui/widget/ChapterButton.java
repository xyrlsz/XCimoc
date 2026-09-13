package com.xyrlsz.xcimocob.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.xyrlsz.xcimocob.R;

/**
 * Created by Hiroshi on 2016/10/2.
 */

public class ChapterButton extends AppCompatTextView {

    private static final int[] NORMAL_STATE = new int[]{-android.R.attr.state_selected};
    private static final int[] SELECTED_STATE = new int[]{android.R.attr.state_selected};
    private static final int[] FOCUSED_STATE = new int[]{android.R.attr.state_focused, -android.R.attr.state_selected};

    private int normalColor;
    private int accentColor;
    private int focusedColor;
    private boolean download;

    public ChapterButton(Context context) {
        this(context, null);
    }

    public ChapterButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChapterButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.ChapterButton, 0, 0);
        TypedArray typedArray2 = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.ThemeAttributes, 0, 0);
        int defaultColor = ContextCompat.getColor(context, R.color.colorAutoG_GW);
        accentColor = typedArray.getColor(R.styleable.ChapterButton_selected_color, defaultColor);
        normalColor = typedArray2.getColor(R.styleable.ThemeAttributes_colorAutoG_GW, defaultColor);
        typedArray.recycle();
        typedArray2.recycle();
        focusedColor = (accentColor & 0x00FFFFFF) | 0x33000000;

        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(false);
        download = false;
        initColorDrawableState();
        initDrawableState();
    }

    private void initColorDrawableState() {
        ColorStateList colorStateList = new ColorStateList(new int[][]{FOCUSED_STATE, SELECTED_STATE, NORMAL_STATE},
                new int[]{Color.WHITE, Color.WHITE, normalColor});
        setTextColor(colorStateList);
    }

    private void initDrawableState() {
        GradientDrawable normalDrawable = new GradientDrawable();
        normalDrawable.setStroke((int) ViewUtils.dpToPixel(1, getContext()), normalColor);
        normalDrawable.setCornerRadius(ViewUtils.dpToPixel(18, getContext()));
        normalDrawable.setColor(Color.TRANSPARENT);

        GradientDrawable selectedDrawable = new GradientDrawable();
        selectedDrawable.setStroke((int) ViewUtils.dpToPixel(1, getContext()), accentColor);
        selectedDrawable.setCornerRadius(ViewUtils.dpToPixel(18, getContext()));
        selectedDrawable.setColor(accentColor);

        GradientDrawable focusedDrawable = new GradientDrawable();
        focusedDrawable.setStroke((int) ViewUtils.dpToPixel(1, getContext()), accentColor);
        focusedDrawable.setCornerRadius(ViewUtils.dpToPixel(18, getContext()));
        focusedDrawable.setColor(focusedColor);

        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(FOCUSED_STATE, focusedDrawable);
        stateList.addState(SELECTED_STATE, selectedDrawable);
        stateList.addState(NORMAL_STATE, normalDrawable);
        setBackgroundDrawable(stateList);
    }

    public void setDownload(boolean download) {
        if (this.download != download) {
            this.download = download;
            normalColor = download ? accentColor : ContextCompat.getColor(getContext(), R.color.colorAutoG_GW);
            initColorDrawableState();
            initDrawableState();
        }
    }

}
