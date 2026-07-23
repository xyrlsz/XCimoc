package com.xyrlsz.xcimocob.presenter;

import android.util.Log;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.core.Backup;
import com.xyrlsz.xcimocob.core.Download;
import com.xyrlsz.xcimocob.core.Manga;
import com.xyrlsz.xcimocob.manager.ChapterManager;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.manager.TagRefManager;
import com.xyrlsz.xcimocob.manager.TaskManager;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.model.Task;
import com.xyrlsz.xcimocob.network.sync.DataSyncManager;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;
import com.xyrlsz.xcimocob.ui.view.DetailView;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.ThreadRunUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Hiroshi on 2016/7/4.
 */
public class DetailPresenter extends BasePresenter<DetailView> {
    private ComicManager mComicManager;
    private ChapterManager mChapterManager;
    private TaskManager mTaskManager;
    private TagRefManager mTagRefManager;
    private SourceManager mSourceManager;
    private Comic mComic;

    @Override
    protected void onViewAttach() {
        mComicManager = ComicManager.getInstance(mBaseView);
        mChapterManager = ChapterManager.getInstance(mBaseView);
        mTaskManager = TaskManager.getInstance(mBaseView);
        mTagRefManager = TagRefManager.getInstance(mBaseView);
        mSourceManager = SourceManager.getInstance(mBaseView);
    }

    @Override
    protected void initSubscription() {
        addSubscription(RxEvent.EVENT_COMIC_UPDATE, rxEvent -> {
            if (mBaseView != null && mComic.getId() > 0 && mComic.getId() == (long) rxEvent.getData()) {
                Comic comic = mComicManager.load(mComic.getId());
                mComic.setPage(comic.getPage());
                mComic.setLast(comic.getLast());
                mComic.setChapter(comic.getChapter());
                mBaseView.onLastChange(mComic.getLast());
            }
        });
        addSubscription(RxEvent.EVENT_COMIC_UPDATE_INFO, rxEvent -> {
            Comic eventComic = (Comic) rxEvent.getData();
            // 必须增加 ID 或 Source+Cid 的双重判断！
            if (mComic != null && mComic.getSource() == eventComic.getSource()
                    && mComic.getCid().equals(eventComic.getCid())) {
                mComicManager.updateOrInsert(eventComic);
                // 尽量不要直接重新赋值 mComic，而是更新其属性
                mComic.copyFrom(eventComic);
                // 信息解析完成后立即更新 UI 头信息（封面、标题等），不必等章节也加载完
                ThreadRunUtils.runOnMainThread(() -> {
                    if (mBaseView != null) {
                        mBaseView.onComicLoadSuccess(mComic);
                    }
                });
            }
        });
        // 监听收藏事件
        addSubscription(RxEvent.EVENT_COMIC_FAVORITE, rxEvent -> {
            MiniComic miniComic = (MiniComic) rxEvent.getData();
            if (mBaseView != null && mComic != null && mComic.getSource() == miniComic.getSource()
                    && mComic.getCid().equals(miniComic.getCid())) {
                // 重新从数据库加载最新状态，确保 ID 和收藏状态同步
                mComic = mComicManager.load(miniComic.getSource(), miniComic.getCid());
                mBaseView.onComicLoadSuccess(mComic); // 触发 Activity 更新图标
            }
        });

        // 监听取消收藏事件
        addSubscription(RxEvent.EVENT_COMIC_UNFAVORITE, rxEvent -> {
            long id = (long) rxEvent.getData();
            if (mBaseView != null && mComic != null && mComic.getId() > 0 && mComic.getId() == id) {
                // 更新本地内存状态
                mComic.setFavorite(null);
                mBaseView.onComicLoadSuccess(mComic); // 触发 Activity 更新图标
            }
        });

        // 监听下载任务添加事件，刷新对应章节的下载状态
        addSubscription(RxEvent.EVENT_TASK_INSERT, rxEvent -> {
            if (mBaseView == null) return;
            MiniComic miniComic = (MiniComic) rxEvent.getData();
            List<Task> list = TaskManager.getInstance(mBaseView).list(miniComic.getId());
            if (mComic != null && mComic.getId() > 0 && mComic.getId() == miniComic.getId()) {
                refreshChapterDownloadStatus(list);
            }
        });

    }

