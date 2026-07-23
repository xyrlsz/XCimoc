package com.xyrlsz.xcimocob.presenter;

import com.xyrlsz.xcimocob.core.Download;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.manager.TaskManager;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.model.Task;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.rx.ToAnotherList;
import com.xyrlsz.xcimocob.ui.view.TaskView;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.disposables.Disposable;


/**
 * Created by Hiroshi on 2016/9/7.
 */
public class TaskPresenter extends BasePresenter<TaskView> {
    private TaskManager mTaskManager;
    private ComicManager mComicManager;
    private SourceManager mSourceManager;
    private Comic mComic;

    @Override
    protected void onViewAttach() {
        mTaskManager    = TaskManager.getInstance(mBaseView);
        mComicManager   = ComicManager.getInstance(mBaseView);
        mSourceManager  = SourceManager.getInstance(mBaseView);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void initSubscription() {
        addSubscription(RxEvent.EVENT_TASK_STATE_CHANGE, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                long id = (long) rxEvent.getData(1);
                switch ((int) rxEvent.getData()) {
                    case Task.STATE_PARSE:
                        mBaseView.onTaskParse(id);
                        break;
                    case Task.STATE_ERROR:
                        mBaseView.onTaskError(id);
                        break;
                    case Task.STATE_PAUSE:
                        mBaseView.onTaskPause(id);
                        break;
                }
            }
        });
        addSubscription(RxEvent.EVENT_TASK_PROCESS, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                long id = (long) rxEvent.getData();
                mBaseView.onTaskProcess(id, (int) rxEvent.getData(1), (int) rxEvent.getData(2));
            }
        });
        addSubscription(RxEvent.EVENT_TASK_INSERT, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                List<Task> list = (List<Task>) rxEvent.getData(1);
                Task task       = list.get(0);
                if (task.getKey() == mComic.getId()) {
                    mBaseView.onTaskAdd(list);
                }
            }
        });
        addSubscription(RxEvent.EVENT_COMIC_UPDATE, new Consumer<RxEvent>() {
            @Override
            public void accept(RxEvent rxEvent) {
                if (mComic.getId() > 0 && mComic.getId() == (long) rxEvent.getData()) {
                    Comic comic = mComicManager.load(mComic.getId());
                    mComic.setPage(comic.getPage());
                    mComic.setLast(comic.getLast());
                    mBaseView.onLastChange(mComic.getLast());
                }
            }
        });
    }

    public Comic getComic() {
        return mComic;
    }

    private void updateTaskList(List<Task> list) {
        for (Task task : list) {
            int state = task.isFinish() ? Task.STATE_FINISH : Task.STATE_PAUSE;
            task.setCid(mComic.getCid());
            task.setSource(mComic.getSource());
            task.setState(state);
        }
    }

    public void load(long id, final boolean asc) {
        mComic = mComicManager.load(id);
        Disposable disposable = mTaskManager.listInRx(id)
                .doOnNext(new Consumer<List<Task>>() {
                    @Override
                    public void accept(List<Task> list) {
                        updateTaskList(list);
                        if (!mComic.getLocal()) {
                            final List<String> sList = Download.getComicIndex(
                                mBaseView.getAppInstance().getContentResolver(),
                                mBaseView.getAppInstance().getDocumentFile(), mComic,
                                mSourceManager.getParser(mComic.getSource()).getTitle());
                            if (sList != null) {
                                Collections.sort(list, new Comparator<Task>() {
                                    @Override
                                    public int compare(Task lhs, Task rhs) {
                                        return asc ? sList.indexOf(rhs.getPath())
                                                - sList.indexOf(lhs.getPath())
                                                   : sList.indexOf(lhs.getPath())
                                                - sList.indexOf(rhs.getPath());
                                    }
                                });
                            }
                        } else {
                            Collections.sort(list, new Comparator<Task>() {
                                @Override
                                public int compare(Task lhs, Task rhs) {
                                    return asc ? lhs.getTitle().compareTo(rhs.getTitle())
                                               : rhs.getTitle().compareTo(lhs.getTitle());
                                }
                            });
                        }
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    new Consumer<List<Task>>() {
                        @Override
                        public void accept(List<Task> list) {
                            mBaseView.onTaskLoadSuccess(list, mComic.getLocal());
                        }
                    },
                    new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) {
                            mBaseView.onTaskLoadFail();
                        }
                    });
        mCompositeSubscription.add(disposable);
    }

    public void deleteTask(List<Chapter> list, final boolean isEmpty) {
        final long id = mComic.getId();
        Disposable disposable = Observable.just(list)
                .subscribeOn(Schedulers.io())
                .doOnNext(new Consumer<List<Chapter>>() {
                    @Override
                    public void accept(List<Chapter> list) {
                        deleteFromDatabase(list, isEmpty);
                        if (!mComic.getLocal()) {
                            if (isEmpty) {
                                Download.delete(mBaseView.getAppInstance().getDocumentFile(),
                                    mComic,
                                    mSourceManager.getParser(mComic.getSource()).getTitle());
                            } else {
                                Download.delete(mBaseView.getAppInstance().getDocumentFile(),
                                    mComic, list,
                                    mSourceManager.getParser(mComic.getSource()).getTitle());
                            }
                        }
                    }
                })
                .compose(new ToAnotherList<>(new Function<Chapter, Long>() {
                    @Override
                    public Long apply(Chapter chapter) {
                        return chapter.getTid();
                    }
                }))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    new Consumer<List<Long>>() {
                        @Override
                        public void accept(List<Long> list) {
                            if (isEmpty) {
                                RxBus.getInstance().post(
                                    new RxEvent(RxEvent.EVENT_DOWNLOAD_REMOVE, id));
                            }
                            mBaseView.onTaskDeleteSuccess(list);
                        }
                    },
                    new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) {
                            mBaseView.onTaskDeleteFail();
                        }
                    });
        mCompositeSubscription.add(disposable);
    }

    private void deleteFromDatabase(final List<Chapter> list, final boolean isEmpty) {
        mComicManager.runInTx(new Runnable() {
            @Override
            public void run() {
                for (Chapter chapter : list) {
                    mTaskManager.delete(chapter.getTid());
                }
                if (isEmpty) {
                    mComic.setDownload(null);
                    mComicManager.updateOrDelete(mComic);
                    Download.delete(mBaseView.getAppInstance().getDocumentFile(), mComic,
                        mSourceManager.getParser(mComic.getSource()).getTitle());
                }
            }
        });
    }

    public long updateLast(String path) {
        if (mComic.getFavorite() != null) {
            mComic.setFavorite(System.currentTimeMillis());
        }
        mComic.setHistory(System.currentTimeMillis());
        if (!path.equals(mComic.getLast())) {
            mComic.setLast(path);
            mComic.setPage(1);
        }
        mComicManager.update(mComic);
        RxBus.getInstance().post(
            new RxEvent(RxEvent.EVENT_COMIC_READ, new MiniComic(mComic), false));
        return mComic.getId();
    }
}