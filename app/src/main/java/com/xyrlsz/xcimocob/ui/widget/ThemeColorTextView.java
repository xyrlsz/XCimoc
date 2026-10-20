package com.xyrlsz.xcimocob.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.xyrlsz.xcimocob.component.ThemeResponsive;

public class ThemeColorTextView extends AppCompatTextView implements ThemeResponsive {

    public ThemeColorTextView(Context context) {
        super(context);
    }

    public ThemeColorTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ThemeColorTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onThemeChange(int primary, int accent) {
        setTextColor(ContextCompat.getColor(getContext(), primary));
    }
}
