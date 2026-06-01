package com.xyrlsz.xcimocob.ui.adapter;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.backends.pipeline.PipelineDraweeControllerBuilder;
import com.facebook.drawee.backends.pipeline.PipelineDraweeControllerBuilderSupplier;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.view.DraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.core.ImagePipelineFactory;
import com.facebook.imagepipeline.image.ImageInfo;
import com.facebook.imagepipeline.listener.BaseRequestListener;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.fresco.ComicFrescoHeaders;
import com.xyrlsz.xcimocob.fresco.ControllerBuilderSupplierFactory;
import com.xyrlsz.xcimocob.fresco.ImagePipelineFactoryBuilder;
import com.xyrlsz.xcimocob.fresco.processor.MangaPostprocessor;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.ui.widget.OnTapGestureListener;
import com.xyrlsz.xcimocob.ui.widget.PhotoDraweeView;
import com.xyrlsz.xcimocob.ui.widget.RetryDraweeView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Headers;

/**
 * Created by Hiroshi on 2016/8/5.
 */
public class ReaderAdapter extends BaseAdapter<ImageUrl> {

    public static final int READER_PAGE = 0;
    public static final int READER_STREAM = 1;

    private static final int TYPE_LOADING = 2016101214;
    private static final int TYPE_IMAGE = 2016101215;
    private static final int TYPE_IMAGE_PAGE = 0;
    private static final int TYPE_IMAGE_STREAM = 1;
    private static @ReaderMode int reader;
    private PipelineDraweeControllerBuilderSupplier mControllerSupplier;
    private PipelineDraweeControllerBuilderSupplier mLargeControllerSupplier;
    private OnTapGestureListener mTapGestureListener;
    private OnLazyLoadListener mLazyLoadListener;
    private boolean isVertical; // 开页方向
    private boolean isPaging;
    private boolean isPagingReverse;
    private boolean isWhiteEdge;
    private boolean isBanTurn;
    private boolean isDoubleTap;
    private boolean isCloseAutoResizeImage;
    private float mScaleFactor;

    // 缓存 ImagePipelineFactory/Supplier 对（按 headers 缓存），减少重复创建开销
    private static final int MAX_SUPPLIER_CACHE = 16;
    private final Map<String, SupplierPair> mSupplierCache = new HashMap<>();

    private static class SupplierPair {
        final PipelineDraweeControllerBuilderSupplier normal;
        final PipelineDraweeControllerBuilderSupplier large;
        SupplierPair(PipelineDraweeControllerBuilderSupplier normal,
                     PipelineDraweeControllerBuilderSupplier large) {
            this.normal = normal;
            this.large = large;
        }
    }


    public ReaderAdapter(Context context, List<ImageUrl> list) {
        super(context, list);
    }

