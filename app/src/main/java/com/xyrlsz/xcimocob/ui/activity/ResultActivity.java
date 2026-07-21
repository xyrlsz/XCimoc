package com.xyrlsz.xcimocob.ui.activity;

import static com.xyrlsz.xcimocob.ui.activity.SearchActivity.SEARCH_TITLE;

import android.content.Context;
import android.content.Intent;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.fresco.ControllerBuilderProvider;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.ResultPresenter;
import com.xyrlsz.xcimocob.ui.adapter.BaseAdapter;
import com.xyrlsz.xcimocob.ui.adapter.ResultAdapter;
import com.xyrlsz.xcimocob.ui.view.ResultView;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Hiroshi on 2016/7/3.
 */
public class ResultActivity extends BackActivity implements ResultView, BaseAdapter.OnItemClickListener {

    /**
     * 根据用户输入的关键词搜索
     * Extra: 关键词 图源列表
     */
    public static final int LAUNCH_MODE_SEARCH = 0;
    /**
     * 根据分类搜索，关键词字段存放 url 格式
     * Extra: 格式 图源
     */
    public static final int LAUNCH_MODE_CATEGORY = 1;
    // 建个Map把漫源搜索的上一个请求的url存下来，最后利用Activity生命周期清掉
    //
    // 解决重复加载列表问题思路：
    // 在新的一次请求（上拉加载）前检查新Url与上一次请求的是否一致。
    // 一致则返回空请求，达到阻断请求的目的；不一致则更新Map中存的Url，Map中不存在则新建
    // 注意：使用实例级缓存而非 static，避免跨实例泄漏
    public SparseArray<String> searchUrls = new SparseArray<>();
    RecyclerView mRecyclerView;
    FrameLayout mLayoutView;
    SwipeRefreshLayout mSwipeRefresh;
    private ResultAdapter mResultAdapter;

    @Override
    protected void initViewById() {
        super.initViewById();
        mRecyclerView = findViewById(R.id.result_recycler_view);
        mLayoutView = findViewById(R.id.result_layout);
        mSwipeRefresh = findViewById(R.id.result_swipe_refresh);
    }
    private LinearLayoutManager mLayoutManager;
    private ResultPresenter mPresenter;
    private ControllerBuilderProvider mProvider;
    private int resultMode;

    public static Intent createIntent(Context context, String keyword, int source, int resultMode) {
        return createIntent(context, keyword, new int[]{source}, resultMode);
    }

    public static Intent createIntent(Context context, String keyword, int[] array, int resultMode) {
        Intent intent = new Intent(context, ResultActivity.class);
        intent.putExtra(Extra.EXTRA_MODE, resultMode);
        intent.putExtra(Extra.EXTRA_SOURCE, array);
        intent.putExtra(Extra.EXTRA_KEYWORD, keyword);
        return intent;
    }

    public static Intent createIntent(Context context, String keyword, int[] array, int resultMode, int searchType) {
        Intent intent = new Intent(context, ResultActivity.class);
        intent.putExtra(Extra.EXTRA_MODE, resultMode);
        intent.putExtra(Extra.EXTRA_SOURCE, array);
        intent.putExtra(Extra.EXTRA_KEYWORD, keyword);
        intent.putExtra(Extra.EXTRA_SEARCH_TYPE, searchType);
        return intent;
    }


    public static Intent createIntent(Context context, String keyword, boolean strictSearch, boolean stSame, int[] array, int resultMode, int searchType) {
        Intent intent = createIntent(context, keyword, array, resultMode);
        intent.putExtra(Extra.EXTRA_STRICT, strictSearch);
        intent.putExtra(Extra.EXTAR_STSAME, stSame);
        intent.putExtra(Extra.EXTRA_SEARCH_TYPE, searchType);
        return intent;
    }

