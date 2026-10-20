package com.xyrlsz.xcimocob.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.utils.STConvertUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;

import java.util.List;

/**
 * Created by Hiroshi on 2016/10/10.
 */

public class SourceAdapter extends BaseAdapter<Source> {

    private OnItemCheckedListener mOnItemCheckedListener;
    private @ColorInt
    int color = -1;

    public SourceAdapter(Context context, List<Source> list) {
        super(context, list);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.item_source, parent, false);
        return new SourceHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Source source = mDataSet.get(position);
        final SourceHolder viewHolder = (SourceHolder) holder;
        viewHolder.sourceTitle.setText(STConvertUtils.convert(source.getTitle()));
        viewHolder.sourceSwitch.setChecked(source.getEnable());
        viewHolder.sourceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mOnItemCheckedListener != null) {
                mOnItemCheckedListener.onItemCheckedListener(isChecked, viewHolder.getAdapterPosition());
            }
        });
        if (color != -1) {
            ColorStateList thumbList = new ColorStateList(new int[][]{{-android.R.attr.state_checked}, {android.R.attr.state_checked}},
                    new int[]{Color.WHITE, color});
            viewHolder.sourceSwitch.setThumbTintList(thumbList);
            int uncheckedTrack = ThemeUtils.isDarkMode(mContext) ? 0x4CFFFFFF : 0x4C000000;
            ColorStateList trackList = new ColorStateList(new int[][]{{-android.R.attr.state_checked}, {android.R.attr.state_checked}},
                    new int[]{uncheckedTrack, (0x00FFFFFF & color | 0x4C000000)});
            viewHolder.sourceSwitch.setTrackTintList(trackList);
        }
    }

    @Override
    public RecyclerView.ItemDecoration getItemDecoration() {
        return new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int offset = parent.getWidth() / 90;
                outRect.set(offset, 0, offset, (int) (offset * 1.5));
            }
        };
    }

    public void setOnItemCheckedListener(OnItemCheckedListener listener) {
        mOnItemCheckedListener = listener;
    }

    public void setColor(@ColorInt int color) {
        this.color = color;
    }

    public interface OnItemCheckedListener {
        void onItemCheckedListener(boolean isChecked, int position);
    }

    static class SourceHolder extends BaseViewHolder {
        TextView sourceTitle;
        SwitchCompat sourceSwitch;

        SourceHolder(final View view) {
            super(view);
            sourceTitle = view.findViewById(R.id.item_source_title);
            sourceSwitch = view.findViewById(R.id.item_source_switch);
        }
    }

}
