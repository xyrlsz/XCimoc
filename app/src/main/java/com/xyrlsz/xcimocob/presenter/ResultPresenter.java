package com.xyrlsz.xcimocob.presenter;

import static com.xyrlsz.xcimocob.ui.activity.SearchActivity.SEARCH_TITLE;

import com.xyrlsz.xcimocob.core.Manga;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.parser.Parser;
import com.xyrlsz.xcimocob.ui.view.ResultView;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Action;

/**
 * Created by Hiroshi on 2016/7/4.
 */
public class ResultPresenter extends BasePresenter<ResultView> {

    private static final int STATE_NULL = 0;
    private static final int STATE_DOING = 1;
    private static final int STATE_DONE = 3;
    private final boolean strictSearch;
    private SourceManager mSourceManager;
    private State[] mStateArray;
    private String keyword;
    private boolean stSameSearch;
    private int searchType = SEARCH_TITLE;
    private int error = 0;
    private String keywordTemp;
    private String comicTitleTemp = "";

    public ResultPresenter(int[] source, String keyword, boolean strictSearch) {
        this.keyword = keyword;
        this.strictSearch = strictSearch;
        if (source != null) {
            initStateArray(source);
        }
    }

    public ResultPresenter(int[] source, String keyword, boolean strictSearch, boolean stSameSearch) {
        this.keyword = keyword;
        this.strictSearch = strictSearch;
        this.stSameSearch = stSameSearch;
        if (source != null) {
            initStateArray(source);
        }
    }

    public ResultPresenter(int[] source, String keyword, boolean strictSearch, boolean stSameSearch, int searchType) {
        this.keyword = keyword;
        this.strictSearch = strictSearch;
        this.stSameSearch = stSameSearch;
        this.searchType = searchType;
        if (source != null) {
            initStateArray(source);
        }
    }

    @Override
    protected void onViewAttach() {
        mSourceManager = SourceManager.getInstance(mBaseView);
        if (mStateArray == null) {
            initStateArray(loadSource());
        }
    }

    private void initStateArray(int[] source) {
        mStateArray = new State[source.length];
        for (int i = 0; i != mStateArray.length; ++i) {
            mStateArray[i] = new State();
            mStateArray[i].source = source[i];
            mStateArray[i].page = 0;
            mStateArray[i].state = STATE_NULL;
        }
    }

    private int[] loadSource() {
        List<Source> list = mSourceManager.listEnable();
        int[] source = new int[list.size()];
        for (int i = 0; i != source.length; ++i) {
            source[i] = list.get(i).getType();
        }
        return source;
    }

    public void loadCategory() {
        if (mStateArray[0].state == STATE_NULL) {
            Parser parser = mSourceManager.getParser(mStateArray[0].source);
            mStateArray[0].state = STATE_DOING;

            //修复扑飞漫画分类查看
//            if (mStateArray[0].page == 0) {
//                if (parser.getTitle().equals("扑飞漫画")) {
//                    keywordTemp = keyword;
//                    keyword = keyword.replace("_%d", "");
//                }
//            } else {
//                if (parser.getTitle().equals("扑飞漫画")) {
//                    keyword = keywordTemp;
//                }
//            }
            mCompositeSubscription.add(Manga.getCategoryComic(parser, keyword, ++mStateArray[0].page)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<List<Comic>>() {
                        @Override
                        public void accept(List<Comic> list) {

                            //修复扑飞漫画分类查看时的重复加载列表问题
                            if (!comicTitleTemp.isEmpty() && comicTitleTemp.equals(list.get(0).getTitle())) {
                                list.clear();
                            }
                            comicTitleTemp = list.get(0).getTitle();

                            mBaseView.onLoadSuccess(list);
                            mStateArray[0].state = STATE_NULL;
                        }
                    }, new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) {
                            throwable.printStackTrace();
                            if (mStateArray[0].page == 1) {
                                mBaseView.onLoadFail();
                            }
                        }
                    }));
        }
    }

    public void loadSearch() {
        if (mStateArray.length == 0) {
            mBaseView.onSearchError();
            return;
        }
        for (final State obj : mStateArray) {
            if (obj.state == STATE_NULL) {
                MangaParser parser = mSourceManager.getParser(obj.source);
                obj.state = STATE_DOING;
                mCompositeSubscription.add(Manga.getSearchResult(parser, keyword, ++obj.page, strictSearch, stSameSearch, searchType)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Consumer<Comic>() {
                            @Override
                            public void accept(Comic comic) {
                                mBaseView.onSearchSuccess(comic);
                            }
                        }, new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) {
                                throwable.printStackTrace();
                                if (obj.page == 1) {
                                    obj.state = STATE_DONE;
                                    if (++error == mStateArray.length) {
                                        mBaseView.onSearchError();
                                    }
                                }
                            }
                        }, new Action() {
                            @Override
                            public void run() {
                                obj.state = STATE_NULL;
                            }
                        }));
            }
        }
    }

    /**
     * 下拉刷新：重置状态后重新从网络获取
     */
    public void refresh() {
        if (mStateArray == null) {
            return;
        }
        // 跳过 OkHttp HTTP 缓存
        Manga.setForceRefresh(true);
        // 重置所有状态
        for (State state : mStateArray) {
            state.page = 0;
            state.state = STATE_NULL;
        }
        error = 0;
        comicTitleTemp = "";
    }

    private static class State {
        int source;
        int page;
        int state;
    }

}
