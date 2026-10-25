package com.xyrlsz.xcimocob.upscale;

import android.graphics.Bitmap;
import android.os.Build;

public final class MangaUpscalerCompat {

    private MangaUpscalerCompat() {
    }

    /** full 版本才打包了超分 SDK；SDK 自身要求 Android 7.0 及以上 */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public static Bitmap upscale(Bitmap bitmap, int targetWidth, String cacheKey) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null;
        }
        return Api24Impl.upscale(bitmap, targetWidth, cacheKey);
    }

    public static void warmUpAsync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Api24Impl.warmUpAsync();
        }
    }

    public static void releaseAsync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Api24Impl.releaseAsync();
        }
    }

    public static void prefetchAsync(byte[] bytes, int targetWidth, String cacheKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Api24Impl.prefetchAsync(bytes, targetWidth, cacheKey);
        }
    }

    public static boolean hasCachedUpscale(String cacheKey, int targetWidth) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || cacheKey == null || targetWidth <= 0) {
            return false;
        }
        return Api24Impl.hasCachedUpscale(cacheKey, targetWidth);
    }

    public static void clearDiskCacheAsync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Api24Impl.clearDiskCacheAsync();
        }
    }

    private static class Api24Impl {
        static Bitmap upscale(Bitmap bitmap, int targetWidth, String cacheKey) {
            return MangaImageUpscaler.upscale(bitmap, targetWidth, cacheKey);
        }

        static void warmUpAsync() {
            MangaImageUpscaler.warmUpAsync();
        }

        static void releaseAsync() {
            MangaImageUpscaler.releaseAsync();
        }

        static void prefetchAsync(byte[] bytes, int targetWidth, String cacheKey) {
            MangaImageUpscaler.prefetchAsync(bytes, targetWidth, cacheKey);
        }

        static boolean hasCachedUpscale(String cacheKey, int targetWidth) {
            return UpscaleDiskCache.contains(UpscaleDiskCache.key(cacheKey, targetWidth));
        }

        static void clearDiskCacheAsync() {
            UpscaleDiskCache.clearAsync();
        }
    }
}