    @Override
    public int getItemViewType(int position) {
//        return mDataSet.get(position).isLazy() ? TYPE_LOADING : TYPE_IMAGE;
        if (mDataSet.get(position).isLazy()) return TYPE_LOADING;

        return reader == READER_PAGE ? TYPE_IMAGE_PAGE : TYPE_IMAGE_STREAM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean isWhiteBackground = App.getPreferenceManager()
                .getBoolean(PreferenceManager.PREF_READER_WHITE_BACKGROUND, false);

        int resId;

        if (viewType == TYPE_LOADING) {
            resId = isWhiteBackground ? R.layout.item_loading_black : R.layout.item_loading;
            View view = mInflater.inflate(resId, parent, false);
            return new LoadingHolder(view);
        }

        if (viewType == TYPE_IMAGE_PAGE) {
            resId = isWhiteBackground ? R.layout.item_picture_black : R.layout.item_picture;
            View view = mInflater.inflate(resId, parent, false);
            return new PageHolder(view); // 👈 用 PhotoDraweeView
        }

        // TYPE_IMAGE_STREAM
        resId = isWhiteBackground ? R.layout.item_picture_stream_black : R.layout.item_picture_stream;
        View view = mInflater.inflate(resId, parent, false);
        return new StreamHolder(view); // 👈 用 RetryDraweeView
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final ImageUrl imageUrl = mDataSet.get(position);

        // 处理懒加载占位
        if (imageUrl.isLazy()) {
            if (!imageUrl.isLoading() && mLazyLoadListener != null) {
                imageUrl.setLoading(true);
                mLazyLoadListener.onLoad(imageUrl);
            }
            return;
        }

        // 根据 holder 类型获取 draweeView
        DraweeView draweeView;
        boolean isPageMode;

        if (holder instanceof PageHolder) {
            PhotoDraweeView photoView = ((PageHolder) holder).draweeView;
            draweeView = photoView;
            isPageMode = true;

            // Page 模式特有配置
            photoView.setTapListenerListener(mTapGestureListener);
            photoView.setAlwaysBlockParent(isBanTurn);
            photoView.setDoubleTap(isDoubleTap);
            photoView.setScaleFactor(mScaleFactor);
            photoView.setScrollMode(isVertical ?
                    PhotoDraweeView.MODE_VERTICAL :
                    PhotoDraweeView.MODE_HORIZONTAL);

        } else if (holder instanceof StreamHolder) {
            draweeView = ((StreamHolder) holder).draweeView;
            isPageMode = false;

        } else {
            // LoadingHolder 不会走到这里，但保留兜底
            return;
        }

        // 设置 Headers
        Headers currHeaders = imageUrl.getHeaders();
        ComicFrescoHeaders.setHeaders(currHeaders);

        if (currHeaders != null) {
            Context context = App.getAppContext();
            // 缓存 ImagePipelineFactory/Supplier 对，避免每次绑定都重建
            String cacheKey = imageUrl.isDownload() ? "" : currHeaders.toString();
            SupplierPair pair = mSupplierCache.get(cacheKey);
            if (pair == null) {
                ImagePipelineFactory normalFactory = ImagePipelineFactoryBuilder
                        .build(context, imageUrl.isDownload() ? null : currHeaders, false);
                ImagePipelineFactory largeFactory = ImagePipelineFactoryBuilder
                        .build(context, imageUrl.isDownload() ? null : currHeaders, true);
                pair = new SupplierPair(
                        ControllerBuilderSupplierFactory.get(context, normalFactory),
                        ControllerBuilderSupplierFactory.get(context, largeFactory)
                );
                // 限制缓存大小，防止内存泄漏
                if (mSupplierCache.size() > MAX_SUPPLIER_CACHE) {
                    mSupplierCache.clear();
                }
                mSupplierCache.put(cacheKey, pair);
            }
            setControllerSupplier(pair.normal, pair.large);
        }

        // 选择 ControllerBuilder
        PipelineDraweeControllerBuilder builder = isNeedResize(imageUrl)
                ? mLargeControllerSupplier.get()
                : mControllerSupplier.get();

        // 设置 ControllerListener
        if (isPageMode) {
            PhotoDraweeView photoView = (PhotoDraweeView) draweeView;
            builder.setControllerListener(new BaseControllerListener<ImageInfo>() {
                @Override
                public void onFinalImageSet(String id, ImageInfo imageInfo, Animatable animatable) {
                    if (imageInfo != null) {
                        imageUrl.setSuccess(true);
                        photoView.update(imageUrl.getId());
                    }
                }

                @Override
                public void onFailure(String id, Throwable throwable) {
                    imageUrl.setSuccess(false);
                    android.util.Log.e("ReaderAdapter",
                            "图片加载失败: " + id + " URL: " + imageUrl.getUrl(),
                            throwable);
                }
            });
        } else {
            builder.setControllerListener(new BaseControllerListener<ImageInfo>() {
                @Override
                public void onFinalImageSet(String id, ImageInfo imageInfo, Animatable animatable) {
                    if (imageInfo != null) {
                        imageUrl.setSuccess(true);

                        if (isVertical) {
                            draweeView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        } else {
                            draweeView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                        }

                        draweeView.setAspectRatio(
                                (float) imageInfo.getWidth() / imageInfo.getHeight()
                        );
                    }
                }

                @Override
                public void onFailure(String id, Throwable throwable) {
                    imageUrl.setSuccess(false);
                    android.util.Log.e("ReaderAdapter",
                            "图片加载失败: " + id + " URL: " + imageUrl.getUrl(),
                            throwable);
                }
            });
        }

        // 构建 ImageRequest 数组
        String[] urls = imageUrl.getUrls().toArray(new String[0]);
        ImageRequest[] requests = new ImageRequest[urls.length];

        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            if (url == null) continue;

            ImageRequestBuilder reqBuilder = ImageRequestBuilder
                    .newBuilderWithSource(Uri.parse(url))
                    .setProgressiveRenderingEnabled(true);

            MangaPostprocessor processor = new MangaPostprocessor(
                    imageUrl, isPaging, isPagingReverse, isWhiteEdge);
            reqBuilder.setPostprocessor(processor);

            if (!isCloseAutoResizeImage) {
                ResizeOptions options = isVertical
                        ? new ResizeOptions(App.mWidthPixels, App.mHeightPixels)
                        : new ResizeOptions(App.mHeightPixels, App.mWidthPixels);

                reqBuilder.setResizeOptions(options);
            }

            reqBuilder.setRequestListener(new BaseRequestListener() {
                @Override
                public void onRequestSuccess(@NonNull ImageRequest request,
                                             @NonNull String requestId,
                                             boolean isPrefetch) {
                    imageUrl.setUrl(url);
                }
            });

            requests[i] = reqBuilder.build();
        }

