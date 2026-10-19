package com.xyrlsz.xcimocob.ui.adapter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.common.internal.Supplier;
import com.facebook.drawee.backends.pipeline.PipelineDraweeControllerBuilder;
import com.facebook.drawee.backends.pipeline.PipelineDraweeControllerBuilderSupplier;
import com.facebook.drawee.view.SimpleDraweeView;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.ChapterBookmarkStore;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.ui.widget.ChapterButton;
import com.xyrlsz.xcimocob.utils.STConvertUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Created by Hiroshi on 2016/7/2.
 */
public class DetailAdapter extends BaseAdapter<Chapter> {

    public String title;
    public String intro;
    public String author;
    public String source;
    Paint textPaint;
    Paint paint;
    private Supplier<PipelineDraweeControllerBuilder> mControllerSupplier;
    private String cover;
    private String update;
    private Boolean finish;

    //用户手动调整顺序
    private Boolean Reversed = false;
    private String last;
    private List<String> tags;
    private OnTagClickListener mTagClickListener;
    private long mBookmarkComicId = -1L;
    private Set<String> mBookmarkedPaths = new java.util.HashSet<>();

    private static final int TAG_MAX_COUNT = 20;
    public interface OnTagClickListener {
        void onTagClick(String tag);
    }

    public DetailAdapter(Context context, List<Chapter> list) {
        super(context, list);
        textPaint = new TextPaint();
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setFakeBoldText(true);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(40);//文字大小
        textPaint.setColor(context.getResources().getColor(R.color.colorAutoG_GW));//背景颜色
        textPaint.setTextAlign(Paint.Align.LEFT);
        paint = new Paint();
        paint.setColor(ContextCompat.getColor(context, R.color.transparent));

    }

    @Override
    public RecyclerView.ItemDecoration getItemDecoration() {
        return new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildLayoutPosition(view);
                if (position == 0) {
                    outRect.set(0, 0, 0, 10);
                } else {
                    if (isFirst(position)) {
                        int offset = parent.getWidth() / 40;

                        outRect.set(offset, 50, offset, (int) (offset * 1.5));

                    } else {
                        int offset = parent.getWidth() / 40;
                        outRect.set(offset, 0, offset, (int) (offset * 1.5));
                    }


                }
            }

