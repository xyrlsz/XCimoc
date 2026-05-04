package com.xyrlsz.xcimocob.presenter;

import androidx.collection.LongSparseArray;

import com.xyrlsz.xcimocob.core.Download;
import com.xyrlsz.xcimocob.manager.ChapterManager;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.manager.TaskManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.model.Task;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.rx.ToAnotherList;
import com.xyrlsz.xcimocob.ui.view.DownloadView;
import com.xyrlsz.xcimocob.utils.ComicUtils;
import com.xyrlsz.xcimocob.utils.IdCreator;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Hiroshi on 2016/9/1.
 */
public class DownloadPresenter extends BasePresenter<DownloadView> {

    private ComicManager mComicManager;
    private TaskManager mTaskManager;
    private SourceManager mSourceManager;
    private ChapterManager mChapterManager;

    @Override
    protected void onViewAttach() {
        mComicManager = ComicManager.getInstance(mBaseView);
        mTaskManager = TaskManager.getInstance(mBaseView);
        mSourceManager = SourceManager.getInstance(mBaseView);
        mChapterManager = ChapterManager.getInstance(mBaseView);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void initSubscription() {
        addSubscription(RxEvent.EVENT_TASK_INSERT, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                mBaseView.onDownloadAdd((MiniComic) rxEvent.getData());
            }
        });
        addSubscription(RxEvent.EVENT_DOWNLOAD_REMOVE, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                mBaseView.onDownloadDelete((long) rxEvent.getData());
            }
        });
        addSubscription(RxEvent.EVENT_DOWNLOAD_CLEAR, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                for (long id : (List<Long>) rxEvent.getData()) {
                    mBaseView.onDownloadDelete(id);
                }
            }
        });
        addSubscription(RxEvent.EVENT_DOWNLOAD_START, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                mBaseView.onDownloadStart();
            }
        });
        addSubscription(RxEvent.EVENT_DOWNLOAD_STOP, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                mBaseView.onDownloadStop();
            }
        });
        addSubscription(RxEvent.EVENT_EXPORT_RESULT, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                boolean success = (boolean) rxEvent.getData(0);
                String message = (String) rxEvent.getData(1);
                mBaseView.onExportResult(success, message);
            }
        });
    }

    public void deleteComic(long id) {
        mCompositeSubscription.add(Observable.just(id)
                .doOnNext(new Consumer<Long>() {
                    @Override
                    public void accept(final Long id) throws Exception {
                        Comic comic = mComicManager.callInTx(() -> {
                            Comic comic1 = mComicManager.load(id);
                            mTaskManager.deleteByComicId(id);
                            comic1.setDownload(null);
                            int res = mComicManager.updateOrDelete(comic1);
                            if (res == ComicManager.RESULT_DELETE) {
                                mChapterManager.deleteBySourceComic(IdCreator.createSourceComic(comic1));
                            }
                            return comic1;
                        });
                        Download.delete(mBaseView.getAppInstance().getDocumentFile(), comic, mSourceManager.getParser(comic.getSource()).getTitle());
                    }
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(id1 -> mBaseView.onDownloadDeleteSuccess(id1), throwable -> mBaseView.onExecuteFail()));
    }

    public Comic load(long id) {
        return mComicManager.load(id);
    }

    public void load() {
        mCompositeSubscription.add(mComicManager.listDownloadInRx()
                .compose(new ToAnotherList<>((io.reactivex.rxjava3.functions.Function<Comic, Object>) MiniComic::new))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> mBaseView.onComicLoadSuccess(list), throwable -> mBaseView.onComicLoadFail()));
    }

    public void loadTask() {
        mCompositeSubscription.add(mTaskManager.listInRx()
                .flatMap(new Function<List<Task>, Observable<Task>>() {
                    @Override
                    public Observable<Task> apply(List<Task> list) {
                        return Observable.fromIterable(list);
                    }
                })
                .filter(new io.reactivex.rxjava3.functions.Predicate<Task>() {
                    @Override
                    public boolean test(Task task) {
                        return !task.isFinish();
                    }
                })
                .toList()
                .doOnSuccess(new Consumer<List<Task>>() {
                    @Override
                    public void accept(List<Task> list) {
                        LongSparseArray<Comic> array = ComicUtils.buildComicMap(mComicManager.listDownload());
                        for (Task task : list) {
                            Comic comic = array.get(task.getKey());
                            if (comic != null) {
                                task.setSource(comic.getSource());
                                task.setCid(comic.getCid());
                            }
                            task.setState(Task.STATE_WAIT);
                        }
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<Task>>() {
                    @Override
                    public void accept(List<Task> list) {
                        mBaseView.onTaskLoadSuccess(new ArrayList<>(list));
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        mBaseView.onExecuteFail();
                    }
                }));
    }

}
