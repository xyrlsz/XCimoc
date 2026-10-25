package com.xyrlsz.xcimocob.upscale;

import android.net.Uri;
import android.util.Log;
import android.util.LruCache;

import com.facebook.common.memory.PooledByteBuffer;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.BaseDataSubscriber;
import com.facebook.datasource.DataSource;
import com.facebook.imagepipeline.core.ImagePipelineFactory;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 超分预取：在后台把后续页的原始字节取出来交给 {@link MangaImageUpscaler}。
 *
 * 只取 Fresco 的编码数据（内存/磁盘缓存或下载），不走解码与后处理链路，
 * 因此不会触发 {@code MangaPostprocessor} 里的切图 / 裁白边状态变更。
 * 同时下载下一张图本身也是后续翻页需要的，不算额外开销。
 */
public final class UpscalePrefetcher {

    private static final String TAG = "UpscalePrefetcher";
    private static final int MAX_TRACKED = 64;

    private static final Executor CALLBACK_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "UpscalePrefetcher");
        thread.setDaemon(true);
        return thread;
    });

    /** 已经发起过预取的「图片 + 目标宽度」，避免重复解码同一张图 */
    private static final LruCache<String, Boolean> REQUESTED = new LruCache<>(MAX_TRACKED);

    private UpscalePrefetcher() {
    }

    public static void prefetch(ImagePipelineFactory factory, String url, int targetWidth,
                                String cacheKey) {
        if (factory == null || url == null || cacheKey == null || targetWidth <= 0) {
            return;
        }
        final String trackKey = targetWidth + "|" + url;
        if (REQUESTED.get(trackKey) != null) {
            return;
        }
        REQUESTED.put(trackKey, Boolean.TRUE);
        // 判断缓存与发起请求都放到后台线程，避免主线程做磁盘 IO
        CALLBACK_EXECUTOR.execute(() -> start(factory, url, targetWidth, cacheKey, trackKey));
    }

    private static void start(ImagePipelineFactory factory, String url, int targetWidth,
                              String cacheKey, String trackKey) {
        try {
            if (MangaUpscalerCompat.hasCachedUpscale(cacheKey, targetWidth)) {
                return;
            }
            ImageRequest request = ImageRequestBuilder.newBuilderWithSource(Uri.parse(url)).build();
            DataSource<CloseableReference<PooledByteBuffer>> dataSource =
                    factory.getImagePipeline().fetchEncodedImage(request, null);
            dataSource.subscribe(new BaseDataSubscriber<CloseableReference<PooledByteBuffer>>() {
                @Override
                protected void onNewResultImpl(
                        DataSource<CloseableReference<PooledByteBuffer>> source) {
                    if (!source.isFinished()) {
                        return;
                    }
                    byte[] bytes = copyBytes(source.getResult());
                    if (bytes != null) {
                        MangaUpscalerCompat.prefetchAsync(bytes, targetWidth, cacheKey);
                    }
                }

                @Override
                protected void onFailureImpl(
                        DataSource<CloseableReference<PooledByteBuffer>> source) {
                    // 预取失败不影响正常阅读，允许下次滚回来时再试
                    REQUESTED.remove(trackKey);
                }
            }, CALLBACK_EXECUTOR);
        } catch (Exception | LinkageError error) {
            REQUESTED.remove(trackKey);
            Log.w(TAG, "Failed to prefetch upscale source", error);
        }
    }

    private static byte[] copyBytes(CloseableReference<PooledByteBuffer> reference) {
        if (reference == null || !reference.isValid()) {
            return null;
        }
        try {
            PooledByteBuffer buffer = reference.get();
            byte[] bytes = new byte[buffer.size()];
            buffer.read(0, bytes, 0, bytes.length);
            return bytes;
        } catch (Exception error) {
            Log.w(TAG, "Failed to read encoded image", error);
            return null;
        } finally {
            CloseableReference.closeSafely(reference);
        }
    }
}
