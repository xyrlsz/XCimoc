package com.xyrlsz.xcimocob.manager;

import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.Comic_;
import com.xyrlsz.xcimocob.model.TagRef;
import com.xyrlsz.xcimocob.model.TagRef_;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.exception.UniqueViolationException;
import io.objectbox.query.QueryBuilder;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by Hiroshi on 2016/7/9.
 */
public class ComicManager {
    public static int RESULT_DELETE = 0;
    public static int RESULT_UPDATE = 1;
    private static volatile ComicManager mInstance;
    private final Box<Comic> mComicBox;
    private final Box<TagRef> mTagRefBox;

    private ComicManager(AppGetter getter) {
        BoxStore boxStore = getter.getAppInstance().getBoxStore();
        mComicBox = boxStore.boxFor(Comic.class);
        mTagRefBox = boxStore.boxFor(TagRef.class);
    }

    public static ComicManager getInstance(AppGetter getter) {
        if (mInstance == null) {
            synchronized (ComicManager.class) {
                if (mInstance == null) {
                    mInstance = new ComicManager(getter);
                }
            }
        }
        return mInstance;
    }

    public void runInTx(Runnable runnable) {
        mComicBox.getStore().runInTx(runnable);
    }

    public <T> T callInTx(Callable<T> callable) throws Exception {
        return mComicBox.getStore().callInTx(callable);
    }

    public List<Comic> listDownload() {
        return mComicBox.query().notNull(Comic_.download).build().find();
    }

    public List<Comic> listLocal() {
        return mComicBox.query().equal(Comic_.local, true).build().find();
    }

    public Observable<List<Comic>> listLocalInRx() {
        return Observable.fromCallable(this::listLocal);
    }

    public Observable<List<Comic>> listFavoriteOrHistoryInRx() {
        return Observable.fromCallable(this::listFavoriteOrHistory);
    }

    public List<Comic> listFavoriteOrHistory() {
        QueryBuilder<Comic> queryBuilder =
                mComicBox.query(Comic_.favorite.notNull().or(Comic_.history.notNull()));
        return queryBuilder.build().find();
    }

    public List<Comic> listFavorite() {
        return mComicBox.query(Comic_.favorite.notNull()).build().find();
    }

    public Observable<List<Comic>> listFavoriteInRx() {
        return Observable.fromCallable(()
                -> mComicBox.query(Comic_.favorite.notNull())
                .orderDesc(Comic_.highlight)
                .orderDesc(Comic_.favorite)
                .build()
                .find());
    }

    public Observable<List<Comic>> listFinishInRx() {
        return Observable.fromCallable(() -> {
            return mComicBox.query(Comic_.favorite.notNull())
                    .equal(Comic_.finish, true)
                    .orderDesc(Comic_.highlight)
                    .orderDesc(Comic_.favorite)
                    .build()
                    .find();
        });
    }

    public Observable<List<Comic>> listContinueInRx() {
        return Observable.fromCallable(()
                -> mComicBox.query(Comic_.favorite.notNull())
                .notEqual(Comic_.finish, true)
                .orderDesc(Comic_.highlight)
                .orderDesc(Comic_.favorite)
                .build()
                .find());
    }

    public Observable<List<Comic>> listHistoryInRx() {
        return Observable.fromCallable(()
                -> mComicBox.query(Comic_.history.notNull())
                .orderDesc(Comic_.history)
                .build()
                .find());
    }

    public Observable<List<Comic>> listDownloadInRx() {
        return Observable.fromCallable(() -> {
            return mComicBox.query()
                    .notNull(Comic_.download)
                    .orderDesc(Comic_.download)
                    .build()
                    .find();
        });
    }

    public Observable<List<Comic>> listFavoriteByTag(long id) {
        return Observable.fromCallable(() -> {
            // 直接获取 ID 数组
            Long[] cids = mTagRefBox.query()
                    .equal(TagRef_.tid, id)
                    .build()
                    .find() // 获取 List<TagRef>
                    .stream()
                    .map(TagRef::getCid) // 转换为 Stream<Long>
                    .toArray(Long[]::new); // 转换为 Long[]

            // 如果没有数据，直接返回空列表
            if (cids.length == 0) {
                return new ArrayList<>();
            }
            long[] cidsLong = new long[cids.length];
            for (int i = 0; i < cids.length; i++) {
                cidsLong[i] = cids[i];
            }
            // 查询 Comic
            return mComicBox.query(Comic_.favorite.notNull())
                    .in(Comic_.id, cidsLong)
                    .orderDesc(Comic_.highlight)
                    .orderDesc(Comic_.favorite)
                    .build()
                    .find();
        });
    }

    public Observable<List<Comic>> listFavoriteNotIn(Collection<Long> collections) {
        return Observable.fromCallable(() -> {
            long[] collectionsLong = new long[collections.size()];
            int i = 0;
            for (Long cid : collections) {
                collectionsLong[i] = cid;
                i++;
            }
            return mComicBox.query(Comic_.favorite.notNull())
                    .notIn(Comic_.id, collectionsLong)
                    .build()
                    .find();
        });
    }

    public long countBySource(int type) {
        return mComicBox.query(Comic_.favorite.notNull())
                .equal(Comic_.source, type)
                .build()
                .count();
    }

    public Comic load(long id) {
        return mComicBox.get(id);
    }

    public Comic load(int source, String cid) {
        List<Comic> list =
                mComicBox.query(Comic_.cid.equal(cid)).equal(Comic_.source, source)
                        .order(Comic_.id)
                        .build()
                        .find(0, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    public Comic loadOrCreate(int source, String cid) {
        Comic comic = load(source, cid);
        return comic == null ? new Comic(source, cid) : comic;
    }

    public Observable<Comic> loadLast() {
        return Observable.defer(() -> {
            List<Comic> list =
                    mComicBox.query(Comic_.history.notNull()).orderDesc(Comic_.history).build().find();
            return list.isEmpty() ? Observable.empty() : Observable.just(list.get(0));
        });
    }

    public void cancelHighlight() {
        List<Comic> comics = mComicBox.query().equal(Comic_.highlight, true).build().find();
        for (Comic comic : comics) {
            comic.setHighlight(false);
        }
        if (!comics.isEmpty()) {
            mComicBox.put(comics);
        }
    }

    public void updateOrInsert(Comic comic) {
        runInTx(() -> {
            Comic existing = load(comic.getSource(), comic.getCid());

            if (existing != null) {
                comic.setId(existing.getId());
            }
            try {
                mComicBox.put(comic);
            } catch (UniqueViolationException ignored) {

            }
        });
    }

    public void update(Comic comic) {
        updateOrInsert(comic);
    }

    public void delete(Comic comic) {
        mComicBox.remove(comic);
        comic.setId(0);
    }

    public int updateOrDelete(Comic comic) {
        if (comic.getFavorite() == null && comic.getHistory() == null && comic.getDownload() == null
                && !comic.getLocal()) {
            delete(comic);
            return RESULT_DELETE;
        } else {
            update(comic);
            return RESULT_UPDATE;
        }
    }

    public void deleteByKey(long key) {
        mComicBox.remove(key);
    }

    public void insert(Comic comic) {
        updateOrInsert(comic);
    }
}
