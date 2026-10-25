package com.xyrlsz.xcimocob.ui.fragment.recyclerview;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.ui.adapter.BaseAdapter;
import com.xyrlsz.xcimocob.ui.fragment.BaseFragment;


/**
 * Created by Hiroshi on 2016/10/11.
 */

public abstract class RecyclerViewFragment extends BaseFragment implements BaseAdapter.OnItemClickListener,
        BaseAdapter.OnItemLongClickListener {

    protected RecyclerView mRecyclerView;

    @Override
    protected void initView() {
        mRecyclerView = mRootView.findViewById(R.id.recycler_view_content);
        mRecyclerView.setItemAnimator(null);
        mRecyclerView.setLayoutManager(initLayoutManager());
        mRecyclerView.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                 oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                updateGridSpanCount();
            }
        });
        BaseAdapter adapter = initAdapter();
        if (adapter != null) {
            adapter.setOnItemClickListener(this);
            adapter.setOnItemLongClickListener(this);
            mRecyclerView.addItemDecoration(adapter.getItemDecoration());
            mRecyclerView.setAdapter(adapter);
        }
    }

    abstract protected BaseAdapter initAdapter();

    protected abstract RecyclerView.LayoutManager initLayoutManager();

    private void updateGridSpanCount() {
        RecyclerView.LayoutManager current = mRecyclerView.getLayoutManager();
        if (!(current instanceof GridLayoutManager)) {
            return;
        }
        RecyclerView.LayoutManager updated = initLayoutManager();
        if (updated instanceof GridLayoutManager) {
            int spanCount = ((GridLayoutManager) updated).getSpanCount();
            GridLayoutManager currentGrid = (GridLayoutManager) current;
            if (currentGrid.getSpanCount() != spanCount) {
                currentGrid.setSpanCount(spanCount);
            }
        }
    }

    @Override
    public void onItemClick(View view, int position) {
    }

    @Override
    public boolean onItemLongClick(View view, int position) {
        return false;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_recycler_view;
    }

}
