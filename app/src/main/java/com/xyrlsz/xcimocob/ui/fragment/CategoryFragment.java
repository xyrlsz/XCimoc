package com.xyrlsz.xcimocob.ui.fragment;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.core.Manga;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.CategoryMiniComic;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.Category;
import com.xyrlsz.xcimocob.parser.Parser;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.ui.activity.DetailActivity;
import com.xyrlsz.xcimocob.ui.adapter.CategoryAdapter;
import com.xyrlsz.xcimocob.ui.adapter.CategoryGridAdapter;
import com.xyrlsz.xcimocob.ui.view.CategoryView;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.STConvertUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;
import com.xyrlsz.xcimocob.utils.ThreadRunUtils;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;


public class CategoryFragment extends BaseFragment implements CategoryView, AdapterView.OnItemSelectedListener {

    private static final String SAVED_STATE_SOURCE = "saved_state_source";
    private static final String SAVED_STATE_PAGE = "saved_state_page";
    private static final String SAVED_STATE_STATE = "saved_state_state";
    private static final String SAVED_STATE_FORMAT = "saved_state_format";
    private static final String SAVED_STATE_HEAD_COLLAPSED = "saved_state_head_collapsed";

    private static final int STATE_NULL = 0;
    private static final int STATE_DOING = 1;
    private static final int STATE_DONE = 3;
    //    private static final int STATE_NO_MORE = 4; // 没有更多数据
    private final List<CategoryMiniComic> mComicList = new ArrayList<>();
    List<AppCompatSpinner> mSpinnerList;
    List<View> mCategoryView;

    AppCompatSpinner mCategorySourceSpinner;
    RecyclerView mRecyclerView;

    CategoryGridAdapter categoryGridAdapter;
    int mSource;
    View headView;
    View mProgressOverlay;
    View mContentContainer;
    FloatingActionButton actionButton;
    FloatingActionButton actionButtonToTop;
    ImageButton toggleHeadButton;
    private Category mCategory;
    private SourceManager mSourceManager;
    private LinkedList<Pair<String, String>> mSourceList;
    private CompositeDisposable mCompositeSubscription;
    private State mCurrentState;
    private String mCurrentFormat;
    private String mPendingTag;
    private int mPendingSource = -1;
    private int mInitializedCategorySource = -1;
    private boolean isHeadCollapsed = false;
    private int headOriginalHeight = 0;

