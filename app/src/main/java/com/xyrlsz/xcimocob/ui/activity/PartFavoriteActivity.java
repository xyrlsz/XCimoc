package com.xyrlsz.xcimocob.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.manager.TagManager;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.PartFavoritePresenter;
import com.xyrlsz.xcimocob.ui.adapter.BaseAdapter;
import com.xyrlsz.xcimocob.ui.adapter.GridAdapter;
import com.xyrlsz.xcimocob.ui.fragment.dialog.MessageDialogFragment;
import com.xyrlsz.xcimocob.ui.fragment.dialog.MultiDialogFragment;
import com.xyrlsz.xcimocob.ui.view.PartFavoriteView;
import com.xyrlsz.xcimocob.utils.HintUtils;

import java.util.LinkedList;
import java.util.List;



/**
 * Created by Hiroshi on 2016/10/11.
 */

public class PartFavoriteActivity extends BackActivity implements PartFavoriteView, BaseAdapter.OnItemClickListener,
        BaseAdapter.OnItemLongClickListener {

    private static final int DIALOG_REQUEST_DELETE = 0;
    private static final int DIALOG_REQUEST_ADD = 1;

    RecyclerView mRecyclerView;
    FrameLayout mLayoutView;

    private PartFavoritePresenter mPresenter;
    private GridAdapter mGridAdapter;

    private MiniComic mSavedComic;
    private boolean isDeletable;

    public static Intent createIntent(Context context, long id, String title) {
        Intent intent = new Intent(context, PartFavoriteActivity.class);
        intent.putExtra(Extra.EXTRA_ID, id);
        intent.putExtra(Extra.EXTRA_KEYWORD, title);
        return intent;
    }

    @Override
    protected BasePresenter initPresenter() {
        mPresenter = new PartFavoritePresenter();
        mPresenter.attachView(this);
        return mPresenter;
    }

    @Override
    protected void initViewById() {
        super.initViewById();
        mRecyclerView = findViewById(R.id.part_favorite_recycler_view);
        mLayoutView = findViewById(R.id.part_favorite_root);
    }

    @Override
    protected void initView() {
        super.initView();
        mGridAdapter = new GridAdapter(this, new LinkedList<Object>());
        mGridAdapter.setSymbol(true);
        mGridAdapter.setProvider(((App) getApplication()).getBuilderProvider());
        mGridAdapter.setTitleGetter(SourceManager.getInstance(this).new TitleGetter());
        mGridAdapter.setOnItemClickListener(this);
        mGridAdapter.setOnItemLongClickListener(this);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemAnimator(null);
        mRecyclerView.addItemDecoration(mGridAdapter.getItemDecoration());
        mRecyclerView.setAdapter(mGridAdapter);
        ViewCompat.setOnApplyWindowInsetsListener(mLayoutView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    systemBars.bottom
            );
            return insets;
        });
    }

    @Override
    protected void initData() {
        long id = getIntent().getLongExtra(Extra.EXTRA_ID, -1);
        isDeletable = id != TagManager.TAG_CONTINUE && id != TagManager.TAG_FINISH;
        mPresenter.load(id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (getIntent().getLongExtra(Extra.EXTRA_ID, -1) >= 0) {
            getMenuInflater().inflate(R.menu.menu_part_favorite, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int __id = item.getItemId();
        if (__id == R.id.part_favorite_add) {
                showProgressDialog();
                mPresenter.loadComicTitle(mGridAdapter.getDateSet());
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(View view, int position) {
        MiniComic comic = (MiniComic) mGridAdapter.getItem(position);
        Intent intent = DetailActivity.createIntent(this, comic.getId(), -1, null);
        startActivity(intent);
    }

    @Override
    public boolean onItemLongClick(View view, int position) {
        if (isDeletable) {
            mSavedComic = (MiniComic) mGridAdapter.getItem(position);
            MessageDialogFragment fragment = MessageDialogFragment.newInstance(R.string.dialog_confirm,
                    R.string.part_favorite_delete_confirm, true, DIALOG_REQUEST_DELETE);
            fragment.show(getSupportFragmentManager(), null);
            return true;
        }
        return false;
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        switch (requestCode) {
            case DIALOG_REQUEST_DELETE:
                long id = mSavedComic.getId();
                mPresenter.delete(id);
                mGridAdapter.remove(mSavedComic);
                HintUtils.showToast(this, R.string.common_execute_success);
                break;
            case DIALOG_REQUEST_ADD:
                showProgressDialog();
                boolean[] check = bundle.getBooleanArray(EXTRA_DIALOG_RESULT_VALUE);
                mPresenter.insert(check);
                break;
        }
    }

    @Override
    public void onComicLoadFail() {
        hideProgressBar();
        HintUtils.showToast(this, R.string.common_data_load_fail);
    }

    @Override
    public void onComicLoadSuccess(List<Object> list) {
        hideProgressBar();
        mGridAdapter.addAll(list);

    }

    @Override
    public void onComicTitleLoadSuccess(List<String> list) {
        hideProgressDialog();
        MultiDialogFragment fragment = MultiDialogFragment.newInstance(R.string.part_favorite_select,
                list.toArray(new String[list.size()]), null, DIALOG_REQUEST_ADD);
        fragment.show(getSupportFragmentManager(), null);
    }

    @Override
    public void onComicTitleLoadFail() {
        hideProgressDialog();
        HintUtils.showToast(this, R.string.common_data_load_fail);
    }

    @Override
    public void onComicInsertSuccess(List<Object> list) {
        hideProgressDialog();
        mGridAdapter.addAll(list);
        HintUtils.showToast(this, R.string.common_execute_success);
    }

    @Override
    public void onComicInsertFail() {
        hideProgressDialog();
        HintUtils.showToast(this, R.string.common_execute_fail);
    }

    @Override
    public void onHighlightCancel(MiniComic comic) {
        mGridAdapter.moveItemTop(comic);
    }

    @Override
    public void onComicRead(MiniComic comic) {
        mGridAdapter.moveItemTop(comic);
    }

    @Override
    public void onComicRemove(long id) {
        mGridAdapter.removeItemById(id);
    }

    @Override
    public void onComicAdd(MiniComic comic) {
        if (!mGridAdapter.contains(comic)) {
            mGridAdapter.add(0, comic);
        }
    }

    @Override
    protected View getLayoutView() {
        return mLayoutView;
    }

    @Override
    protected String getDefaultTitle() {
        return getIntent().getStringExtra(Extra.EXTRA_KEYWORD);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_part_favorite;
    }

    @Override
    protected boolean isNavTranslation() {
        return true;
    }
}
