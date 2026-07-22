package com.xyrlsz.xcimocob.manager;

import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.model.SearchHistory;
import com.xyrlsz.xcimocob.model.SearchHistory_;

import java.util.List;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 搜索历史记录管理器
 * Created by XCimoc on 2026/07/22.
 */
public class SearchHistoryManager {

    private static volatile SearchHistoryManager mInstance;

    private final Box<SearchHistory> mHistoryBox;

    private SearchHistoryManager(AppGetter getter) {
        BoxStore boxStore = getter.getAppInstance().getBoxStore();
        mHistoryBox = boxStore.boxFor(SearchHistory.class);
    }

    public static SearchHistoryManager getInstance(AppGetter getter) {
        if (mInstance == null) {
            synchronized (SearchHistoryManager.class) {
                if (mInstance == null) {
                    mInstance = new SearchHistoryManager(getter);
                }
            }
        }
        return mInstance;
    }

    /**
     * 查询所有搜索历史，按时间倒序排列（同步）
     */
    public List<SearchHistory> list() {
        return mHistoryBox.query()
                .orderDesc(SearchHistory_.timestamp)
                .build()
                .find();
    }

    public List<SearchHistory> list(long offset, long count) {
        return mHistoryBox.query()
                .orderDesc(SearchHistory_.timestamp)
                .build()
                .find(offset, count);
    }

    /**
     * 查询所有搜索历史，按时间倒序排列（Rx）
     */
    public Observable<List<SearchHistory>> listInRx() {
        return Observable.fromCallable(this::list)
                .subscribeOn(Schedulers.io());
    }

    public Observable<List<SearchHistory>> listInRx(long offset, long count) {
        return Observable.fromCallable(() -> list(offset, count))
                .subscribeOn(Schedulers.io());
    }

    /**
     * 插入或更新搜索历史（若已存在相同关键词则更新时间戳）
     */
    public void insertOrUpdate(String keyword) {
        SearchHistory existing = mHistoryBox.query(
                SearchHistory_.keyword.equal(keyword)
        ).build().findFirst();
        if (existing != null) {
            existing.setTimestamp(System.currentTimeMillis());
            mHistoryBox.put(existing);
        } else {
            SearchHistory history = new SearchHistory(0, keyword, System.currentTimeMillis());
            mHistoryBox.put(history);
        }
    }

    /**
     * 删除单条搜索历史
     */
    public void delete(SearchHistory history) {
        mHistoryBox.remove(history);
    }

    /**
     * 清空所有搜索历史
     */
    public void clearAll() {
        mHistoryBox.removeAll();
    }
}
