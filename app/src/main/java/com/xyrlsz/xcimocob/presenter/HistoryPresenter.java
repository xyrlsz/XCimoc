package com.xyrlsz.xcimocob.presenter;

import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.network.sync.DataSyncManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.rx.ToAnotherList;
import com.xyrlsz.xcimocob.ui.view.HistoryView;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Hiroshi on 2016/7/18.
 */
public class HistoryPresenter extends BasePresenter<HistoryView> {

    private ComicManager mComicManager;

    @Override
    protected void onViewAttach() {
        mComicManager = ComicManager.getInstance(mBaseView);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void initSubscription() {
        super.initSubscription();
        addSubscription(RxEvent.EVENT_COMIC_READ, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                mBaseView.onItemUpdate((MiniComic) rxEvent.getData());
            }
        });
        addSubscription(RxEvent.EVENT_COMIC_HISTORY_RESTORE, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                mBaseView.OnComicRestore((List<Object>) rxEvent.getData());
            }
        });
    }

    public Comic load(long id) {
        return mComicManager.load(id);
    }

    public void load() {
        mCompositeSubscription.add(mComicManager.listHistoryInRx()
                .compose(new ToAnotherList<>(new Function<Comic, Object>() {
                    @Override
                    public MiniComic apply(Comic comic) {
                        return new MiniComic(comic);
                    }
                }))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<Object>>() {
                    @Override
                    public void accept(List<Object> list) {
                        mBaseView.onComicLoadSuccess(list);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        mBaseView.onComicLoadFail();
                    }
                }));
    }

    public void delete(final long id) {
        // 先在主线程获取 source/cid（异步删除后漫画可能已被删除）
        final Comic preLoad = mComicManager.load(id);
        final int source = preLoad != null ? preLoad.getSource() : 0;
        final String cid = preLoad != null ? preLoad.getCid() : null;

        mCompositeSubscription.add(Observable.just(id)
                .doOnNext(new Consumer<Long>() {
                    @Override
                    public void accept(Long id1) {
                        Comic comic = mComicManager.load(id1);
                        if (comic != null) {
                            comic.setHistory(null);
                            mComicManager.updateOrDelete(comic);
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long id2) {
                        // 标记此漫画历史已删除，防止下载同步时被恢复
                        if (cid != null) {
                            DataSyncManager.markHistoryDeleted(source, cid);
                        }
                        mBaseView.onHistoryDelete(id2);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        mBaseView.onExecuteFail();
                    }
                }));
    }

    public void clear() {
        mCompositeSubscription.add(mComicManager.listHistoryInRx()
                .doOnNext(new Consumer<List<Comic>>() {
                    @Override
                    public void accept(final List<Comic> list) {
                        mComicManager.runInTx(new Runnable() {
                            @Override
                            public void run() {
                                for (Comic comic : list) {
                                    comic.setHistory(null);
                                    mComicManager.updateOrDelete(comic);
                                }
                            }
                        });
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<Comic>>() {
                    @Override
                    public void accept(List<Comic> list) {
                        // 标记所有被清除历史的漫画，防止下载同步时被恢复
                        DataSyncManager.markHistoryDeleted(list);
                        mBaseView.onHistoryClearSuccess();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        mBaseView.onExecuteFail();
                    }
                }));
    }

}
