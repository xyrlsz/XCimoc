package com.xyrlsz.xcimocob.ui.adapter;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.misc.Switcher;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.ui.widget.ChapterButton;
import com.xyrlsz.xcimocob.utils.STConvertUtils;

import java.util.List;
import java.util.Set;

/**
 * Created by Hiroshi on 2016/11/15.
 */

public class ChapterAdapter extends BaseAdapter<Switcher<Chapter>> {

    private static final int TYPE_ITEM = 2017030222;
    private static final int TYPE_BUTTON = 2017030223;

    private boolean isButtonMode = false;
    private Set<String> bookmarks;
    private OnBookmarkClickListener bookmarkClickListener;

    public ChapterAdapter(Context context, List<Switcher<Chapter>> list) {
        super(context, list);
    }

    @Override
    public int getItemViewType(int position) {
        return isButtonMode ? TYPE_BUTTON : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ITEM) {
            View view = mInflater.inflate(R.layout.item_select, parent, false);
            return new ItemHolder(view);
        }
        View view = mInflater.inflate(R.layout.item_chapter, parent, false);
        return new ButtonHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Switcher<Chapter> switcher = mDataSet.get(position);
        if (isButtonMode) {
            final ButtonHolder viewHolder = (ButtonHolder) holder;
            viewHolder.chapterButton.setText(STConvertUtils.convert(switcher.getElement().getTitle()));
            if (switcher.getElement().isDownload()) {
                viewHolder.chapterButton.setDownload(true);
                viewHolder.chapterButton.setSelected(false);
            } else {
                viewHolder.chapterButton.setDownload(false);
                viewHolder.chapterButton.setSelected(switcher.isEnable());
            }
            bindBookmark(viewHolder.bookmark, switcher.getElement());
        } else {
            ItemHolder viewHolder = (ItemHolder) holder;
            viewHolder.chapterTitle.setText(STConvertUtils.convert(switcher.getElement().getTitle()));
            viewHolder.chapterChoice.setEnabled(!switcher.getElement().isDownload());
            viewHolder.chapterChoice.setChecked(switcher.isEnable());
            bindBookmark(viewHolder.bookmark, switcher.getElement());
        }
    }

    private void bindBookmark(TextView bookmark, Chapter chapter) {
        if (bookmarkClickListener == null) {
            bookmark.setVisibility(View.GONE);
            return;
        }
        bookmark.setVisibility(View.VISIBLE);
        boolean marked = bookmarks != null && bookmarks.contains(chapter.getPath());
        bookmark.setText(marked ? "★" : "☆");
        bookmark.setSelected(marked);
        bookmark.setAlpha(marked ? 1f : 0.55f);
        bookmark.setContentDescription(mContext.getString(
                marked ? R.string.chapter_bookmark_remove : R.string.chapter_bookmark_add));
        bookmark.setOnClickListener(v -> bookmarkClickListener.onBookmarkClick(chapter));
    }

    public void setButtonMode(boolean enable) {
        isButtonMode = enable;
    }

    public void setBookmarks(Set<String> bookmarks) {
        this.bookmarks = bookmarks;
    }

    public void setOnBookmarkClickListener(OnBookmarkClickListener listener) {
        bookmarkClickListener = listener;
    }

    @Override
    public RecyclerView.ItemDecoration getItemDecoration() {
        return new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int offset = parent.getWidth() / 40;
                outRect.set(offset, 0, offset, (int) (offset * 1.5));
            }
        };
    }

    @Override
    protected boolean isClickValid() {
        return true;
    }

    static class ItemHolder extends BaseAdapter.BaseViewHolder {
        TextView chapterTitle;
        CheckBox chapterChoice;
        TextView bookmark;

        ItemHolder(View view) {
            super(view);
            chapterTitle = view.findViewById(R.id.item_select_title);
            chapterChoice = view.findViewById(R.id.item_select_checkbox);
            bookmark = view.findViewById(R.id.item_select_bookmark);
        }
    }

    static class ButtonHolder extends BaseAdapter.BaseViewHolder {
        ChapterButton chapterButton;
        TextView bookmark;

        ButtonHolder(View view) {
            super(view);
            chapterButton = view.findViewById(R.id.item_chapter_button);
            bookmark = view.findViewById(R.id.item_chapter_bookmark);
        }
    }

    public interface OnBookmarkClickListener {
        void onBookmarkClick(Chapter chapter);
    }

}
