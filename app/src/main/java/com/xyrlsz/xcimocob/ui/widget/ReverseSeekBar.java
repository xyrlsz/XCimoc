package com.xyrlsz.xcimocob.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.appcompat.widget.AppCompatSeekBar;

/**
 * Created by Hiroshi on 2016/8/13.
 * Replaced DiscreteSeekBar with AppCompatSeekBar + RTL layout direction.
 */
public class ReverseSeekBar extends AppCompatSeekBar {

    public ReverseSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ReverseSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ReverseSeekBar(Context context) {
        super(context);
    }

    public void setReverse(boolean reverse) {
        setLayoutDirection(reverse ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

}
