package com.xyrlsz.xcimocob.utils;

import android.content.Context;

/**
 * Stub for HintUtils
 */
public class HintUtils {
    public static void showToast(Context context, String message) {
        System.err.println("[Toast] " + message);
    }

    public static void showToast(Context context, int resId) {
        System.err.println("[Toast] resource #" + resId);
    }

    public static void showToastLong(Context context, int resId) {
        System.err.println("[Toast Long] resource #" + resId);
    }
}
