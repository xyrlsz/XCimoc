package com.xyrlsz.xcimocob.ui.widget.preference;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.content.ContextCompat;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.ThemeResponsive;
import com.xyrlsz.xcimocob.manager.PreferenceManager;

/**
 * Created by Hiroshi on 2017/1/10.
 */

public class CheckBoxPreference extends FrameLayout implements View.OnClickListener, ThemeResponsive {

    private AppCompatCheckBox mCheckBox;
    private PreferenceManager mPreferenceManager;
    private String mPreferenceKey;

    public CheckBoxPreference(Context context) {
        this(context, null);
    }

    public CheckBoxPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.custom_option_checkbox, this);

        mPreferenceManager = ((App) context.getApplicationContext()).getPreferenceManager();

        init(context, attrs);

        setOnClickListener(this);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.Option);
        String title = typedArray.getString(R.styleable.Option_title);
        String summary = typedArray.getString(R.styleable.Option_summary);

        mCheckBox = this.findViewById(R.id.custom_option_checkbox);
        TextView titleView = this.findViewById(R.id.custom_option_title);
        TextView summaryView = this.findViewById(R.id.custom_option_summary);

        titleView.setText(title);
        summaryView.setText(summary);

        typedArray.recycle();
    }

    @Override
    public void onClick(View v) {
        if (mPreferenceKey != null) {
            boolean checked = !mCheckBox.isChecked();
            mCheckBox.setChecked(checked);
            mPreferenceManager.putBoolean(mPreferenceKey, checked);
        }
    }

    public void bindPreference(String key, boolean def) {
        mPreferenceKey = key;
        mCheckBox.setChecked(mPreferenceManager.getBoolean(key, def));
    }

    @SuppressLint("RestrictedApi")
    public void setColorStateList(ColorStateList stateList) {
        mCheckBox.setSupportButtonTintList(stateList);
    }

    @Override
    public void onThemeChange(int primary, int accent) {
        ColorStateList stateList = new ColorStateList(
                new int[][]{{-android.R.attr.state_checked}, {android.R.attr.state_checked}},
                new int[]{0x8A000000, ContextCompat.getColor(getContext(), accent)});
        setColorStateList(stateList);
    }

    public boolean isChecked() {
        return mCheckBox.isChecked();
    }

    public void setChecked(boolean checked) {
        mCheckBox.setChecked(checked);
    }

    public void setSummary(String summary) {
        TextView summaryView = this.findViewById(R.id.custom_option_summary);
        summaryView.setText(summary);
    }

}
