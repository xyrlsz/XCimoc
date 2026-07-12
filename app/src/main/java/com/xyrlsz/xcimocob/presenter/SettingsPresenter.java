package com.xyrlsz.xcimocob.presenter;

import android.util.Pair;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.xyrlsz.xcimocob.core.Download;
import com.xyrlsz.xcimocob.core.Storage;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.TaskManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.model.Task;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;
import com.xyrlsz.xcimocob.ui.view.SettingsView;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Action;

/**
 * Created by Hiroshi on 2016/7/22.
 */
public class SettingsPresenter extends BasePresenter<SettingsView> {

    private ComicManager mComicManager;
    private TaskManager mTaskManager;

    @Override
    protected void onViewAttach() {
        mComicManager = ComicManager.getInstance(mBaseView);
        mTaskManager = TaskManager.getInstance(mBaseView);
    }

    public void clearCache() {
        Fresco.getImagePipeline().clearDiskCaches();
    }

    public void moveFiles(CimocDocumentFile dst) {

        mCompositeSubscription.add(Storage.moveRootDir(mBaseView.getAppInstance().getContentResolver(),
                mBaseView.getAppInstance().getDocumentFile(), dst)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String msg) {
                        RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_DIALOG_PROGRESS, msg));
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        mBaseView.onExecuteFail();
                    }
                }, new Action() {
                    @Override
                    public void run() {
                        mBaseView.onFileMoveSuccess();
                    }
                }));
    }

    private void updateKey(long key, List<Task> list) {
        for (Task task : list) {
            task.setKey(key);
        }
    }

    public void scanTask() {
        // Todo 重写一下
        mCompositeSubscription.add(Download.scan(mBaseView.getAppInstance().getContentResolver(), mBaseView.getAppInstance().getDocumentFile())
                .doOnNext(new Consumer<Pair<Comic, List<Task>>>() {
                    @Override
                    public void accept(Pair<Comic, List<Task>> pair) {
                        Comic comic = mComicManager.load(pair.first.getSource(), pair.first.getCid());
                        if (comic == null) {
                            mComicManager.insert(pair.first);
                            updateKey(pair.first.getId(), pair.second);
                            mTaskManager.insertInTx(pair.second);
                            comic = pair.first;
                        } else {
                            comic.setDownload(System.currentTimeMillis());
                            mComicManager.update(comic);
                            updateKey(comic.getId(), pair.second);
                            mTaskManager.insertIfNotExist(pair.second);
                        }
                        RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_TASK_INSERT, new MiniComic(comic)));
                        RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_DIALOG_PROGRESS, comic.getTitle()));
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Pair<Comic, List<Task>>>() {
                    @Override
                    public void accept(Pair<Comic, List<Task>> pair) {
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        mBaseView.onExecuteFail();
                    }
                }, new Action() {
                    @Override
                    public void run() {
                        mBaseView.onExecuteSuccess();
                    }
                }));
    }

}
