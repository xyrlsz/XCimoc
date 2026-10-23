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
import java.util.Collection;
import java.util.Collections;
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
    private final List<Chapter> mAllChapters = new ArrayList<>();
    private boolean mShowBookmarkedOnly;
    private OnBookmarkNoteListener mBookmarkNoteListener;

    /**
     * header 项的偏移：1 = 封面简介作为列表第 0 项（单栏）；0 = 由外部视图显示（大屏双栏）
     */
    private int mHeaderOffset = 1;
    private View mExternalHeaderView;

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
                if (position < mHeaderOffset) {
                    outRect.set(0, 0, 0, 10);
                } else {
                    if (isFirst(position)) {
                        int offset = parent.getWidth() / 40;
                        // 双栏模式下列表里没有 header 项，首个分组标题需要自己留出空间，否则会被裁掉
                        int top = mHeaderOffset == 0 && position == 0 ? 70 : 50;
                        outRect.set(offset, top, offset, (int) (offset * 1.5));

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
                    for (int i = 0; i < count; i++) {
                        View view = parent.getChildAt(i);
                        int position = parent.getChildLayoutPosition(view);
                        if (position < mHeaderOffset) {
                            continue;
                        }
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
        return position < mHeaderOffset ? 0 : 1;
    }

    @Override
    public void reverse() {
        this.Reversed = !this.Reversed;
        Collections.reverse(mAllChapters);
        refreshChapterData();
    }

    //判断是否分组且当前是否为第一个元素
    public boolean isFirst(int position) {
        int index = position - mHeaderOffset;
        if (index < 0 || index >= mDataSet.size()) {
            return false;
        }
        if (mDataSet.get(index).getSourceGroup().isEmpty()) {
            return false;
        }
        if (index == 0) {
            return true;
        }
        return !mDataSet.get(index - 1).getSourceGroup().equals(mDataSet.get(index).getSourceGroup());
    }

    public String getGroupName(int position) {
        return STConvertUtils.convert(mDataSet.get(position - mHeaderOffset).getSourceGroup());
    }

    @Override
    public int getItemCount() {
        return mDataSet.size() + mHeaderOffset;
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
        refreshHeader();
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? null : new ArrayList<>(tags);
        refreshHeader();
    }

    public void setBookmarkComicId(long sourceComic) {
        mBookmarkComicId = sourceComic;
        mBookmarkedPaths = ChapterBookmarkStore.getBookmarks(mContext, sourceComic);
        refreshChapterData();
    }

    public void setBookmarkedOnly(boolean bookmarkedOnly) {
        mShowBookmarkedOnly = bookmarkedOnly;
        refreshChapterData();
    }

    public boolean isBookmarkedOnly() {
        return mShowBookmarkedOnly;
    }

    public void setOnBookmarkNoteListener(OnBookmarkNoteListener listener) {
        mBookmarkNoteListener = listener;
    }

    public void refreshBookmarks() {
        if (mBookmarkComicId != -1L) {
            mBookmarkedPaths = ChapterBookmarkStore.getBookmarks(mContext, mBookmarkComicId);
            refreshChapterData();
        }
    }

    @Override
    public void setData(Collection<Chapter> chapters) {
        mAllChapters.clear();
        mAllChapters.addAll(chapters);
        refreshChapterData();
    }

    private void refreshChapterData() {
        List<Chapter> visible = new ArrayList<>();
        for (Chapter chapter : mAllChapters) {
            if (!mShowBookmarkedOnly || mBookmarkedPaths.contains(chapter.getPath())) {
                visible.add(chapter);
            }
        }
        super.setData(visible);
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
            if (position < mHeaderOffset) {
                bindHeader((HeaderHolder) holder);
            } else {
                Chapter chapter = mDataSet.get(position - mHeaderOffset);
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

    private void bindHeader(HeaderHolder headerHolder) {
        if (title == null) {
            return;
        }
        if (cover != null && mControllerSupplier != null) {
            headerHolder.mComicImage.setController(mControllerSupplier.get().setUri(cover).build());
        }
        headerHolder.mComicTitle.setSelected(true);
        headerHolder.mComicTitle.setText(STConvertUtils.convert(title));
        headerHolder.mComicIntro.setText(STConvertUtils.convert(intro));
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

    /**
     * 把封面简介换成列表外的视图显示（大屏双栏），传 null 恢复为列表第 0 项
     */
    public void setExternalHeaderView(View view) {
        mExternalHeaderView = view;
        int offset = view == null ? 1 : 0;
        if (offset != mHeaderOffset) {
            mHeaderOffset = offset;
            notifyDataSetChanged();
        }
        refreshHeader();
    }

    /**
     * 列表当前把封面简介算作第几项：1 = 在列表内，0 = 已移到列表外
     */
    public int getHeaderOffset() {
        return mHeaderOffset;
    }

    public Chapter getChapterAt(int adapterPosition) {
        int index = adapterPosition - mHeaderOffset;
        if (index < 0 || index >= mDataSet.size()) {
            return null;
        }
        return mDataSet.get(index);
    }

    private void refreshHeader() {
        if (mExternalHeaderView != null) {
            bindHeader(new HeaderHolder(mExternalHeaderView));
        } else if (mHeaderOffset > 0) {
            notifyItemChanged(0);
        }
    }

    private void bindBookmark(ChapterHolder holder, Chapter chapter) {
        TextView bookmark = holder.bookmark;
        if (mBookmarkComicId == -1L || !ChapterBookmarkStore.isEnabled()) {
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
            refreshChapterData();
        });
        bookmark.setOnLongClickListener(v -> {
            if (mBookmarkNoteListener != null) {
                mBookmarkNoteListener.onEditBookmarkNote(chapter);
                return true;
            }
            return false;
        });
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        final GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
        Objects.requireNonNull(manager).setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position < mHeaderOffset || isFirst(position)) {
                    return manager.getSpanCount();
                }

                return 1;
            }
        });
    }

    public void setControllerSupplier(Supplier<PipelineDraweeControllerBuilder> supplier) {
        this.mControllerSupplier = supplier;
        refreshHeader();
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
                notifyItemChanged(i + mHeaderOffset);
            } else if (path.equals(temp)) {
                notifyItemChanged(i + mHeaderOffset);
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

    public interface OnBookmarkNoteListener {
        void onEditBookmarkNote(Chapter chapter);
    }

}
