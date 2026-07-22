package com.xyrlsz.xcimocob.utils;

import android.animation.ObjectAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.xyrlsz.xcimocob.R;

public class CustomSnackbar {

    public static void show(View parentView, String msg) {
        if (parentView == null) {
            return;
        }
        ThreadRunUtils.runOnMainThread(() -> {
            if (!parentView.isShown()) {
                return;
            }
            Snackbar snackbar = Snackbar.make(parentView, "", Snackbar.LENGTH_SHORT);
            ViewGroup snackbarLayout = (ViewGroup) snackbar.getView();

            // 使用淡入动画（默认为滑入），避免与自定义滑入冲突
            snackbar.setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE);

            // 获取主题色
            int theme = ThemeUtils.getThemeId();
            int bgColor = parentView.getContext().getResources().getColor(
                    ThemeUtils.getThemeColorById(theme));

            // 设置 Snackbar 宽度自适应内容，水平居中
            ViewGroup.LayoutParams layoutParams = snackbarLayout.getLayoutParams();
            if (layoutParams instanceof FrameLayout.LayoutParams frameParams) {
                frameParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                frameParams.gravity = Gravity.CENTER_HORIZONTAL;
                frameParams.setMargins(48, 0, 48, 48);
            } else if (layoutParams instanceof ViewGroup.MarginLayoutParams marginParams) {
                marginParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                marginParams.setMargins(48, 0, 48, 48);
            }
            snackbarLayout.setLayoutParams(layoutParams);

            // 清除 SnackbarLayout 默认背景（灰色圆角形状），只显示自定义布局的颜色
            snackbarLayout.setBackgroundColor(Color.TRANSPARENT);

            // 清除默认子View，添加自定义布局
            snackbarLayout.removeAllViews();
            View customView = LayoutInflater.from(parentView.getContext())
                    .inflate(R.layout.custom_snackbar, snackbarLayout, false);

            // 在自定义布局上设置圆角背景并应用主题色
            customView.setBackgroundResource(R.drawable.snackbar_bg);
            customView.setBackgroundTintList(ColorStateList.valueOf(bgColor));

            TextView textView = customView.findViewById(R.id.snackbar_text);
            textView.setText(msg);

            snackbarLayout.addView(customView);

            // 设置初始位置为屏幕外底部
            DisplayMetrics displayMetrics = parentView.getResources().getDisplayMetrics();
            snackbarLayout.setTranslationY(displayMetrics.heightPixels);

            // 监听视图附加到窗口后启动自定义滑入动画
            snackbarLayout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    v.removeOnAttachStateChangeListener(this);
                    // 等一帧确保布局完成
                    v.post(() -> playSlideUpAnimation(v));
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    v.removeOnAttachStateChangeListener(this);
                }
            });

            snackbar.show();
        });
    }

    private static void playSlideUpAnimation(View view) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(
                view, "translationY", view.getTranslationY(), 0);
        animator.setDuration(400);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();
    }

}