            @Override
            public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.onDraw(c, parent, state);
                if (parent.getAdapter() != null) {
//                    RecyclerView.Adapter adapter = parent.getAdapter();
                    int count = parent.getChildCount();
                    for (int i = 1; i < count; i++) {
                        View view = parent.getChildAt(i);
                        int position = parent.getChildLayoutPosition(view);
                        boolean isHeader = isFirst(position);

                        if (isHeader) {
                            float top = view.getTop() - 60;
                            float bottom = view.getTop() - 30;
                            Rect rect = new Rect(parent.getPaddingLeft(), (int) top, parent.getWidth() - parent.getPaddingRight(), (int) bottom);
                            c.drawRect(rect, paint);
                            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
                            float baseline = (rect.bottom + rect.top - fontMetrics.bottom - fontMetrics.top) / 2;
                            textPaint.setTextAlign(Paint.Align.CENTER);
                            c.drawText(getGroupName(position), rect.centerX(), baseline, textPaint);
                        }  //                            c.drawRect(0, view.getTop() - 1, parent.getWidth(), view.getTop(), mLinePaint);

                    }
                }
            }
        };
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? 0 : 1;
    }

    @Override
    public void reverse() {
        this.Reversed = !this.Reversed;
        super.reverse();
    }

    //判断是否分组且当前是否为第一个元素
    public boolean isFirst(int position) {
        position = position - 1;
        if (mDataSet.get(position).getSourceGroup().isEmpty()) {
            return false;
        }
        if (position == 0) {
            return true;
        }
        if (position <= 0) {
            return false;
        }
        return !mDataSet.get(position - 1).getSourceGroup().equals(mDataSet.get(position).getSourceGroup());
    }

    public String getGroupName(int position) {
//        return mDataSet.get(position - 1).getSourceGroup();
        return STConvertUtils.convert(mDataSet.get(position - 1).getSourceGroup());
    }

    @Override
    public int getItemCount() {
        return mDataSet.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) {
            View view = mInflater.inflate(R.layout.item_chapter_header, parent, false);
            return new HeaderHolder(view);
        }
        View view = mInflater.inflate(R.layout.item_chapter, parent, false);
        return new ChapterHolder(view);
    }

    public void setInfo(String cover, String title, String author, String intro, Boolean finish, String update, String last, String source) {
        this.cover = cover;
        this.title = title;
        this.intro = intro;
        this.finish = finish;
        this.update = update;
        this.author = author;
        this.last = last;
        this.source = source;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? null : new ArrayList<>(tags);
        notifyItemChanged(0);
    }

    public void setBookmarkComicId(long sourceComic) {
        mBookmarkComicId = sourceComic;
        mBookmarkedPaths = ChapterBookmarkStore.getBookmarks(mContext, sourceComic);
        notifyDataSetChanged();
    }

    public void setOnTagClickListener(OnTagClickListener listener) {
        mTagClickListener = listener;
    }


    public boolean isReversed() {
        return Reversed;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        try {
            if (position == 0) {
                HeaderHolder headerHolder = (HeaderHolder) holder;
                if (title != null) {
                    if (cover != null) {
                        headerHolder.mComicImage.setController(mControllerSupplier.get().setUri(cover).build());
                    }

//                    headerHolder.mComicTitle.setTextIsSelectable(true);
                    headerHolder.mComicTitle.setSelected(true);
                    headerHolder.mComicTitle.setText(STConvertUtils.convert(title));
//                    headerHolder.mComicIntro.setTextIsSelectable(true);
                    headerHolder.mComicIntro.setText(STConvertUtils.convert(intro));
//                    headerHolder.mComicAuthor.setTextIsSelectable(true);
                    headerHolder.mComicAuthor.setSelected(true);

                    if (finish != null) {
                        headerHolder.mComicStatus.setText(finish ? mContext.getString(R.string.comic_status_finish) : mContext.getString(R.string.comic_status_continue));
                    }
                    if (update != null && !update.isEmpty()) {
                        headerHolder.mComicUpdate.setText(mContext.getString(R.string.comic_last_update).concat(update));
                    }
                    headerHolder.mComicAuthor.setText(author);
                    headerHolder.mComicSource.setText(STConvertUtils.convert(source));
                    bindTags(headerHolder);
                }
            } else {
                Chapter chapter = mDataSet.get(position - 1);
                ChapterHolder viewHolder = (ChapterHolder) holder;
                viewHolder.chapterButton.setOnClickListener(v -> viewHolder.itemView.performClick());
                viewHolder.chapterButton.setText(STConvertUtils.convert(chapter.getTitle()));
                viewHolder.chapterButton.setDownload(chapter.isComplete());
                bindBookmark(viewHolder, chapter);
                if (chapter.getPath() != null && chapter.getPath().equals(last)) {
                    viewHolder.chapterButton.setSelected(true);
                } else if (viewHolder.chapterButton.isSelected()) {
                    viewHolder.chapterButton.setSelected(false);
                }
            }
        } catch (Exception ignored) {

        }
    }

    private void bindBookmark(ChapterHolder holder, Chapter chapter) {
        TextView bookmark = holder.bookmark;
        if (mBookmarkComicId == -1L) {
            bookmark.setVisibility(View.GONE);
            return;
        }
        bookmark.setVisibility(View.VISIBLE);
        boolean marked = mBookmarkedPaths.contains(chapter.getPath());
        bookmark.setText(marked ? "★" : "☆");
        bookmark.setAlpha(marked ? 1f : 0.55f);
        bookmark.setContentDescription(mContext.getString(
                marked ? R.string.chapter_bookmark_remove : R.string.chapter_bookmark_add));
        bookmark.setOnClickListener(v -> {
            ChapterBookmarkStore.setBookmarked(mContext, mBookmarkComicId, chapter.getPath(), !marked);
            mBookmarkedPaths = ChapterBookmarkStore.getBookmarks(mContext, mBookmarkComicId);
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(adapterPosition);
            }
        });
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        final GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
        Objects.requireNonNull(manager).setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position == 0 || isFirst(position)) {
                    return manager.getSpanCount();
                }

                return 1;
            }
        });
    }

    public void setControllerSupplier(Supplier<PipelineDraweeControllerBuilder> supplier) {
        this.mControllerSupplier = supplier;
    }

    public void setLast(String value) {
        if (value == null || value.equals(last)) {
            return;
        }
        String temp = last;
        last = value;
        for (int i = 0; i != mDataSet.size(); ++i) {
            String path = mDataSet.get(i).getPath();
            if (path.equals(last)) {
                notifyItemChanged(i + 1);
            } else if (path.equals(temp)) {
                notifyItemChanged(i + 1);
            }
        }
    }

    static class ChapterHolder extends BaseViewHolder {
        ChapterButton chapterButton;
        TextView bookmark;

        ChapterHolder(View view) {
            super(view);
            chapterButton = view.findViewById(R.id.item_chapter_button);
            bookmark = view.findViewById(R.id.item_chapter_bookmark);
        }
    }

    private int dp(float value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }

    /**
     * 动态构建标签芯片。标签来自图源解析，点击后跳转分类页按标签检索；
     * 直接 new TextView + setOnClickListener，避免字符串拼接 onclick 的注入与引号问题。
     */
    private void bindTags(HeaderHolder holder) {
        holder.mComicTags.removeAllViews();
        if (tags == null || tags.isEmpty()) {
            holder.mComicTagsScroll.setVisibility(View.GONE);
            return;
        }
        holder.mComicTagsScroll.setVisibility(View.VISIBLE);
        Context tagContext = holder.mComicTags.getContext();
        int count = Math.min(tags.size(), TAG_MAX_COUNT);
        for (int i = 0; i < count; i++) {
            final String tag = tags.get(i);
            if (tag == null || tag.trim().isEmpty()) {
                continue;
            }
            TextView tagView = new TextView(tagContext);
            tagView.setText(STConvertUtils.convert(tag));
            tagView.setTextColor(Color.WHITE);
            tagView.setTextSize(12);
            tagView.setSingleLine(true);
            tagView.setBackgroundResource(R.drawable.bg_detail_tag);
            int padH = dp(10);
            int padV = dp(4);
            tagView.setPadding(padH, padV, padH, padV);
            tagView.setClickable(true);
            tagView.setFocusable(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(6));
            tagView.setLayoutParams(params);
            if (mTagClickListener != null) {
                tagView.setOnClickListener(v -> mTagClickListener.onTagClick(tag));
            }
            holder.mComicTags.addView(tagView);
        }
    }

    class HeaderHolder extends BaseViewHolder {
        SimpleDraweeView mComicImage;
        TextView mComicTitle;
        TextView mComicIntro;
        TextView mComicStatus;
        TextView mComicUpdate;
        TextView mComicAuthor;
        TextView mComicSource;
        HorizontalScrollView mComicTagsScroll;
        LinearLayout mComicTags;

        HeaderHolder(View view) {
            super(view);
            mComicImage = view.findViewById(R.id.item_header_comic_image);
            mComicTitle = view.findViewById(R.id.item_header_comic_title);
            mComicIntro = view.findViewById(R.id.item_header_comic_intro);
            mComicStatus = view.findViewById(R.id.item_header_comic_status);
            mComicUpdate = view.findViewById(R.id.item_header_comic_update);
            mComicAuthor = view.findViewById(R.id.item_header_comic_author);
            mComicSource = view.findViewById(R.id.item_header_comic_source);
            mComicTagsScroll = view.findViewById(R.id.item_header_comic_tags_scroll);
            mComicTags = view.findViewById(R.id.item_header_comic_tags);
        }
    }

}
