package com.xyrlsz.xcimocob.ui.adapter;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;

import com.xyrlsz.xcimocob.global.FastClick;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;



/**
 * Created by Hiroshi on 2016/7/1.
 */
public abstract class BaseAdapter<T> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    Context mContext;
    List<T> mDataSet;
    LayoutInflater mInflater;
    List<T> mOriginalData = new ArrayList<>();
    private OnItemClickListener mClickListener;
    private OnItemLongClickListener mLongClickListener;

    public BaseAdapter(Context context, List<T> list) {
        mContext = context.getApplicationContext();
        mDataSet = list;
        mInflater = LayoutInflater.from(context);
    }

    public void add(T data) {
        if (mDataSet.add(data)) {
            mOriginalData.add(data);
            notifyItemInserted(mDataSet.size());
        }
    }

    public void add(int location, T data) {
        mDataSet.add(location, data);
        mOriginalData.add(location,data);
        notifyItemInserted(location);
    }

    public void addAll(Collection<T> collection) {
        addAll(mDataSet.size(), collection);
    }

    public void addAll(int location, Collection<T> collection) {
        if (mDataSet.addAll(location, collection)) {
            mOriginalData.addAll(location,collection);
            notifyItemRangeInserted(location, collection.size());
        }
    }

    public boolean exist(T data) {
        return mDataSet.indexOf(data) != -1;
    }

    public boolean remove(T data) {
        int position = mDataSet.indexOf(data);
        if (position != -1) {
            remove(position);
            return true;
        }
        return false;
    }

    public void remove(int position) {
        mDataSet.remove(position);
        mOriginalData.remove(position);
        notifyItemRemoved(position);
    }

    public void removeAll(Collection<T> collection) {
        mDataSet.removeAll(collection);
        mOriginalData.remove(collection);
        notifyDataSetChanged();
    }

    public boolean contains(T data) {
        return mDataSet.contains(data);
    }

    public void reverse() {
        Collections.reverse(mDataSet);
        Collections.reverse(mOriginalData);
        notifyDataSetChanged();
    }

    public void clear() {
        mDataSet.clear();
        mOriginalData.clear();
        notifyDataSetChanged();
    }

    public List<T> getDateSet() {
        return mDataSet;
    }

    public void setData(Collection<T> collection) {
        mDataSet.clear();
        mDataSet.addAll(collection);
        mOriginalData.clear();
        mOriginalData.addAll(collection);
        notifyDataSetChanged();
    }

    public T getItem(int position) {
        return mDataSet.get(position);
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        mClickListener = onItemClickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener onItemLongClickListener) {
        mLongClickListener = onItemLongClickListener;
    }

    public abstract RecyclerView.ItemDecoration getItemDecoration();

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClickListener != null && isClickValid()) {
                    int adapterPosition = holder.getAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        mClickListener.onItemClick(v, adapterPosition);
                    }
                }
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mLongClickListener == null) {
                    return false;
                }
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    return mLongClickListener.onItemLongClick(v, adapterPosition);
                }
                return false;
            }
        });
    }

    protected boolean isClickValid() {
        return FastClick.isClickValid();
    }

    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(View view, int position);
    }

    static class BaseViewHolder extends RecyclerView.ViewHolder {
        BaseViewHolder(View view) {
            super(view);
        }
    }

}