        // 绑定 Controller
        builder.setOldController(draweeView.getController())
                .setTapToRetryEnabled(true)
                .setRetainImageOnFailure(true);

        draweeView.setController(
                builder.setFirstAvailableImageRequests(requests).build()
        );
    }

    public void setControllerSupplier(PipelineDraweeControllerBuilderSupplier normal,
                                      PipelineDraweeControllerBuilderSupplier large) {
        mControllerSupplier = normal;
        mLargeControllerSupplier = large;
    }

    public void setTapGestureListener(OnTapGestureListener listener) {
        mTapGestureListener = listener;
    }

    public void setLazyLoadListener(OnLazyLoadListener listener) {
        mLazyLoadListener = listener;
    }

    public void setScaleFactor(float factor) {
        mScaleFactor = factor;
    }

    public void setDoubleTap(boolean enable) {
        isDoubleTap = enable;
    }

    public void setBanTurn(boolean block) {
        isBanTurn = block;
    }

    public void setVertical(boolean vertical) {
        isVertical = vertical;
    }

    public void setPaging(boolean paging) {
        isPaging = paging;
    }

    public void setPagingReverse(boolean pagingReverse) {
        isPagingReverse = pagingReverse;
    }

    public void setCloseAutoResizeImage(boolean closeAutoResizeImage) {
        isCloseAutoResizeImage = closeAutoResizeImage;
    }

    public void setWhiteEdge(boolean whiteEdge) {
        isWhiteEdge = whiteEdge;
    }

    public void setReaderMode(@ReaderMode int reader) {
        ReaderAdapter.reader = reader;
    }

    private boolean isNeedResize(ImageUrl imageUrl) {
        // 长图例如条漫不 resize
        return (imageUrl.getWidth() * 2) > imageUrl.getHeight() && imageUrl.getSize() > App.mLargePixels;
    }

    @Override
    public RecyclerView.ItemDecoration getItemDecoration() {
        switch (reader) {
            default:
            case READER_PAGE:
                return new RecyclerView.ItemDecoration() {
                    @Override
                    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                        outRect.set(0, 0, 0, 0);
                    }
                };
            case READER_STREAM:
                return new RecyclerView.ItemDecoration() {
                    @Override
                    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                        if (isVertical) {
                            outRect.set(0, 10, 0, 10);
                        } else {
                            outRect.set(10, 0, 10, 0);
                        }
                    }
                };
        }
    }

    /**
     * 假设一定找得到
     */
    public int getPositionByNum(int current, int num, boolean reverse) {
        try {
            while (mDataSet.get(current).getNum() < num) {
                current = reverse ? current - 1 : current + 2;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return current;
        }
    }

    public int getPositionById(Long id) {
        int size = mDataSet.size();
        for (int i = 0; i < size; ++i) {
            if (mDataSet.get(i).getId() == (id)) {
                return i;
            }
        }
        return -1;
    }

    public void update(Long id, String url) {
        for (int i = 0; i < mDataSet.size(); ++i) {
            ImageUrl imageUrl = mDataSet.get(i);
            if (imageUrl.getId() == (id) && imageUrl.isLoading()) {
                if (url == null) {
                    imageUrl.setLoading(false);
                    return;
                }
                imageUrl.setUrl(url);
                imageUrl.setLoading(false);
                imageUrl.setLazy(false);
                notifyItemChanged(i);
                break;
            }
        }
    }

    @IntDef({READER_PAGE, READER_STREAM})
    @Retention(RetentionPolicy.SOURCE)
    @interface ReaderMode {
    }

    public interface OnLazyLoadListener {
        void onLoad(ImageUrl imageUrl);
    }


    // 👇 Page模式（支持缩放）
    public static class PageHolder extends RecyclerView.ViewHolder {
        public PhotoDraweeView draweeView;

        public PageHolder(View itemView) {
            super(itemView);
            draweeView = itemView.findViewById(R.id.reader_image_view);
        }
    }

    // 👇 Stream模式（普通图）
    public static class StreamHolder extends RecyclerView.ViewHolder {
        public RetryDraweeView draweeView;

        public StreamHolder(View itemView) {
            super(itemView);
            draweeView = itemView.findViewById(R.id.reader_image_view);
        }
    }

    // 👇 Loading
    public static class LoadingHolder extends RecyclerView.ViewHolder {
        public LoadingHolder(View itemView) {
            super(itemView);
        }
    }
}
