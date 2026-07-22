package com.xyrlsz.xcimocob.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.model.SearchHistory;

import java.util.List;

/**
 * 搜索历史关键词适配器 - 以 Chip 小按钮形式展示
 * Created by XCimoc on 2026/07/22.
 */
public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {

    private List<SearchHistory> mData;
    private OnItemClickListener mListener;
    private OnItemDeleteListener mDeleteListener;

    public interface OnItemClickListener {
        void onItemClick(String keyword);
    }

    public interface OnItemDeleteListener {
        void onItemDelete(SearchHistory history);
    }

    public SearchHistoryAdapter(List<SearchHistory> data) {
        this.mData = data;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mListener = listener;
    }

    public void setOnItemDeleteListener(OnItemDeleteListener listener) {
        this.mDeleteListener = listener;
    }

    public void updateData(List<SearchHistory> newData) {
        this.mData = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchHistory item = mData.get(position);
        holder.chip.setText(item.getKeyword());
        holder.chip.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onItemClick(item.getKeyword());
            }
        });
        holder.chip.setOnCloseIconClickListener(v -> {
            if (mDeleteListener != null) {
                mDeleteListener.onItemDelete(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        Chip chip;

        ViewHolder(View itemView) {
            super(itemView);
            chip = itemView.findViewById(R.id.chip_history_keyword);
        }
    }
}