    @Override
    protected BasePresenter initPresenter() {
        String keyword = getIntent().getStringExtra(Extra.EXTRA_KEYWORD);
        int[] source = getIntent().getIntArrayExtra(Extra.EXTRA_SOURCE);
        boolean strictSearch = getIntent().getBooleanExtra(Extra.EXTRA_STRICT, true);
        boolean stSameSearch = getIntent().getBooleanExtra(Extra.EXTAR_STSAME, true);
        int searchType = getIntent().getIntExtra(Extra.EXTRA_SEARCH_TYPE, SEARCH_TITLE);
        mPresenter = new ResultPresenter(source, keyword, strictSearch, stSameSearch, searchType);
        mPresenter.attachView(this);
        return mPresenter;
    }

    @Override
    protected void initView() {
        super.initView();
        mLayoutManager = new LinearLayoutManager(this);
        mResultAdapter = new ResultAdapter(this, new LinkedList<Comic>());
        mResultAdapter.setOnItemClickListener(this);
        mProvider = new ControllerBuilderProvider(this, SourceManager.getInstance(this).new HeaderGetter(), true);
        mResultAdapter.setProvider(mProvider);
        mResultAdapter.setTitleGetter(SourceManager.getInstance(this).new TitleGetter());
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(mResultAdapter.getItemDecoration());
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (mLayoutManager.findLastVisibleItemPosition() >= mResultAdapter.getItemCount() - 4 && dy > 0) {
                    load();
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                switch (newState) {
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        mProvider.pause();
                        break;
                    case RecyclerView.SCROLL_STATE_IDLE:
                        mProvider.resume();
                        break;
                }
            }
        });
        mRecyclerView.setAdapter(mResultAdapter);
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
        resultMode = getIntent().getIntExtra(Extra.EXTRA_MODE, -1);
        load();

        // 启用下拉刷新
        enablePullRefresh(() -> {
            mResultAdapter.clear();
            mPresenter.refresh();
            load();
        });
    }

    @Override
    protected void onDestroy() {
        if (mProvider != null) {
            mProvider.clear();
        }
        // 清理实例级缓存
        searchUrls.clear();
        super.onDestroy();
    }

    private void load() {
        switch (resultMode) {
            case LAUNCH_MODE_SEARCH:
                mPresenter.loadSearch();
                break;
            case LAUNCH_MODE_CATEGORY:
                mPresenter.loadCategory();
                break;
        }
    }

    @Override
    public void onItemClick(View view, int position) {
        Comic comic = mResultAdapter.getItem(position);
        Intent intent = DetailActivity.createIntent(this, null, comic.getSource(), comic.getCid());
        startActivity(intent);
    }

    @Override
    public void onSearchSuccess(Comic comic) {
        hideProgressBar();
        stopPullRefresh();
        mResultAdapter.add(comic);
    }

    @Override
    public void onLoadSuccess(List<Comic> list) {
        hideProgressBar();
        stopPullRefresh();
        mResultAdapter.setData(list);
    }

    @Override
    public void onLoadFail() {
        hideProgressBar();
        stopPullRefresh();
        showSnackbar(R.string.common_parse_error);
    }

    @Override
    public void onSearchError() {
        hideProgressBar();
        stopPullRefresh();
        showSnackbar(R.string.result_empty);
    }

    /**
     * 启用下拉刷新
     */
    private void enablePullRefresh(SwipeRefreshLayout.OnRefreshListener listener) {
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setOnRefreshListener(listener);
            mSwipeRefresh.setColorSchemeResources(
                    android.R.color.holo_blue_light,
                    android.R.color.holo_orange_light,
                    android.R.color.holo_green_light);
        }
    }

    /**
     * 停止下拉刷新动画
     */
    private void stopPullRefresh() {
        if (mSwipeRefresh != null && mSwipeRefresh.isRefreshing()) {
            mSwipeRefresh.setRefreshing(false);
        }
    }

    @Override
    protected String getDefaultTitle() {
        return getString(R.string.result);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_result;
    }

    @Override
    protected View getLayoutView() {
        return mLayoutView;
    }

    @Override
    protected boolean isNavTranslation() {
        return true;
    }

}
