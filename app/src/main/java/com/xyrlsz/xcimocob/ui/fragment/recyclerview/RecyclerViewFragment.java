package com.xyrlsz.xcimocob.ui.fragment.recyclerview;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.ui.adapter.BaseAdapter;
import com.xyrlsz.xcimocob.ui.fragment.BaseFragment;
import com.xyrlsz.xcimocob.utils.ScreenUtils;


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
        applyResponsiveGrid();
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

    /**
     * 网格每格的最小宽度（dp），返回大于 0 的值才会启用大屏列数自适应
     */
    protected float getGridMinItemWidthDp() {
        return 0;
    }

    protected int getGridMaxSpans() {
        return 8;
    }

    /**
     * 大屏（平板、折叠屏展开）时按可用宽度重算网格列数，手机上保持 initLayoutManager 的固定列数
     */
    private void applyResponsiveGrid() {
        float minItemWidthDp = getGridMinItemWidthDp();
        if (minItemWidthDp <= 0) {
            return;
        }
        ScreenUtils.bindResponsiveGrid(mRecyclerView, minItemWidthDp, getGridMaxSpans());
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
