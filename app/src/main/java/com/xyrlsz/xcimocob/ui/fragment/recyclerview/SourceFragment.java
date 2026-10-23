package com.xyrlsz.xcimocob.ui.fragment.recyclerview;

import static android.content.Context.MODE_PRIVATE;
import static com.xyrlsz.xcimocob.ui.activity.WebviewActivity.EXTRA_IS_USE_TO_WEB_PARSER;
import static com.xyrlsz.xcimocob.ui.activity.WebviewActivity.EXTRA_WEB_URL;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import com.xyrlsz.xcimocob.component.DialogCaller;
import com.xyrlsz.xcimocob.manager.JsSourceManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.SourcePresenter;
import com.xyrlsz.xcimocob.source.js.JsMangaParser;
import com.xyrlsz.xcimocob.ui.activity.JsSourceSettingsActivity;
import com.xyrlsz.xcimocob.ui.activity.SearchActivity;
import com.xyrlsz.xcimocob.ui.activity.SourceDetailActivity;
import com.xyrlsz.xcimocob.ui.activity.WebviewActivity;
import com.xyrlsz.xcimocob.ui.adapter.BaseAdapter;
import com.xyrlsz.xcimocob.ui.adapter.SourceAdapter;
import com.xyrlsz.xcimocob.ui.fragment.dialog.EditorDialogFragment;
import com.xyrlsz.xcimocob.ui.view.SourceView;
import com.xyrlsz.xcimocob.utils.HintUtils;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.disposables.Disposable;


/**
 * Created by Hiroshi on 2016/8/11.
 */
public class SourceFragment extends RecyclerViewFragment implements SourceView, SourceAdapter.OnItemCheckedListener, DialogCaller {

    private static final int DIALOG_SOURCE_REPO = 2001;

    FrameLayout frameLayout;
    private SourcePresenter mPresenter;
    private SourceAdapter mSourceAdapter;
    private Disposable mUpdateDisposable;

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
    protected float getGridMinItemWidthDp() {
        return 180;
    }

    @Override
    protected int getGridMaxSpans() {
        return 4;
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
            SourceManager.getInstance(this).clearParserCache();
        } else if (__id == R.id.comic_allSelection) {
            for (int i = 0; i < mSourceAdapter.getItemCount(); i++) {
                Source source = mSourceAdapter.getItem(i);
                source.setEnable(true);
                mPresenter.update(source);
            }
            mSourceAdapter.notifyDataSetChanged();
            SourceManager.getInstance(this).clearParserCache();
        } else if (__id == R.id.comic_AllDeselect) {
            for (int i = 0; i < mSourceAdapter.getItemCount(); i++) {
                Source source = mSourceAdapter.getItem(i);
                source.setEnable(false);
                mPresenter.update(source);
            }
            mSourceAdapter.notifyDataSetChanged();
            SourceManager.getInstance(this).clearParserCache();
        } else if (__id == R.id.comic_update_source) {
            updateSources();
        } else if (__id == R.id.comic_source_repo) {
            EditorDialogFragment fragment = EditorDialogFragment.newInstance(
                    R.string.comic_source_repo,
                    JsSourceManager.getInstance(this).getRepoUrl(),
                    DIALOG_SOURCE_REPO);
            fragment.setTargetFragment(this, DIALOG_SOURCE_REPO);
            fragment.show(requireActivity().getSupportFragmentManager(), "editor_source_repo");
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        if (requestCode == DIALOG_SOURCE_REPO) {
            String url = bundle.getString(DialogCaller.EXTRA_DIALOG_RESULT_VALUE);
            JsSourceManager.getInstance(this).setRepoUrl(url);
            HintUtils.showToast(requireActivity(), R.string.comic_source_repo);
        }
    }

    private void updateSources() {
        JsSourceManager manager = JsSourceManager.getInstance(this);
        String repo = manager.getRepoUrl();
        if (repo == null || repo.isEmpty()) {
            HintUtils.showToast(requireActivity(), R.string.comic_update_source_empty_repo);
            return;
        }
        HintUtils.showToast(requireActivity(), R.string.comic_update_source_running);
        if (mUpdateDisposable != null && !mUpdateDisposable.isDisposed()) {
            mUpdateDisposable.dispose();
        }
        mUpdateDisposable = manager.updateFromServer(repo).subscribe(
                result -> {
                    HintUtils.showToast(requireActivity(), getString(
                            R.string.comic_update_source_success,
                            result.added, result.updated, result.removed, result.failed));
                    mPresenter.load();
                },
                e -> HintUtils.showToast(requireActivity(), R.string.common_execute_fail));
    }

    @Override
    public void onDestroyView() {
        if (mUpdateDisposable != null && !mUpdateDisposable.isDisposed()) {
            mUpdateDisposable.dispose();
        }
        super.onDestroyView();
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
        boolean isUseToOpenWebview = sharedPreferences.getBoolean(Constants.APP_SHARED_TEST_MODE, false);
        if (url == null || url.isEmpty() || !isUseToOpenWebview) {
            if (!isUseToOpenWebview) {
                openSourceSettings(position);
            }
            return;
        }
        intent.putExtra(EXTRA_WEB_URL, url);
        intent.putExtra(EXTRA_IS_USE_TO_WEB_PARSER, false);
        startActivity(intent);
    }

    private void openSourceSettings(int position) {
        Source source = mSourceAdapter.getItem(position);
        MangaParser parser = SourceManager.getInstance(this).getParser(source.getType());
        if (parser instanceof JsMangaParser) {
            JsMangaParser mParser;
            mParser = (JsMangaParser) parser;
            boolean hasLogin = mParser.hasLogin();
            JSONArray settings = mParser.getSettings();
            int settingCount = (settings == null) ? 0 : settings.length();
            if (hasLogin || settingCount != 0) {
                startActivity(JsSourceSettingsActivity.createIntent(requireActivity(), source.getType(), source.getTitle()));
            }
            return;
        }
        Intent intent = SourceDetailActivity.createIntent(getActivity(), source.getType());
        startActivity(intent);
    }

    @Override
    public boolean onItemLongClick(View view, int position) {
        openSourceSettings(position);
        return true;
    }

    @Override
    public void onItemCheckedListener(boolean isChecked, int position) {
        Source source = mSourceAdapter.getItem(position);
        source.setEnable(isChecked);
        mPresenter.update(source);
        SourceManager.getInstance(this).clearParserCache();
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
