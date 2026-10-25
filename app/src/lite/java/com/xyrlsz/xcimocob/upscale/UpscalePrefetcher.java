package com.xyrlsz.xcimocob.upscale;

import com.facebook.imagepipeline.core.ImagePipelineFactory;

/**
 * 精简版（lite）不做超分，自然也不需要预取。
 */
public final class UpscalePrefetcher {

    private UpscalePrefetcher() {
    }

    public static void prefetch(ImagePipelineFactory factory, String url, int targetWidth,
                                String cacheKey) {
    }
}
