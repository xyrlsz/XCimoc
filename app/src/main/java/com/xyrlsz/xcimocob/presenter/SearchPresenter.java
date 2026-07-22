package com.xyrlsz.xcimocob.presenter;

import com.xyrlsz.xcimocob.core.Manga;
import com.xyrlsz.xcimocob.manager.SearchHistoryManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.SearchHistory;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.ui.view.SearchView;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * Created by Hiroshi on 2016/10/11.
 */

public class SearchPresenter extends BasePresenter<SearchView> {

    private SourceManager mSourceManager;
    private SearchHistoryManager mSearchHistoryManager;

    @Override
    protected void onViewAttach() {
        mSourceManager = SourceManager.getInstance(mBaseView);
        mSearchHistoryManager = SearchHistoryManager.getInstance(mBaseView);
    }

    public void loadSource() {
        mCompositeSubscription.add(mSourceManager.listEnableInRx()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<Source>>() {
                    @Override
                    public void accept(List<Source> list) {
                        mBaseView.onSourceLoadSuccess(list);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        mBaseView.onSourceLoadFail();
                    }
                }));
    }

    public void loadAutoComplete(String keyword) {
        mCompositeSubscription.add(Manga.loadAutoComplete(keyword)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<String>>() {
                    @Override
                    public void accept(List<String> list) {
                        mBaseView.onAutoCompleteLoadSuccess(list);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                }));
    }

    public void loadSearchHistory() {
        mCompositeSubscription.add(mSearchHistoryManager.listInRx(0, 20)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<SearchHistory>>() {
                    @Override
                    public void accept(List<SearchHistory> list) {
                        if (mBaseView != null) {
                            mBaseView.onSearchHistoryLoadSuccess(list);
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                }));
    }

    public void saveSearchHistory(String keyword) {
        mSearchHistoryManager.insertOrUpdate(keyword);
    }

    public void deleteSearchHistory(SearchHistory history) {
        mSearchHistoryManager.delete(history);
        if (mBaseView != null) {
            mBaseView.onSearchHistoryDeleteSuccess();
        }
    }

    public void clearSearchHistory() {
        mSearchHistoryManager.clearAll();
        if (mBaseView != null) {
            mBaseView.onSearchHistoryClearSuccess();
        }
    }

}
