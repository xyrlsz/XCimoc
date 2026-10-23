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
import java.util.Collection;
import java.util.LinkedHashMap;
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
    private static final int TYPE_IMAGE_PAGE_DUAL = 2;
    private static @ReaderMode int reader;
    private PipelineDraweeControllerBuilderSupplier mControllerSupplier;
    private PipelineDraweeControllerBuilderSupplier mLargeControllerSupplier;
    private OnTapGestureListener mTapGestureListener;
    private OnLazyLoadListener mLazyLoadListener;
    private boolean isVertical; // 开页方向
    private boolean isDualPage; // 大横屏左右双页
    private boolean isDualPageRtl; // 双页从右往左排列
    private boolean isDualPageNoGap; // 双页左右两图紧贴中间
    private boolean isPaging;
    private boolean isPagingReverse;
    private boolean isWhiteEdge;
    private boolean isBanTurn;
    private boolean isDoubleTap;
    private boolean isCloseAutoResizeImage;
    private float mScaleFactor;
    private int mViewWidth;
    private int mViewHeight;

    // 缓存 ImagePipelineFactory/Supplier 对（按 headers 缓存），减少重复创建开销
    private static final int MAX_SUPPLIER_CACHE = 32;
    private final LinkedHashMap<String, SupplierPair> mSupplierCache =
            new LinkedHashMap<String, SupplierPair>(MAX_SUPPLIER_CACHE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Entry<String, SupplierPair> eldest) {
                    return size() > MAX_SUPPLIER_CACHE;
                }
            };

    /** 预计算的 ResizeOptions，减少重复创建 */
    private ResizeOptions mCachedVerticalResize;
    private ResizeOptions mCachedHorizontalResize;

    /** 页面模式图片加载成功监听器（复用实例） */
    private final BaseControllerListener<ImageInfo> mPageSuccessListener =
            new BaseControllerListener<ImageInfo>() {
                @Override
                public void onFinalImageSet(String id, ImageInfo imageInfo, Animatable animatable) {
                    // onBindViewHolder 会通过 tag 传递 ImageUrl
                }

                @Override
                public void onFailure(String id, Throwable throwable) {
                    // onBindViewHolder 会通过 tag 传递 ImageUrl
                }
            };

    /** 流模式图片加载成功监听器（复用实例） */
    private final BaseControllerListener<ImageInfo> mStreamSuccessListener =
            new BaseControllerListener<ImageInfo>() {
                @Override
                public void onFinalImageSet(String id, ImageInfo imageInfo, Animatable animatable) {
                    // onBindViewHolder 会通过 tag 传递 ImageUrl
                }

                @Override
                public void onFailure(String id, Throwable throwable) {
                    // onBindViewHolder 会通过 tag 传递 ImageUrl
                }
            };

    /** 请求成功监听器（复用实例） */
    private final BaseRequestListener mSharedRequestListener = new BaseRequestListener() {
        @Override
        public void onRequestSuccess(@NonNull ImageRequest request,
                                     @NonNull String requestId,
                                     boolean isPrefetch) {
            // URL 会在构建时设置到 ImageRequest 的 tag，或者通过 ImageUrl 获取
        }
    };

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
        if (isDualPage) {
            int dataPosition = toDataPosition(position);
            // 同一屏的第二张图如果还在懒加载，整屏先显示加载占位
            if (isLazy(dataPosition) || isLazy(dataPosition + 1)) {
                return TYPE_LOADING;
            }
            return TYPE_IMAGE_PAGE_DUAL;
        }

        if (mDataSet.get(position).isLazy()) return TYPE_LOADING;

        return reader == READER_PAGE ? TYPE_IMAGE_PAGE : TYPE_IMAGE_STREAM;
    }

    private boolean isLazy(int dataPosition) {
        return dataPosition >= 0 && dataPosition < mDataSet.size() && mDataSet.get(dataPosition).isLazy();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean isWhiteBackground = App.getPreferenceManager()
                .getBoolean(PreferenceManager.PREF_READER_WHITE_BACKGROUND, true);

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

        if (viewType == TYPE_IMAGE_PAGE_DUAL) {
            resId = isWhiteBackground ? R.layout.item_picture_dual_black : R.layout.item_picture_dual;
            View view = mInflater.inflate(resId, parent, false);
            return new DualPageHolder(view);
        }

        // TYPE_IMAGE_STREAM
        resId = isWhiteBackground ? R.layout.item_picture_stream_black : R.layout.item_picture_stream;
        View view = mInflater.inflate(resId, parent, false);
        return new StreamHolder(view); // 👈 用 RetryDraweeView
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DualPageHolder) {
            bindDualPage((DualPageHolder) holder, position);
            return;
        }

        // 处理懒加载占位
        if (holder instanceof LoadingHolder) {
            int dataPosition = toDataPosition(position);
            requestLazyLoad(dataPosition);
            if (isDualPage) {
                requestLazyLoad(dataPosition + 1);
            }
            return;
        }

        if (isDualPage) {
            return;
        }

        final ImageUrl imageUrl = mDataSet.get(position);
        if (holder instanceof PageHolder) {
            PageHolder pageHolder = (PageHolder) holder;
            // Page 模式特有配置（仅在首次绑定时设置）
            if (!pageHolder.mConfigured) {
                pageHolder.mConfigured = true;
                configurePageView(pageHolder.draweeView);
            }
            bindImage(pageHolder.draweeView, imageUrl, true);
        } else if (holder instanceof StreamHolder) {
            bindImage(((StreamHolder) holder).draweeView, imageUrl, false);
        }
    }

    private void requestLazyLoad(int dataPosition) {
        if (dataPosition < 0 || dataPosition >= mDataSet.size()) {
            return;
        }
        ImageUrl imageUrl = mDataSet.get(dataPosition);
        if (imageUrl.isLazy() && !imageUrl.isLoading() && mLazyLoadListener != null) {
            imageUrl.setLoading(true);
            mLazyLoadListener.onLoad(imageUrl);
        }
    }

    private void configurePageView(PhotoDraweeView photoView) {
        photoView.setTapListenerListener(mTapGestureListener);
        photoView.setAlwaysBlockParent(isBanTurn);
        photoView.setDoubleTap(isDoubleTap);
        photoView.setScaleFactor(mScaleFactor);
        photoView.setScrollMode(isVertical ?
                PhotoDraweeView.MODE_VERTICAL :
                PhotoDraweeView.MODE_HORIZONTAL);
    }

    /**
     * 绑定一屏双页：按排列顺序开关决定哪张图在左、哪张图在右。
     * 一屏按数据顺序固定合并相邻两张图，奇数页时最后一屏按顺序留空一侧。
     */
    private void bindDualPage(@NonNull DualPageHolder holder, int position) {
        int firstPosition = toDataPosition(position);
        ImageUrl firstImage = mDataSet.get(firstPosition);
        ImageUrl secondImage = firstPosition + 1 < mDataSet.size()
                ? mDataSet.get(firstPosition + 1) : null;

        requestLazyLoad(firstPosition);
        if (secondImage != null) {
            requestLazyLoad(firstPosition + 1);
        }

        if (!holder.mConfigured) {
            holder.mConfigured = true;
            configurePageView(holder.leftView);
            configurePageView(holder.rightView);
        }
        // 无间隔时两侧图片各自贴向中线，否则居中显示
        holder.leftView.setHorizontalAlign(isDualPageNoGap ?
                PhotoDraweeView.ALIGN_RIGHT : PhotoDraweeView.ALIGN_CENTER);
        holder.rightView.setHorizontalAlign(isDualPageNoGap ?
                PhotoDraweeView.ALIGN_LEFT : PhotoDraweeView.ALIGN_CENTER);

        PhotoDraweeView firstView = isDualPageRtl ? holder.rightView : holder.leftView;
        PhotoDraweeView secondView = isDualPageRtl ? holder.leftView : holder.rightView;
        bindImage(firstView, firstImage, true);
        if (secondImage != null) {
            bindImage(secondView, secondImage, true);
        } else {
            secondView.setTag(null);
            secondView.setController(null);
        }
    }

    private void bindImage(@NonNull DraweeView draweeView, @NonNull ImageUrl imageUrl, final boolean isPageMode) {
        // 设置 Headers
        Headers currHeaders = imageUrl.getHeaders();

        if (currHeaders != null) {
            Context context = App.getAppContext();
            // 缓存 ImagePipelineFactory/Supplier 对（LRU 淘汰）
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
                mSupplierCache.put(cacheKey, pair);
            }
            mControllerSupplier = pair.normal;
            mLargeControllerSupplier = pair.large;
        }

        // 选择 ControllerBuilder
        final boolean needResize = isNeedResize(imageUrl);
        PipelineDraweeControllerBuilder builder = needResize
                ? mLargeControllerSupplier.get()
                : mControllerSupplier.get();

        // 构建 ImageRequest 数组（使用预计算的 ResizeOptions）
        String[] urls = imageUrl.getUrls().toArray(new String[0]);
        ImageRequest[] requests = new ImageRequest[urls.length];

        // 预计算 ResizeOptions（仅首次或方向改变时）
        if (mCachedVerticalResize == null) {
            int width = mViewWidth > 0 ? mViewWidth : App.mWidthPixels;
            int height = mViewHeight > 0 ? mViewHeight : App.mHeightPixels;
            // 双页时每页只占半屏宽，按半屏宽解码以节省内存
            int pageWidth = isDualPage ? width / 2 : width;
            mCachedVerticalResize = new ResizeOptions(pageWidth, height);
            mCachedHorizontalResize = new ResizeOptions(height, pageWidth);
        }
        final ResizeOptions resizeOptions = isVertical
                ? mCachedVerticalResize : mCachedHorizontalResize;

        // 预创建 MangaPostprocessor（所有 URL 共享同一个处理器）
        final MangaPostprocessor sharedProcessor = new MangaPostprocessor(
                imageUrl, isPaging && !isDualPage, isPagingReverse, isWhiteEdge);

        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            if (url == null) continue;

            ImageRequestBuilder reqBuilder = ImageRequestBuilder
                    .newBuilderWithSource(Uri.parse(url))
                    .setProgressiveRenderingEnabled(true);

            // 使用共享的 postprocessor（状态由 ImageUrl 管理）
            reqBuilder.setPostprocessor(sharedProcessor);

            if (!isCloseAutoResizeImage) {
                reqBuilder.setResizeOptions(resizeOptions);
            }

            // 使用共享的 RequestListener（URL 更新通过 ImageUrl 完成）
            reqBuilder.setRequestListener(mSharedRequestListener);

            requests[i] = reqBuilder.build();
        }

        // 绑定 Controller（使用 ControllerListener 统一处理成功/失败）
        final int[] consumed = {0};
        final long imageId = imageUrl.getId();

        if (isPageMode) {
            final PhotoDraweeView photoView = (PhotoDraweeView) draweeView;
            builder.setControllerListener(new BaseControllerListener<ImageInfo>() {
                @Override
                public void onFinalImageSet(String id, ImageInfo imageInfo, Animatable animatable) {
                    if (imageInfo != null) {
                        imageUrl.setSuccess(true);
                        photoView.update(imageId);
                    }
                }

                @Override
                public void onFailure(String id, Throwable throwable) {
                    imageUrl.setSuccess(false);
                    if (consumed[0] == 0) {
                        consumed[0] = 1;
                        android.util.Log.e("ReaderAdapter",
                                "图片加载失败: " + imageUrl.getUrl(), throwable);
                    }
                }
            });
        } else {
            final DraweeView finalDraweeView = draweeView;
            builder.setControllerListener(new BaseControllerListener<ImageInfo>() {
                @Override
                public void onFinalImageSet(String id, ImageInfo imageInfo, Animatable animatable) {
                    if (imageInfo != null) {
                        imageUrl.setSuccess(true);

                        if (isVertical) {
                            finalDraweeView.getLayoutParams().height =
                                    ViewGroup.LayoutParams.WRAP_CONTENT;
                        } else {
                            finalDraweeView.getLayoutParams().width =
                                    ViewGroup.LayoutParams.WRAP_CONTENT;
                        }

                        finalDraweeView.setAspectRatio(
                                (float) imageInfo.getWidth() / imageInfo.getHeight()
                        );
                    }
                }

                @Override
                public void onFailure(String id, Throwable throwable) {
                    imageUrl.setSuccess(false);
                    if (consumed[0] == 0) {
                        consumed[0] = 1;
                        android.util.Log.e("ReaderAdapter",
                                "图片加载失败: " + imageUrl.getUrl(), throwable);
                    }
                }
            });
        }

        builder.setOldController(draweeView.getController())
                .setTapToRetryEnabled(true)
                .setRetainImageOnFailure(true);

        if (currHeaders != null) {
            builder.setCallerContext(currHeaders);
        }

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

    /**
     * 双页排列顺序：true 为从右往左（第一张图在右），false 为从左往右
     */
    public void setDualPageRtl(boolean rtl) {
        isDualPageRtl = rtl;
    }

    /**
     * 双页无间隔：左右两张图分别贴向中线，适合跨页长图
     */
    public void setDualPageNoGap(boolean noGap) {
        isDualPageNoGap = noGap;
    }

    /**
     * 大横屏左右双页：把相邻的两张图合并到同一个列表项中。
     * 开启后列表项位置与数据位置不再一致，需通过 {@link #toDataPosition}/{@link #toAdapterPosition} 转换。
     */
    public void setDualPage(boolean dualPage) {
        if (isDualPage == dualPage) {
            return;
        }
        isDualPage = dualPage;
        mCachedVerticalResize = null;
        mCachedHorizontalResize = null;
    }

    public boolean isDualPage() {
        return isDualPage;
    }

    /**
     * 列表项位置 → 数据位置
     */
    public int toDataPosition(int position) {
        return isDualPage && position >= 0 ? position * 2 : position;
    }

    /**
     * 数据位置 → 列表项位置
     */
    public int toAdapterPosition(int position) {
        return isDualPage && position >= 0 ? position / 2 : position;
    }

    /**
     * 阅读区域尺寸（px），用于按实际屏幕尺寸解码图片
     */
    public void setViewSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width == mViewWidth && height == mViewHeight) {
            return;
        }
        mViewWidth = width;
        mViewHeight = height;
        mCachedVerticalResize = null;
        mCachedHorizontalResize = null;
    }

    @Override
    public int getItemCount() {
        int size = mDataSet.size();
        return isDualPage ? (size + 1) / 2 : size;
    }

    @Override
    public ImageUrl getItem(int position) {
        int dataPosition = toDataPosition(position);
        dataPosition = Math.max(0, Math.min(dataPosition, mDataSet.size() - 1));
        return mDataSet.get(dataPosition);
    }

    /**
     * 双页模式下数据位置与列表项位置不一致，增量通知的下标会错位，需要单独处理
     */
    @Override
    public void add(int location, ImageUrl data) {
        if (!isDualPage) {
            super.add(location, data);
            return;
        }
        mDataSet.add(location, data);
        mOriginalData.add(location, data);
        notifyDataSetChanged();
    }

    @Override
    public void addAll(int location, Collection<ImageUrl> collection) {
        if (!isDualPage) {
            super.addAll(location, collection);
            return;
        }
        if (collection == null || collection.isEmpty()) {
            return;
        }
        int oldSize = mDataSet.size();
        int oldSpreadCount = getItemCount();
        mDataSet.addAll(location, collection);
        mOriginalData.addAll(location, collection);
        if (location >= oldSize) {
            // 追加不影响已有列表项的位置，只需刷新被补全的最后一屏并插入新屏
            if (oldSize % 2 == 1) {
                notifyItemChanged(oldSpreadCount - 1);
            }
            notifyItemRangeInserted(oldSpreadCount, getItemCount() - oldSpreadCount);
        } else {
            notifyDataSetChanged();
        }
    }

    @Override
    public void remove(int position) {
        if (!isDualPage) {
            super.remove(position);
            return;
        }
        mDataSet.remove(position);
        mOriginalData.remove(position);
        notifyDataSetChanged();
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
     * 假设一定找得到。
     * current 为数据位置，返回列表项位置（双页模式下两者不同）。
     */
    public int getPositionByNum(int current, int num, boolean reverse) {
        int position = current;
        try {
            while (mDataSet.get(position).getNum() < num) {
                position = reverse ? position - 1 : position + 2;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return toAdapterPosition(position);
        }
    }

    /**
     * 返回数据位置
     */
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
                notifyItemChanged(toAdapterPosition(i));
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
        /** 是否已完成首次配置（避免重复设置） */
        boolean mConfigured = false;

        public PageHolder(View itemView) {
            super(itemView);
            draweeView = itemView.findViewById(R.id.reader_image_view);
        }
    }

    // 👇 Page模式（大横屏左右双页）
    public static class DualPageHolder extends RecyclerView.ViewHolder {
        public PhotoDraweeView leftView;
        public PhotoDraweeView rightView;
        /** 是否已完成首次配置（避免重复设置） */
        boolean mConfigured = false;

        public DualPageHolder(View itemView) {
            super(itemView);
            leftView = itemView.findViewById(R.id.reader_image_view_left);
            rightView = itemView.findViewById(R.id.reader_image_view_right);
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
