package com.xyrlsz.xcimocob.ui.fragment.recyclerview;

import static android.content.Context.MODE_PRIVATE;
import static com.xyrlsz.xcimocob.ui.activity.WebviewActivity.EXTRA_IS_USE_TO_WEB_PARSER;
import static com.xyrlsz.xcimocob.ui.activity.WebviewActivity.EXTRA_WEB_URL;

import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xyrlsz.xcimocob.Constants;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.SourcePresenter;
import com.xyrlsz.xcimocob.ui.activity.SearchActivity;
import com.xyrlsz.xcimocob.ui.activity.SourceDetailActivity;
import com.xyrlsz.xcimocob.ui.activity.WebviewActivity;
import com.xyrlsz.xcimocob.ui.adapter.BaseAdapter;
import com.xyrlsz.xcimocob.ui.adapter.SourceAdapter;
import com.xyrlsz.xcimocob.ui.view.SourceView;
import com.xyrlsz.xcimocob.utils.HintUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by Hiroshi on 2016/8/11.
 */
public class SourceFragment extends RecyclerViewFragment implements SourceView, SourceAdapter.OnItemCheckedListener {

    FrameLayout frameLayout;
    private SourcePresenter mPresenter;
    private SourceAdapter mSourceAdapter;

    @Override
    protected BasePresenter initPresenter() {
        mPresenter = new SourcePresenter();
        mPresenter.attachView(this);
        return mPresenter;
    }

    @Override
    protected void initView() {
        setHasOptionsMenu(true);
        frameLayout = mRootView.findViewById(R.id.fragment_container);
        ViewCompat.setOnApplyWindowInsetsListener(frameLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    systemBars.bottom
            );
            return insets;
        });
        super.initView();
    }

    @Override
    protected BaseAdapter initAdapter() {
        mSourceAdapter = new SourceAdapter(getActivity(), new ArrayList<Source>());
        mSourceAdapter.setOnItemCheckedListener(this);
        return mSourceAdapter;
    }

    @Override
    protected RecyclerView.LayoutManager initLayoutManager() {
        return new GridLayoutManager(getActivity(), 2);
    }

    @Override
    protected void initData() {
        mPresenter.load();
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();
        if (mPresenter != null && isAdded()) {
            mPresenter.load();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_source, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int __id = item.getItemId();
        if (__id == R.id.comic_search) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
        } else if (__id == R.id.comic_inverseSelection) {
                for (int i = 0; i < mSourceAdapter.getItemCount(); i++) {
                    Source source = mSourceAdapter.getItem(i);
                    source.setEnable(!source.getEnable());
                    mPresenter.update(source);
                }
                mSourceAdapter.notifyDataSetChanged();
        } else if (__id == R.id.comic_allSelection) {
                for (int i = 0; i < mSourceAdapter.getItemCount(); i++) {
                    Source source = mSourceAdapter.getItem(i);
                    source.setEnable(true);
                    mPresenter.update(source);
                }
                mSourceAdapter.notifyDataSetChanged();
        } else if (__id == R.id.comic_AllDeselect) {
                for (int i = 0; i < mSourceAdapter.getItemCount(); i++) {
                    Source source = mSourceAdapter.getItem(i);
                    source.setEnable(false);
                    mPresenter.update(source);
                }
                mSourceAdapter.notifyDataSetChanged();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(View view, int position) {
//        Source source = mSourceAdapter.getItem(position);
//        if (SourceManager.getInstance(this).getParser(source.getType()).getCategory() == null) {
//            HintUtils.showToast(getActivity(), R.string.common_execute_fail);
//        } else {
//            Intent intent = CategoryActivity.createIntent(getActivity(), source.getType(), source.getTitle());
//            startActivity(intent);
//        }
        Source source = mSourceAdapter.getItem(position);
        Intent intent = new Intent(getContext(), WebviewActivity.class);
        String url = source.getBaseUrl();
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences(Constants.APP_SHARED, MODE_PRIVATE);
        boolean isUseToWebParser = sharedPreferences.getBoolean(Constants.APP_SHARED_TEST_MODE, false);
        if (url == null || url.isEmpty() || !isUseToWebParser) {
            return;
        }
        intent.putExtra(EXTRA_WEB_URL, url);
        intent.putExtra(EXTRA_IS_USE_TO_WEB_PARSER, false);
        startActivity(intent);
    }

    @Override
    public boolean onItemLongClick(View view, int position) {
        Intent intent = SourceDetailActivity.createIntent(getActivity(), mSourceAdapter.getItem(position).getType());
        startActivity(intent);
        return true;
    }

    @Override
    public void onItemCheckedListener(boolean isChecked, int position) {
        Source source = mSourceAdapter.getItem(position);
        source.setEnable(isChecked);
        mPresenter.update(source);
    }

    @Override
    public void onSourceLoadSuccess(List<Source> list) {
        hideProgressBar();
        mSourceAdapter.setData(list);
    }

    @Override
    public void onSourceLoadFail() {
        hideProgressBar();
        HintUtils.showToast(getActivity(), R.string.common_data_load_fail);
    }

    @Override
    public void onThemeChange(@ColorRes int primary, @ColorRes int accent) {
        mSourceAdapter.setColor(ContextCompat.getColor(requireActivity(), accent));
        mSourceAdapter.notifyDataSetChanged();
    }

}
