package com.xyrlsz.xcimocob.ui.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.utils.FrescoUtils;
import com.xyrlsz.xcimocob.utils.STConvertUtils;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Hiroshi on 2016/7/1.
 */
public class GridAdapter extends BaseAdapter<Object> {

    public static final int TYPE_GRID = 2016101213;

    private ControllerBuilderProvider mProvider;
    private SourceManager.TitleGetter mTitleGetter;
    private boolean symbol = false;

    public GridAdapter(Context context, List<Object> list) {
        super(context, list);
    }

    @Override
    public int getItemViewType(int position) {
        return TYPE_GRID;
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.item_grid, parent, false);
        GridHolder holder = new GridHolder(view);
        // 使用 OnPreDrawListener（仅执行一次）替代 OnDrawListener（每帧触发）
        holder.rlItemGrid.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!holder.mHasMeasured) {
                    int width = holder.rlItemGrid.getWidth();
                    if (width > 0) {
                        ViewGroup.LayoutParams params = holder.rlItemGrid.getLayoutParams();
                        params.height = (int) (width * (4 / 3.0));
                        holder.rlItemGrid.setLayoutParams(params);
                        holder.mHasMeasured = true;
                    }
                }
                return true;
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        int viewType = getItemViewType(position);
        switch (viewType) {
            case TYPE_GRID:
            default:
                MiniComic comic = (MiniComic) mDataSet.get(position);
                GridHolder gridHolder = (GridHolder) holder;
                gridHolder.comicTitle.setText(STConvertUtils.convert(comic.getTitle()));
                gridHolder.comicSource.setText(STConvertUtils.convert(mTitleGetter.getTitle(comic.getSource())));
                if (mProvider != null) {
                    ImageRequest request = buildImageRequest(comic);
                    DraweeController controller = mProvider.get(comic.getSource())
                            .setOldController(gridHolder.comicImage.getController())
                            .setImageRequest(request)
                            .build();
                    gridHolder.comicImage.setController(controller);
                }
                gridHolder.comicHighlight.setVisibility(symbol && comic.isHighlight() ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /**
     * 构建封面图片请求，复用逻辑消除重复代码
     */
    @Nullable
    private ImageRequest buildImageRequest(MiniComic comic) {
        boolean onlyWifi = App.getPreferenceManager().getBoolean(PreferenceManager.PREF_OTHER_CONNECT_ONLY_WIFI, false);
        boolean onlyWifiCover = App.getPreferenceManager().getBoolean(PreferenceManager.PREF_OTHER_LOADCOVER_ONLY_WIFI, false);
        boolean wifiEnabled = App.getManager_wifi().isWifiEnabled();

        // 仅 WiFi 模式下非 WiFi 时，仅使用缓存
        if ((!wifiEnabled && onlyWifi) || (!wifiEnabled && onlyWifiCover)) {
            if (FrescoUtils.isCached(comic.getCover())) {
                return ImageRequestBuilder
                        .newBuilderWithSource(Uri.fromFile(FrescoUtils.getFileFromDiskCache(comic.getCover())))
                        .setResizeOptions(new ResizeOptions(App.mCoverWidthPixels / 3, App.mCoverHeightPixels / 3))
                        .build();
            }
            return null;
        }

        // 正常模式：缓存存在则用本地文件，否则用网络 URL
        try {
            Uri sourceUri;
            if (FrescoUtils.isCached(comic.getCover())) {
                sourceUri = Uri.fromFile(FrescoUtils.getFileFromDiskCache(comic.getCover()));
            } else {
                sourceUri = Uri.parse(comic.getCover());
            }
            return ImageRequestBuilder
                    .newBuilderWithSource(sourceUri)
                    .setResizeOptions(new ResizeOptions(App.mCoverWidthPixels / 3, App.mCoverHeightPixels / 3))
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setProvider(ControllerBuilderProvider provider) {
        mProvider = provider;
    }

    public void setTitleGetter(SourceManager.TitleGetter getter) {
        mTitleGetter = getter;
    }

    public void setSymbol(boolean symbol) {
        this.symbol = symbol;
    }

    @Override
    public RecyclerView.ItemDecoration getItemDecoration() {
        return new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NotNull Rect outRect, @NotNull View view,
                                       @NotNull RecyclerView parent, @NotNull RecyclerView.State state) {
                int offset = parent.getWidth() / 90;
                outRect.set(offset, 0, offset, (int) (2.8 * offset));
            }
        };
    }

    public void removeItemById(long id) {
        for (Object O_comic : mDataSet) {
            MiniComic comic = (MiniComic) O_comic;
            if (id == comic.getId()) {
                remove(comic);
                break;
            }
        }
    }

    public int findFirstNotHighlight() {
        int count = 0;
        if (symbol) {
            for (Object O_comic : mDataSet) {
                MiniComic comic = (MiniComic) O_comic;
                if (!comic.isHighlight()) {
                    break;
                }
                ++count;
            }
        }
        return count;
    }

    public void cancelAllHighlight() {
        int count = 0;
        for (Object O_comic : mDataSet) {
            MiniComic comic = (MiniComic) O_comic;
            if (!comic.isHighlight()) {
                break;
            }
            ++count;
            comic.setHighlight(false);
        }
        notifyItemRangeChanged(0, count);
    }

    public void moveItemTop(MiniComic comic) {
        if (remove(comic)) {
            add(findFirstNotHighlight(), comic);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void filterByKeyword(String keyword) {
        List<Object> temp = new ArrayList<>();
        if (mOriginalData.isEmpty()) {
            mOriginalData.addAll(mDataSet);
        } else if (mDataSet.isEmpty()) {
            mDataSet.addAll(mOriginalData);
        }
        for (Object O_comic : mDataSet) {
            MiniComic comic = (MiniComic) O_comic;
            String title = STConvertUtils.T2S(comic.getTitle()).toUpperCase();
            if (title.contains(STConvertUtils.T2S(keyword.toUpperCase()))) {
                temp.add(comic);
            }
        }
        mDataSet.clear();
        mDataSet.addAll(temp);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void filterByKeyword(String keyword, boolean isCompleted, boolean isNotCompleted) {
        List<Object> temp = new ArrayList<>();
        if (mOriginalData.isEmpty()) {
            mOriginalData.addAll(mDataSet);
        } else if (mDataSet.isEmpty()) {
            mDataSet.addAll(mOriginalData);
        }
        for (Object O_comic : mDataSet) {
            MiniComic comic = (MiniComic) O_comic;
            String title = STConvertUtils.T2S(comic.getTitle()).toUpperCase();
            if (title.contains(STConvertUtils.T2S(keyword.toUpperCase())) || keyword.isEmpty()) {
                if (isCompleted && comic.isFinish()) {
                    temp.add(comic);
                } else if (isNotCompleted && !comic.isFinish()) {
                    temp.add(comic);
                }
            }
        }
        mDataSet.clear();
        mDataSet.addAll(temp);
        notifyDataSetChanged();
    }

    public void cancelFilter() {
        if (mOriginalData == null || mOriginalData.isEmpty()) {
            return; // 如果 original 为 null 或空，直接返回，不修改 mDataSet
        }

        if (mDataSet == null) {
            mDataSet = new ArrayList<>(); // 防止 mDataSet 为 null（可选）
        }

        mDataSet.clear();          // 清空旧数据
        mDataSet.addAll(mOriginalData); // 添加新数据
        notifyDataSetChanged();    // 通知 Adapter 刷新
    }

    static class GridHolder extends BaseViewHolder {
        SimpleDraweeView comicImage;
        TextView comicTitle;
        TextView comicSource;
        View comicHighlight;
        RelativeLayout rlItemGrid;
        boolean mHasMeasured = false; // 防止 OnPreDrawListener 重复计算布局

        GridHolder(View view) {
            super(view);
            comicImage = view.findViewById(R.id.item_grid_image);
            comicTitle = view.findViewById(R.id.item_grid_title);
            comicSource = view.findViewById(R.id.item_grid_subtitle);
            comicHighlight = view.findViewById(R.id.item_grid_symbol);
            rlItemGrid = view.findViewById(R.id.rl_item_grid);
        }
    }
}