    @Override
    protected BasePresenter initPresenter() {
        return super.initPresenter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCompositeSubscription != null && !mCompositeSubscription.isDisposed()) {
            mCompositeSubscription.dispose();
            mCompositeSubscription.clear();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mCurrentState != null) {
            outState.putInt(SAVED_STATE_SOURCE, mCurrentState.source);
            outState.putInt(SAVED_STATE_PAGE, mCurrentState.page);
            outState.putInt(SAVED_STATE_STATE, mCurrentState.state);
        }
        if (mCurrentFormat != null) {
            outState.putString(SAVED_STATE_FORMAT, mCurrentFormat);
        }
        outState.putBoolean(SAVED_STATE_HEAD_COLLAPSED, isHeadCollapsed);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            // 在 initData 设置完 mCurrentState 后恢复状态
            if (mCurrentState != null) {
                mCurrentState.source = savedInstanceState.getInt(SAVED_STATE_SOURCE, mCurrentState.source);
                mCurrentState.page = savedInstanceState.getInt(SAVED_STATE_PAGE, mCurrentState.page);
                mCurrentState.state = savedInstanceState.getInt(SAVED_STATE_STATE, mCurrentState.state);
            }
            mCurrentFormat = savedInstanceState.getString(SAVED_STATE_FORMAT, mCurrentFormat);
            isHeadCollapsed = savedInstanceState.getBoolean(SAVED_STATE_HEAD_COLLAPSED, false);
        }
    }

    /**
     * 后台构建「支持分类」的源列表。
     * <p>
     * 优先用 Source 表缓存的 {@code hasCategory} 标志快速过滤（无需启动 JS 引擎）；
     * 对尚未回填元信息的源（hasCategory=false 但可能实际支持分类），回退调用
     * {@code getCategory()} 在后台线程判定，避免漏显。整个过程不阻塞主线程。
     */
    private LinkedList<Pair<String, String>> buildSourceList() {
        List<Source> sourceList = mSourceManager.listEnable();
        LinkedList<Pair<String, String>> result = new LinkedList<>();
        for (Source source : sourceList) {
            if (!source.getEnable()) {
                continue;
            }
            boolean hasCategory = source.isHasCategory();
            if (!hasCategory) {
                // 元信息未回填：后台跑一次 getCategory 判定（结果会被解析器自身缓存）
                Category cat = mSourceManager.getParser(source.getType()).getCategory();
                hasCategory = cat != null;
            }
            if (hasCategory) {
                result.add(new Pair<>(source.getTitle(), Integer.toString(source.getType())));
            }
        }
        return result;
    }

    /**
     * 异步加载源列表：后台过滤 → 主线程更新 Spinner 与状态。
     */
    private void loadSourceListAsync() {
        mCompositeSubscription.add(Observable.fromCallable(this::buildSourceList)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    mSourceList = list;
                    if (list.isEmpty()) {
                        mSource = -1;
                        mCategorySourceSpinner.setAdapter(new CategoryAdapter(getContext(), mSourceList));
                        mPendingSource = -1;
                        if (mPendingTag != null) {
                            mPendingTag = null;
                            showTagUnsupported();
                        }
                    } else {
                        int selectedPosition = 0;
                        if (mPendingSource != -1) {
                            for (int i = 0; i < list.size(); i++) {
                                if (Integer.parseInt(list.get(i).second) == mPendingSource) {
                                    selectedPosition = i;
                                    break;
                                }
                            }
                            if (Integer.parseInt(list.get(selectedPosition).second) != mPendingSource
                                    && mPendingTag != null) {
                                mPendingTag = null;
                                showTagUnsupported();
                            }
                        }
                        mPendingSource = -1;
                        mSource = Integer.parseInt(list.get(selectedPosition).second);
                        mCategorySourceSpinner.setAdapter(new CategoryAdapter(getContext(), mSourceList));
                        mCategorySourceSpinner.setSelection(selectedPosition);
                        // setSelection 会触发 onItemSelected → initSpinner，这里显式调用一次保证初始化
                        initSpinner(mSource);
                        if (mCurrentState != null) {
                            mCurrentState.source = mSource;
                        }
                    }
                    hideProgressOverlay();
                }, throwable -> {
                    throwable.printStackTrace();
                    hideProgressOverlay();
                }));
    }

    private void hideProgressOverlay() {
        if (mProgressOverlay != null) {
            mProgressOverlay.setVisibility(View.GONE);
        }
        // 数据加载完毕，显示内容区与回顶按钮
        if (mContentContainer != null) {
            mContentContainer.setVisibility(View.VISIBLE);
        }
        if (actionButtonToTop != null) {
            actionButtonToTop.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void initData() {
        // 查找视图（mRootView 在 onCreateView 中已设置）
        mSpinnerList = new ArrayList<>();
        mSpinnerList.add(mRootView.findViewById(R.id.category_spinner_subject));
        mSpinnerList.add(mRootView.findViewById(R.id.category_spinner_area));
        mSpinnerList.add(mRootView.findViewById(R.id.category_spinner_reader));
        mSpinnerList.add(mRootView.findViewById(R.id.category_spinner_year));
        mSpinnerList.add(mRootView.findViewById(R.id.category_spinner_progress));
        mSpinnerList.add(mRootView.findViewById(R.id.category_spinner_order));

        mCategoryView = new ArrayList<>();
        mCategoryView.add(mRootView.findViewById(R.id.category_subject));
        mCategoryView.add(mRootView.findViewById(R.id.category_area));
        mCategoryView.add(mRootView.findViewById(R.id.category_reader));
        mCategoryView.add(mRootView.findViewById(R.id.category_year));
        mCategoryView.add(mRootView.findViewById(R.id.category_progress));
        mCategoryView.add(mRootView.findViewById(R.id.category_order));

        mCategorySourceSpinner = mRootView.findViewById(R.id.category_spinner_source);
        mRecyclerView = mRootView.findViewById(R.id.recycler_view_content);
        headView = mRootView.findViewById(R.id.head_view);
        actionButton = mRootView.findViewById(R.id.category_action_button);
        toggleHeadButton = mRootView.findViewById(R.id.category_action_toggle_head);

        mSourceManager = SourceManager.getInstance(this);
        mSourceList = new LinkedList<>();
        mSource = -1;
        mCompositeSubscription = new CompositeDisposable();

        // 详情页标签跳转携带的待选标签：自动选中主题分类并触发加载
        mPendingTag = requireActivity().getIntent().getStringExtra(Extra.EXTRA_KEYWORD);
        mPendingSource = requireActivity().getIntent().getIntExtra(Extra.EXTRA_SOURCE, -1);

        mCurrentState = new State();
        mCurrentState.source = mSource;
        mCurrentState.page = 0;
        mCurrentState.state = STATE_NULL;

        mCategorySourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= mSourceList.size()) {
                    return;
                }
                mSource = Integer.parseInt(mSourceList.get(position).second);
                initSpinner(mSourceList.get(position).second);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (mSourceList.isEmpty()) {
                    return;
                }
                mSource = Integer.parseInt(mSourceList.get(0).second);
                initSpinner(mSourceList.get(0).second);
            }
        });
    }

    @Override
    protected void initView() {
        setHasOptionsMenu(true);

        actionButtonToTop = mRootView.findViewById(R.id.category_action_button_to_top);
        actionButtonToTop.setOnClickListener(v -> onToTopButtonClick());
        mRootView.findViewById(R.id.category_action_button).setOnClickListener(v -> onActionButtonClick());

        categoryGridAdapter = new CategoryGridAdapter(getContext(), mComicList);
        categoryGridAdapter.setProvider(getAppInstance().getBuilderProvider());
        categoryGridAdapter.setOnComicClickListener(comic -> {
            // TODO: 打开详情页
            Intent intent = DetailActivity.createIntent(getContext(), null, comic.getSource(), comic.getCid());
            startActivity(intent);
        });
        mRecyclerView.setAdapter(categoryGridAdapter);
        mRecyclerView.setRecycledViewPool(getAppInstance().getGridRecycledPool());
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemAnimator(null);
        mRecyclerView.setLayoutManager(initLayoutManager());
        mRecyclerView.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                 oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                updateGridSpanCount();
            }
        });

        // 加载蒙层（含居中 ProgressBar）与内容容器：初始仅显示进度条，加载完成后显示内容
        mProgressOverlay = mRootView.findViewById(R.id.category_progress_overlay);
        mContentContainer = mRootView.findViewById(R.id.category_content_container);

        // 源列表在后台构建，完成后再填充 Spinner 并初始化分类选项
        loadSourceListAsync();
        GridLayoutManager layoutManager = (GridLayoutManager) mRecyclerView.getLayoutManager();
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private ScrollDirection lastDirection = ScrollDirection.NONE;

            @Override
            public void onScrollStateChanged(@NotNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                switch (newState) {
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        getAppInstance().getBuilderProvider().pause();
                        break;
                    case RecyclerView.SCROLL_STATE_IDLE:
                        getAppInstance().getBuilderProvider().resume();
                        break;
                }

            }

            @Override
            public void onScrolled(@NotNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (layoutManager != null) {
                    int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                    int totalItemCount = layoutManager.getItemCount();

                    // 当滚动到最后几个项目时加载更多
                    if (totalItemCount > 0 && lastVisibleItem >= totalItemCount - 6) {
                        loadMoreData();
                    }
                }
                ScrollDirection currentDirection = ScrollDirection.NONE;
                // 滑动阈值，避免微小抖动
                int scrollThreshold = 10;
                if (dy > scrollThreshold) {
                    currentDirection = ScrollDirection.UP;
                } else if (dy < -scrollThreshold) {
                    currentDirection = ScrollDirection.DOWN;
                }
                // 方向发生变化时处理
                if (currentDirection != lastDirection) {
                    lastDirection = currentDirection;
                    switch (currentDirection) {
                        case UP:
                            // 处理向上滑动
                            if (!isHeadCollapsed && !mComicList.isEmpty()) {
                                toggleHeadView();
                            }
                            break;
                        case DOWN:
                            // 处理向下滑动

                            break;
                    }
                }
            }

            private enum ScrollDirection {
                UP, DOWN, NONE
            }
        });
        boolean isDarkMode = ThemeUtils.isDarkMode(requireContext());
        if (isDarkMode) {
            toggleHeadButton.setImageResource(R.drawable.ic_bxs_up_arrow_white);
        } else {
            toggleHeadButton.setImageResource(R.drawable.ic_bxs_up_arrow);
        }
        toggleHeadButton.setOnClickListener(v -> toggleHeadView());

    }

    private void toggleHeadView() {
        headView.measure(
                View.MeasureSpec.makeMeasureSpec(headView.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED
        );
        int realHeight = headView.getMeasuredHeight();

        int startHeight;
        int endHeight;

        if (isHeadCollapsed) {
            startHeight = 0;
            endHeight = realHeight;
            animateIconToggle(true);
//            actionButton.setVisibility(View.VISIBLE);
        } else {
            startHeight = headView.getHeight();
            endHeight = 0;
            animateIconToggle(false);
//            actionButton.setVisibility(View.GONE);
        }
        ValueAnimator animator = ValueAnimator.ofInt(startHeight, endHeight);
        if (headOriginalHeight == 0) {
            headView.measure(View.MeasureSpec.makeMeasureSpec(headView.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.UNSPECIFIED);
            headOriginalHeight = headView.getMeasuredHeight();
        }

        animator.setDuration(300);
        animator.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            headView.getLayoutParams().height = val;
            headView.requestLayout();
        });
        animator.start();

        isHeadCollapsed = !isHeadCollapsed;
    }

    private void animateIconToggle(boolean expand) {
        int from = expand ? 180 : 0;
        int to = expand ? 0 : 180;

        toggleHeadButton.animate()
                .rotation(to)
                .setDuration(200)
                .start();
    }

    protected RecyclerView.LayoutManager initLayoutManager() {
        int width = mRecyclerView.getRootView().getWidth();
        int height = mRecyclerView.getRootView().getHeight();
        if (width <= 0 || height <= 0) {
            width = App.mWidthPixels;
            height = App.mHeightPixels;
        }
        int spanCount = 3;
        if (height * 9 < width * 16) {
            spanCount = 4;
        }
        GridLayoutManager manager = new GridLayoutManager(getActivity(), spanCount);
        manager.setRecycleChildrenOnDetach(true);
        return manager;
    }

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


    private void initSpinner(String source) {
        initSpinner(Integer.parseInt(source));
    }

    private void initSpinner(int source) {
        if (mInitializedCategorySource == source) {
            return;
        }
        mInitializedCategorySource = source;
//      mComicList.clear();
//      categoryGridAdapter.notifyDataSetChanged();

        mCategory = mSourceManager.getParser(source).getCategory();
        mCurrentState.source = source;
        int[] type = new int[]{Category.CATEGORY_SUBJECT, Category.CATEGORY_AREA, Category.CATEGORY_READER,
                Category.CATEGORY_YEAR, Category.CATEGORY_PROGRESS, Category.CATEGORY_ORDER};

        if (mCategory == null) {
            // 该源无分类：隐藏所有分类行，避免 NPE
            for (int i = 0; i != type.length; ++i) {
                mCategoryView.get(i).setVisibility(View.GONE);
            }
            return;
        }
        for (int i = 0; i != type.length; ++i) {
            if (mCategory.hasAttribute(type[i])) {
                mCategoryView.get(i).setVisibility(View.VISIBLE);
                if (!mCategory.isComposite()) {
                    mSpinnerList.get(i).setOnItemSelectedListener(this);
                }
                mSpinnerList.get(i).setAdapter(new CategoryAdapter(getContext(), mCategory.getAttrList(type[i])));
            } else {
                mCategoryView.get(i).setVisibility(View.GONE);
            }
        }
        headView.post(() -> {
            headView.measure(
                    View.MeasureSpec.makeMeasureSpec(headView.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.UNSPECIFIED
            );
            headOriginalHeight = headView.getMeasuredHeight();

            // 若当前是展开状态，需要立即更新高度
            if (!isHeadCollapsed) {
                headView.getLayoutParams().height = headOriginalHeight;
                headView.requestLayout();
            }
        });
        applyPendingTag();
    }

    /**
     * 详情页点击标签跳转后，遍历当前图源的所有分类维度，找到匹配项并立即检索。
     * 先按显示名称匹配，也兼容标签直接传入分类值。
     */
    private void applyPendingTag() {
        final String tag = mPendingTag;
        if (tag == null || tag.trim().isEmpty()) {
            return;
        }
        mPendingTag = null;
        if (mCategory == null) {
            showTagUnsupported();
            return;
        }
        String target = STConvertUtils.convert(tag).trim();
        int matchedAttribute = -1;
        int matchedPosition = -1;
        Pair<String, String> matchedOption = null;
        for (int attribute = Category.CATEGORY_SUBJECT;
             attribute <= Category.CATEGORY_ORDER && matchedOption == null;
             attribute++) {
            if (!mCategory.hasAttribute(attribute)) {
                continue;
            }
            List<Pair<String, String>> attrList = mCategory.getAttrList(attribute);
            if (attrList == null) {
                continue;
            }
            for (int position = 0; position < attrList.size(); position++) {
                Pair<String, String> option = attrList.get(position);
                if (option == null) {
                    continue;
                }
                String name = option.first == null ? null : STConvertUtils.convert(option.first).trim();
                String value = option.second == null ? null : STConvertUtils.convert(option.second).trim();
                if (target.equals(name) || target.equals(value)) {
                    matchedAttribute = attribute;
                    matchedPosition = position;
                    matchedOption = option;
                    break;
                }
            }
        }
        if (matchedOption == null) {
            showTagUnsupported();
            return;
        }
        mSpinnerList.get(matchedAttribute).setSelection(matchedPosition, false);
        String[] args = new String[mSpinnerList.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = getSpinnerValue(mSpinnerList.get(i));
        }
        args[matchedAttribute] = matchedOption.second;
        searchCategory(args);
    }

    private void showTagUnsupported() {
        if (mRootView != null) {
            HintUtils.showSnackbar(mRootView, getString(R.string.category_tag_unsupported));
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        for (AppCompatSpinner spinner : mSpinnerList) {
            if (position == 0) {
                spinner.setEnabled(true);
            } else if (!parent.equals(spinner)) {
                spinner.setEnabled(false);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    void onToTopButtonClick() {
        mRecyclerView.scrollToPosition(0);
    }

    void onActionButtonClick() {
        if (mCategory == null) {
            // 当前源无分类信息（理论上不会发生，buildCategory 已保证非空），防御性处理
            return;
        }
        String[] args = new String[mSpinnerList.size()];
        for (int i = 0; i != args.length; ++i) {
            args[i] = getSpinnerValue(mSpinnerList.get(i));
        }
        searchCategory(args);
    }

    private void searchCategory(String[] args) {
        mCurrentFormat = mCategory.getFormat(args);

        mCurrentState.state = STATE_NULL;
        mCurrentState.page = 0;

        mComicList.clear();
        categoryGridAdapter.notifyDataSetChanged();

        loadCategory(mCurrentState, mCurrentFormat);
    }

    private void loadMoreData() {
        if (mCurrentState != null && mCurrentState.state == STATE_DONE) {
            mCurrentState.state = STATE_NULL;
            loadCategory(mCurrentState, mCurrentFormat);
        }
    }

    public void loadCategory(State state, String format) {
        if (state.state == STATE_NULL) {
            Parser parser = mSourceManager.getParser(state.source);
            state.state = STATE_DOING;
            mCompositeSubscription.add(Manga.getCategoryComic(parser, format, ++state.page)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(list -> {
                        if (state.page == 1) {
                            mComicList.clear();
                        }
                        if (list != null && !list.isEmpty()) {
                            for (Comic comic : list) {
                                mComicList.add(new CategoryMiniComic(comic));
                            }

                            // 根据返回数据数量判断是否还有更多数据
                            if (list.size() >= 5) {
                                state.state = STATE_DONE; // 可以加载下一页
                            } else {
                                state.state = STATE_NULL; // 没有更多数据
                            }

                            ThreadRunUtils.runOnMainThread(() -> {
                                int start = mComicList.size() - list.size();
                                if (start > 0) {
                                    categoryGridAdapter.notifyItemRangeInserted(start, list.size());
                                } else {
                                    categoryGridAdapter.notifyDataSetChanged();
                                }
                                // 可以添加加载完成的提示
                            });
                        } else {
                            // 没有更多数据了
                            state.state = STATE_NULL;
                            // 可以显示"没有更多数据"的提示
                        }

                    }, throwable -> {
                        throwable.printStackTrace();
                        state.state = STATE_DONE; // 出错后允许重试
                    }));
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_category, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int __id = item.getItemId();
        if (__id == R.id.category_refresh) {
                // 刷新时重新进入加载态：隐藏内容区，只显示进度条
                if (mContentContainer != null) {
                    mContentContainer.setVisibility(View.GONE);
                }
                if (actionButtonToTop != null) {
                    actionButtonToTop.setVisibility(View.GONE);
                }
                if (mProgressOverlay != null) {
                    mProgressOverlay.setVisibility(View.VISIBLE);
                }
                mInitializedCategorySource = -1;
                loadSourceListAsync();
                // mComicList.clear();
                // categoryGridAdapter.notifyDataSetChanged();
        }
        return super.onOptionsItemSelected(item);
    }

    private String getSpinnerValue(AppCompatSpinner spinner) {
        int index = mSpinnerList.indexOf(spinner);
        if (index < 0 || mCategoryView.get(index).getVisibility() != View.VISIBLE) {
            return null;
        }
        if (!(spinner.getAdapter() instanceof CategoryAdapter adapter)) {
            return null;
        }
        int position = spinner.getSelectedItemPosition();
        if (position < 0 || position >= adapter.getCount()) {
            return null;
        }
        return adapter.getValue(position);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_category;
    }

    private static class State {
        int source;
        int page;
        int state;
    }


}
