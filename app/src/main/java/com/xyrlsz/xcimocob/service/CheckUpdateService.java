package com.xyrlsz.xcimocob.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.core.Manga;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.misc.NotificationWrapper;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 检查漫画更新的前台 Service
 * 用于在后台检查所有收藏漫画的更新状态，并通过通知和 RxBus 反馈进度
 */
public class CheckUpdateService extends Service implements AppGetter {

    private static final String TAG = "CheckUpdateService";
    private static final String NOTIFICATION_CHECK_UPDATE = "NOTIFICATION_CHECK_UPDATE";

    private NotificationWrapper mNotification;
    private ComicManager mComicManager;
    private SourceManager mSourceManager;
    private Disposable mDisposable;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mComicManager = ComicManager.getInstance(this);
        mSourceManager = SourceManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // 防止重复启动检查
            if (mDisposable != null && !mDisposable.isDisposed()) {
                return START_NOT_STICKY;
            }
            startForegroundNotification();
            startCheckUpdate();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mDisposable != null && !mDisposable.isDisposed()) {
            mDisposable.dispose();
        }
        if (mNotification != null) {
            try {
                stopForeground(true);
            } catch (Exception e) {
                Log.e(TAG, "stopForeground failed", e);
            }
            mNotification.cancel();
            mNotification = null;
        }
    }

    @Override
    public App getAppInstance() {
        return App.getApp();
    }

    private void startForegroundNotification() {
        mNotification = new NotificationWrapper(this, NOTIFICATION_CHECK_UPDATE,
                R.drawable.ic_sync_white_24dp, true);
        // 启动时显示不定进度条（循环动画），首条结果返回后切换为数值进度
        mNotification.postIndeterminate(getString(R.string.favorite_check_update_doing), true);
        try {
            startForeground(NOTIFICATION_CHECK_UPDATE.hashCode(), mNotification.getNotification());
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }
    }

    private void startCheckUpdate() {
        final List<Comic> list = mComicManager.listFavorite();
        final int total = list.size();

        mDisposable = Manga.checkUpdate(mSourceManager, list)
                .doOnNext(event -> {
                    if (event.hasUpdate) {
                        mComicManager.update(event.comic);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        new io.reactivex.rxjava3.functions.Consumer<Manga.CheckUpdateEvent>() {
                            private int count = 0;

                            @Override
                            public void accept(Manga.CheckUpdateEvent event) {
                                ++count;
                                // 更新通知进度
                                if (mNotification != null) {
                                    mNotification.post(
                                            getString(R.string.favorite_check_update_doing),
                                            count, total);
                                }
                                // 发送 RxBus 事件通知 UI
                                RxBus.getInstance().post(new RxEvent(
                                        RxEvent.EVENT_CHECK_UPDATE_PROGRESS,
                                        count, total,
                                        event.hasUpdate ? event.comic : null));
                            }
                        },
                        new io.reactivex.rxjava3.functions.Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable e) {
                                Log.e(TAG, "check update failed", e);
                                if (mNotification != null) {
                                    mNotification.post(
                                            getString(R.string.favorite_check_update_fail),
                                            false);
                                }
                                RxBus.getInstance().post(new RxEvent(
                                        RxEvent.EVENT_CHECK_UPDATE_FAIL));
                                stopForegroundAndSelf();
                            }
                        },
                        new io.reactivex.rxjava3.functions.Action() {
                            @Override
                            public void run() {
                                if (mNotification != null) {
                                    mNotification.post(
                                            getString(R.string.favorite_check_update_done),
                                            false);
                                }
                                RxBus.getInstance().post(new RxEvent(
                                        RxEvent.EVENT_CHECK_UPDATE_COMPLETE));
                                stopForegroundAndSelf();
                            }
                        }
                );
    }

    private void stopForegroundAndSelf() {
        if (mNotification != null) {
            try {
                stopForeground(true);
            } catch (Exception e) {
                Log.e(TAG, "stopForeground failed", e);
            }
            mNotification.cancel();
            mNotification = null;
        }
        stopSelf();
    }
}