    private void refreshChapterDownloadStatus(List<Task> tasks) {
        if (mComic == null || mComic.getId() == 0) {
            return;
        }
        mCompositeSubscription.add(
                mChapterManager.getListChapter(IdCreator.createSourceComic(mComic))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                list -> {
                                    for (Chapter chapter : list) {
                                        for (Task task : tasks) {
                                            if (chapter.getId() == task.getId()) {
                                                chapter.setDownload(true);
                                                chapter.setComplete(task.isFinish());
                                                chapter.setTid(task.getId());
                                            }
                                        }
                                    }
                                    if (mBaseView != null) {
                                        mBaseView.onChapterDownloadStatusChanged(list);
                                    }
                                },
                                throwable -> {
                                    // ignore
                                }));

    }

    public void checkDatabaseStatus() {
        if (mComic != null && mBaseView != null) {
            Comic latest = mComicManager.load(mComic.getSource(), mComic.getCid());
            if (latest != null) {
                mComic = latest;
                mBaseView.onComicLoadSuccess(mComic);
            }
        }
    }

    public void load(long id, int source, String cid) {
        if (id == -1) {
            mComic = mComicManager.loadOrCreate(source, cid);
        } else {
            mComic = mComicManager.load(id);
        }
        cancelHighlight();
        preLoad();
        load();
    }

    /**
     * 下拉刷新：清除缓存后重新从网络加载
     */
    public void refresh() {
        if (mComic == null || mBaseView == null) {
            return;
        }
        // 跳过 OkHttp HTTP 缓存
        Manga.setForceRefresh(true);
        // 重新发起网络请求
        load();
    }

    private void updateChapterList(List<Chapter> list) {
        Map<String, Task> map = new HashMap<>();
        for (Task task : mTaskManager.list(mComic.getId())) {
            map.put(task.getPath(), task);
        }
        if (!map.isEmpty()) {
            for (Chapter chapter : list) {
                Task task = map.get(chapter.getPath());
                if (task != null) {
                    chapter.setDownload(true);
                    chapter.setCount(task.getProgress());
                    chapter.setComplete(task.isFinish());
                    mChapterManager.update(chapter);
                }
            }
        }
        mComic.setChapterCount(list.size());
        mComicManager.update(mComic);
    }

    public void preLoad() {
        if (mComic.getId() == 0) {
            return;
        }
        mCompositeSubscription.add(
                mChapterManager.getListChapter(IdCreator.createSourceComic(mComic))
                        .doOnNext(new Consumer<List<Chapter>>() {
                            @Override
                            public void accept(List<Chapter> list) {
                                if (mComic.getId() > 0 && !list.isEmpty()) {
                                    updateChapterList(list);
                                }
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                new Consumer<List<Chapter>>() {
                                    @Override
                                    public void accept(List<Chapter> list) {
                                        if (list != null && !list.isEmpty() && mBaseView != null) {
                                            mBaseView.onPreLoadSuccess(list, mComic);
                                        }
                                    }
                                },
                                new Consumer<Throwable>() {
                                    @Override
                                    public void accept(Throwable throwable) {
                                        if (mBaseView != null) {
                                            mBaseView.onComicLoadSuccess(mComic);
                                            mBaseView.onParseError();
                                        }
                                    }
                                }));
    }

    private void load() {
        // 网络请求 Observable（共享，避免重复请求）
        Observable<List<Chapter>> networkObs = Manga.getComicInfo(mSourceManager.getParser(mComic.getSource()), mComic)
                .doOnNext(new Consumer<List<Chapter>>() {
                    @Override
                    public void accept(List<Chapter> list) {
                        // 数据库写入在 IO 线程完成，不阻塞主线程
                        if (mComic.getId() > 0) {
                            Long sourceComic = IdCreator.createSourceComic(mComic);
                            for (Chapter chapter : list) {
                                chapter.setSourceComic(sourceComic);
                            }
                        }
                        mChapterManager.updateOrInsert(list);
                        if (mComic.getId() > 0) {
                            updateChapterList(list);
                        }
                    }
                })
                .share();

        // 超时降级：8 秒后网络未返回，用数据库缓存数据先展示 UI
        // takeUntil(networkObs) 保证网络先返回时，缓存降级不会覆盖新数据
        Observable<List<Chapter>> cacheObs = Observable.timer(8, TimeUnit.SECONDS)
                .flatMap(new io.reactivex.rxjava3.functions.Function<Long, Observable<List<Chapter>>>() {
                    @Override
                    public Observable<List<Chapter>> apply(Long tick) {
                        if (mComic.getId() > 0) {
                            List<Chapter> cached =
                                    mChapterManager.getChapterList(IdCreator.createSourceComic(mComic));
                            if (!cached.isEmpty()) {
                                return Observable.just(cached);
                            }
                        }
                        // 没有缓存数据，不发射，让网络请求继续等待
                        return Observable.never();
                    }
                })
                .takeUntil(networkObs);

        mCompositeSubscription.add(
                Observable.merge(networkObs, cacheObs)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                new Consumer<List<Chapter>>() {
                                    @Override
                                    public void accept(List<Chapter> list) {
                                        // 主线程只做 UI 刷新
                                        if (mBaseView != null) {
                                            mBaseView.onComicLoadSuccess(mComic);
                                            mBaseView.onChapterLoadSuccess(list);
                                        }
                                    }
                                },
                                new Consumer<Throwable>() {
                                    @Override
                                    public void accept(Throwable throwable) {
                                        if (mBaseView == null) {
                                            return;
                                        }
                                        List<Chapter> cachedChapters =
                                                mChapterManager.getChapterList(IdCreator.createSourceComic(mComic));
                                        mBaseView.onComicLoadSuccess(mComic);
                                        mBaseView.onChapterLoadSuccess(cachedChapters);
                                        mBaseView.onParseError();
                                    }
                                }));
    }

    private void cancelHighlight() {
        if (mComic.getHighlight()) {
            mComic.setHighlight(false);
            mComic.setFavorite(System.currentTimeMillis());
            mComicManager.update(mComic);
            RxBus.getInstance().post(
                    new RxEvent(RxEvent.EVENT_COMIC_CANCEL_HIGHLIGHT, new MiniComic(mComic)));
        }
    }

    /**
     * 更新最后阅读
     *
     * @param path 最后阅读
     * @return 漫画ID
     */
    public long updateLast(String path) {
        if (mComic.getFavorite() != null) {
            mComic.setFavorite(System.currentTimeMillis());
        }
        mComic.setHistory(System.currentTimeMillis());
        if (!path.equals(mComic.getLast())) {
            mComic.setLast(path);
            mComic.setPage(1);
        }
        mComicManager.updateOrInsert(mComic);
        RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_COMIC_READ, new MiniComic(mComic)));
        return mComic.getId();
    }

    public Comic getComic() {
        return mComic;
    }

    public void backup(CimocDocumentFile file) {
        mComicManager.listFavoriteOrHistoryInRx()
                .doOnNext(new Consumer<List<Comic>>() {
                    @Override
                    public void accept(List<Comic> list) {
                        Backup.saveComicAuto(
                                mBaseView.getAppInstance().getContentResolver(), file, list);
                    }
                })
                .subscribe(result -> {
                }, throwable -> Log.e("RX", "Chapter error", throwable));
    }

    public void favoriteComic() {
        mComic.setFavorite(System.currentTimeMillis());
        mComicManager.updateOrInsert(mComic);
        RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_COMIC_FAVORITE, new MiniComic(mComic)));
    }

    public void unfavoriteComic() {
        long id = mComic.getId();
        // 标记收藏已取消，防止同步时被服务端恢复
        DataSyncManager.markFavoriteDeleted(mComic.getSource(), mComic.getCid());
        mComic.setFavorite(null);
        mTagRefManager.deleteByComic(id);
        mComicManager.updateOrDelete(mComic);
        RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_COMIC_UNFAVORITE, id));
    }

    private ArrayList<Task> getTaskList(List<Chapter> list) {
        ArrayList<Task> result = new ArrayList<>(list.size());
        for (Chapter chapter : list) {
            Task task = new Task(null, -1, chapter.getPath(), chapter.getTitle(), 0, 0);
            task.setSource(mComic.getSource());
            task.setCid(mComic.getCid());
            task.setState(Task.STATE_WAIT);
            result.add(task);
        }
        return result;
    }

    /**
     * 添加任务到数据库
     *
     * @param cList 所有章节列表，用于写索引文件
     * @param dList 下载章节列表
     */
    public void addTask(final List<Chapter> cList, final List<Chapter> dList) {
        mCompositeSubscription.add(Observable
                .create((io.reactivex.rxjava3.core.ObservableOnSubscribe<ArrayList<Task>>) emitter -> {
                        List<Chapter> dchapterList = new LinkedList<>();
                        List<Chapter> cchapterList = new LinkedList<>();
                        if (mComic.getId() == 0) {
                            ComicManager.getInstance(App.getApp()).updateOrInsert(mComic);
                        }
                        for (Chapter chapter : dList) {
                            Long newSourceComic = IdCreator.recreateSourceComic(
                                    chapter.getSourceComic(), mComic.getId());
                            Long newChapterId =
                                    IdCreator.recreateChapterId(newSourceComic, chapter.getId());
                            chapter.setId(newChapterId);
                            chapter.setSourceComic(newSourceComic);
                            dchapterList.add(chapter);
                        }
                        for (Chapter chapter : cList) {
                            Long newSourceComic = IdCreator.recreateSourceComic(
                                    chapter.getSourceComic(), mComic.getId());
                            Long newChapterId =
                                    IdCreator.recreateChapterId(newSourceComic, chapter.getId());
                            chapter.setId(newChapterId);
                            chapter.setSourceComic(newSourceComic);
                            cchapterList.add(chapter);
                        }
                        ChapterManager.getInstance(App.getApp()).updateOrInsert(cchapterList);
                        final ArrayList<Task> result = getTaskList(dchapterList);
                        mComic.setDownload(System.currentTimeMillis());
                        mComic.setHistory(System.currentTimeMillis());
                        RxBus.getInstance().post(
                                new RxEvent(RxEvent.EVENT_COMIC_READ, new MiniComic(mComic)));
                        mComicManager.runInTx(new Runnable() {
                            @Override
                            public void run() {
                                mComicManager.updateOrInsert(mComic);
                                for (Task task : result) {
                                    task.setKey(mComic.getId());
                                    mTaskManager.insert(task);
                                }
                            }
                        });
                        Download.updateComicIndex(mBaseView.getAppInstance().getContentResolver(),
                                mBaseView.getAppInstance().getDocumentFile(), cList, mComic);
                        emitter.onNext(result);
                        emitter.onComplete();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        new Consumer<ArrayList<Task>>() {
                            @Override
                            public void accept(ArrayList<Task> list) {
                                RxBus.getInstance().post(new RxEvent(
                                        RxEvent.EVENT_TASK_INSERT, new MiniComic(mComic), list));
                                mBaseView.onTaskAddSuccess(list);
                            }
                        },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) {
                                mBaseView.onTaskAddFail();
                            }
                        }));
    }
}
