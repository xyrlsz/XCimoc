package com.xyrlsz.xcimocob.upscale;

import android.graphics.Bitmap;

/**
 * 精简版（lite）的超分入口占位：不打包 RealCUGAN SDK，所有调用都是空实现。
 * 与 full 版本保持同一套 API，调用方（MangaPostprocessor / ReaderActivity 等）无需区分。
 */
public final class MangaUpscalerCompat {

    private MangaUpscalerCompat() {
    }

    public static boolean isSupported() {
        return false;
    }

    public static Bitmap upscale(Bitmap bitmap, int targetWidth, String cacheKey) {
        return null;
    }

    public static void warmUpAsync() {
    }

    public static void releaseAsync() {
    }

    public static void prefetchAsync(byte[] bytes, int targetWidth, String cacheKey) {
    }

    public static boolean hasCachedUpscale(String cacheKey, int targetWidth) {
        return false;
    }

    public static void clearDiskCacheAsync() {
    }
}
