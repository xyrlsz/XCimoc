package com.xyrlsz.xcimocob.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

/**
 * 大屏（平板、折叠屏展开）相关的尺寸判断与网格列数自适应。
 * 判定标准与阅读器的大横屏双页保持一致：最小宽度 >= 600dp 视为大屏。
 */
public class ScreenUtils {

    /** 大屏判定阈值（dp），与 values-sw600dp 等资源限定符一致 */
    public static final int LARGE_SCREEN_WIDTH_DP = 600;

    private ScreenUtils() {
    }

    public static boolean isLargeScreen(Context context) {
        return context.getResources().getConfiguration().smallestScreenWidthDp
                >= LARGE_SCREEN_WIDTH_DP;
    }

    public static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * 大屏且横屏：折叠屏展开、平板横放时使用更宽的布局
     */
    public static boolean isLargeLandscape(Context context) {
        return isLargeScreen(context) && isLandscape(context);
    }

    /**
     * 让网格列数跟随可用宽度变化：大屏按最小列宽自动计算列数，非大屏保持 fallbackSpans。
     * 旋转屏幕、折叠屏展开/折叠、分屏改尺寸都不会重建 Activity，因此监听布局变化重新计算。
     *
     * @param fallbackSpans  非大屏时的固定列数
     * @param minItemWidthDp 每格的最小宽度（dp），决定大屏上能排几列
     * @param maxSpans       列数上限，避免超大屏一格过窄
     */
    public static void bindResponsiveGrid(final RecyclerView recyclerView, final int fallbackSpans,
                                          final float minItemWidthDp, final int maxSpans) {
        if (recyclerView == null || fallbackSpans <= 0 || minItemWidthDp <= 0) {
            return;
        }
        recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                applyResponsiveGrid(recyclerView, fallbackSpans, minItemWidthDp, maxSpans);
            }
        });
        applyResponsiveGrid(recyclerView, fallbackSpans, minItemWidthDp, maxSpans);
    }

    /**
     * 自动以当前 LayoutManager 的列数作为非大屏时的固定列数
     */
    public static void bindResponsiveGrid(RecyclerView recyclerView, float minItemWidthDp,
                                          int maxSpans) {
        int fallbackSpans = getSpanCount(recyclerView.getLayoutManager());
        bindResponsiveGrid(recyclerView, fallbackSpans, minItemWidthDp, maxSpans);
    }

    /**
     * 立即按当前宽度重算列数。替换 LayoutManager 后需要手动调用一次（尺寸没变时不会触发布局回调）
     */
    public static void applyResponsiveGrid(RecyclerView recyclerView, int fallbackSpans,
                                           float minItemWidthDp, int maxSpans) {
        RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
        if (!(manager instanceof GridLayoutManager)
                && !(manager instanceof StaggeredGridLayoutManager)) {
            return;
        }
        Context context = recyclerView.getContext();
        int spanCount = fallbackSpans;
        if (isLargeScreen(context)) {
            float density = context.getResources().getDisplayMetrics().density;
            int available = recyclerView.getWidth()
                    - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight();
            if (available > 0 && density > 0) {
                spanCount = (int) (available / density / minItemWidthDp);
            }
        }
        spanCount = Math.max(1, Math.min(spanCount, maxSpans));
        if (manager instanceof GridLayoutManager) {
            ((GridLayoutManager) manager).setSpanCount(spanCount);
        } else {
            ((StaggeredGridLayoutManager) manager).setSpanCount(spanCount);
        }
    }

    private static int getSpanCount(RecyclerView.LayoutManager manager) {
        if (manager instanceof GridLayoutManager) {
            return ((GridLayoutManager) manager).getSpanCount();
        }
        if (manager instanceof StaggeredGridLayoutManager) {
            return ((StaggeredGridLayoutManager) manager).getSpanCount();
        }
        return 0;
    }
}
