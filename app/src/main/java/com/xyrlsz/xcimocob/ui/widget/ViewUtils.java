package com.xyrlsz.xcimocob.ui.widget;

import android.content.Context;
import android.graphics.Matrix;
import android.os.Build;
import android.view.View;

/**
 * Created by Hiroshi on 2016/10/12.
 */

public class ViewUtils {

    /** 网格卡片的目标宽度（dp）：列数由可用宽度除以它得出，让不同屏幕上卡片大小接近 */
    public static final int GRID_ITEM_TARGET_WIDTH_DP = 140;
    /** 网格最少 / 最多列数，避免极窄或极宽的屏幕上算出离谱的列数 */
    private static final int GRID_MIN_SPAN_COUNT = 2;
    private static final int GRID_MAX_SPAN_COUNT = 8;

    public static float dpToPixel(float dp, Context context) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    public static int dpToPixelInt(int dp, Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f); // +0.5f 是为了四舍五入
    }

    public static void postOnAnimation(View view, Runnable runnable) {
        if (Build.VERSION.SDK_INT >= 16) {
            view.postOnAnimation(runnable);
        } else {
            view.postDelayed(runnable, 16L);
        }
    }

    public static float calculateScale(Matrix matrix) {
        float[] values = new float[9];
        matrix.getValues(values);
        return (float) Math.sqrt((float) Math.pow(values[Matrix.MSCALE_X], 2) +
                (float) Math.pow(values[Matrix.MSKEW_Y], 2));
    }

    public static int getViewWidth(View view) {
        return view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
    }

    public static int getViewHeight(View view) {
        return view.getHeight() - view.getPaddingTop() - view.getPaddingBottom();
    }

    /**
     * 按可用宽度和卡片目标宽度计算网格列数。
     * 只按屏幕宽高比给固定列数时，卡片宽度会跟着屏幕一起放大或缩小；
     * 这里让不同尺寸屏幕上的卡片宽度都落在目标宽度附近，并限制在合理列数范围内。
     */
    public static int calcGridSpanCount(Context context, int availableWidthPx) {
        int targetWidthPx = dpToPixelInt(GRID_ITEM_TARGET_WIDTH_DP, context);
        if (availableWidthPx <= 0 || targetWidthPx <= 0) {
            return GRID_MIN_SPAN_COUNT;
        }
        int spanCount = Math.round((float) availableWidthPx / targetWidthPx);
        return Math.min(GRID_MAX_SPAN_COUNT, Math.max(GRID_MIN_SPAN_COUNT, spanCount));
    }

}
