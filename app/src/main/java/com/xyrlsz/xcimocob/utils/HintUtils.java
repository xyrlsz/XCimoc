package com.xyrlsz.xcimocob.utils;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

/**
 * Created by Hiroshi on 2016/9/22.
 */

public class HintUtils {

    public static void showSnackbar(View layout, String msg) {
        CustomSnackbar.show(layout, msg);
    }

    public static void showToast(Context context, int resId) {
        if (context == null) return;
        runOnMainThread(() -> {
            Toast.makeText(context.getApplicationContext(), resId, Toast.LENGTH_SHORT).show();
        });
    }

    public static void showToastLong(Context context, int resId) {
        if (context == null) return;
        runOnMainThread(() -> {
            Toast.makeText(context.getApplicationContext(), resId, Toast.LENGTH_LONG).show();
        });
    }

    public static void showToast(Context context, CharSequence text) {
        if (context == null) return;
        runOnMainThread(() -> {
            Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        });
    }

    public static void showToastLong(Context context, CharSequence text) {
        if (context == null) return;
        runOnMainThread(() -> {
            Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_LONG).show();
        });
    }

    private static void runOnMainThread(Runnable runnable) {
        ThreadRunUtils.runOnMainThread(runnable);
    }

}
