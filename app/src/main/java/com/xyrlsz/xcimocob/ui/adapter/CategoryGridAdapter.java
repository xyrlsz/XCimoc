package com.xyrlsz.xcimocob.ui.adapter;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.fresco.ControllerBuilderProvider;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.CategoryMiniComic;
import com.xyrlsz.xcimocob.utils.FrescoUtils;
import com.xyrlsz.xcimocob.utils.STConvertUtils;

import java.util.List;

public class CategoryGridAdapter extends RecyclerView.Adapter<CategoryGridAdapter.ViewHolder> {

    private final Context mContext;
    private final List<CategoryMiniComic> mList;
    private OnComicClickListener mListener;
    private ControllerBuilderProvider mProvider;

    public CategoryGridAdapter(Context context, List<CategoryMiniComic> list) {
        this.mContext = context;
        this.mList = list;
    }

    public void setProvider(ControllerBuilderProvider provider) {
        mProvider = provider;
    }

    public void setOnComicClickListener(OnComicClickListener l) {
        this.mListener = l;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext)
                .inflate(R.layout.item_category_comic, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryMiniComic comic = mList.get(position);

        // 封面
//        Uri uri = Uri.parse(comic.getCover());
//        holder.cover.setImageURI(uri);
        if (mProvider != null) {
            ImageRequest request = null;
            try {
                if (!App.getManager_wifi().isWifiEnabled() && App.getPreferenceManager().getBoolean(PreferenceManager.PREF_OTHER_CONNECT_ONLY_WIFI, false)) {
                    //                    request = null;
                    if (FrescoUtils.isCached(comic.getCover())) {
                        request = ImageRequestBuilder
                                .newBuilderWithSource(Uri.fromFile(FrescoUtils.getFileFromDiskCache(comic.getCover())))
                                .setResizeOptions(new ResizeOptions(App.mCoverWidthPixels / 3, App.mCoverHeightPixels / 3))
                                .build();
                    }
                } else if (!App.getManager_wifi().isWifiEnabled() && App.getPreferenceManager().getBoolean(PreferenceManager.PREF_OTHER_LOADCOVER_ONLY_WIFI, false)) {
                    //                    request = null;
                    if (FrescoUtils.isCached(comic.getCover())) {
                        request = ImageRequestBuilder
                                .newBuilderWithSource(Uri.fromFile(FrescoUtils.getFileFromDiskCache(comic.getCover())))
                                .setResizeOptions(new ResizeOptions(App.mCoverWidthPixels / 3, App.mCoverHeightPixels / 3))
                                .build();
                    }
                } else {
                    if (FrescoUtils.isCached(comic.getCover())) {
                        request = ImageRequestBuilder
                                .newBuilderWithSource(Uri.fromFile(FrescoUtils.getFileFromDiskCache(comic.getCover())))
                                .setResizeOptions(new ResizeOptions(App.mCoverWidthPixels / 3, App.mCoverHeightPixels / 3))
                                .build();
                    } else {
                        request = ImageRequestBuilder
                                .newBuilderWithSource(Uri.parse(comic.getCover()))
                                .setResizeOptions(new ResizeOptions(App.mCoverWidthPixels / 3, App.mCoverHeightPixels / 3))
                                .build();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            DraweeController controller = mProvider.get(comic.getSource())
                    .setOldController(holder.cover.getController())
                    .setImageRequest(request)
                    .build();
            holder.cover.setController(controller);
        } else {
            Uri uri = Uri.parse(comic.getCover());
            holder.cover.setImageURI(uri);
        }
        // 标题（颜色由布局中的 textColorPrimary 随明暗模式自动解析）
        holder.title.setText(STConvertUtils.convert(comic.getTitle()));
        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onComicClick(comic);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    public interface OnComicClickListener {

        void onComicClick(CategoryMiniComic comic);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        SimpleDraweeView cover;
        TextView title;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.item_cover);
            title = itemView.findViewById(R.id.item_title);

        }
    }
}
