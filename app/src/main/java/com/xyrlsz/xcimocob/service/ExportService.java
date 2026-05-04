package com.xyrlsz.xcimocob.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.misc.NotificationWrapper;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.utils.ComicUtils;
import com.xyrlsz.xcimocob.utils.ThreadRunUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 导出漫画的前台 Service
 * 将已下载的漫画导出为指定格式（simple/zip/epub/cbz），
 * 并通过通知和 RxBus 反馈结果
 */
public class ExportService extends Service implements AppGetter {

    private static final String TAG = "ExportService";
    private static final String NOTIFICATION_EXPORT = "NOTIFICATION_EXPORT";

    private NotificationWrapper mNotification;
    private ComicManager mComicManager;
    private ExecutorService mExecutorService;
    private volatile boolean mIsExporting;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mComicManager = ComicManager.getInstance(this);
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // 防止重复导出
            if (mIsExporting) {
                Log.d(TAG, "Export already in progress, ignoring");
                return START_NOT_STICKY;
            }

            long comicId = intent.getLongExtra(Extra.EXTRA_ID, -1);
            int exportType = intent.getIntExtra(Extra.EXTRA_EXPORT_TYPE, ComicUtils.SIMPLE);

            if (comicId == -1) {
                Log.e(TAG, "No comic ID provided");
                stopSelf();
                return START_NOT_STICKY;
            }

            Comic comic = mComicManager.load(comicId);
            if (comic == null) {
                Log.e(TAG, "Comic not found: " + comicId);
                stopSelf();
                return START_NOT_STICKY;
            }

            mIsExporting = true;
            startForegroundNotification(comic.getTitle());
            startExport(comic, exportType);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
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

    private void startForegroundNotification(String comicTitle) {
        mNotification = new NotificationWrapper(this, NOTIFICATION_EXPORT,
                R.drawable.ic_file_download_white_24dp, true);
        mNotification.post(getString(R.string.download_output_doing, comicTitle), true);
        try {
            startForeground(NOTIFICATION_EXPORT.hashCode(), mNotification.getNotification());
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }
    }

    private void startExport(Comic comic, int exportType) {
        mExecutorService.submit(() -> {
            // 显示不定进度条（循环动画），表示正在导出中
            ThreadRunUtils.runOnMainThread(() -> {
                if (mNotification != null) {
                    mNotification.postIndeterminate(
                            getString(R.string.download_output_doing, comic.getTitle()),
                            true);
                }
            });
            // 使用 CountDownLatch 等待导出完成后才停止服务
            ComicUtils.OutputDownloadedComic(this, this, exportType, comic,
                    new ComicUtils.OutputComicCallback() {
                        @Override
                        public void onSuccess(String path) {
                            Log.d(TAG, "Export success: " + path);
                            if (mNotification != null) {
                                mNotification.post(
                                        getString(R.string.download_output_success),
                                        false);
                            }
                            RxBus.getInstance().post(new RxEvent(
                                    RxEvent.EVENT_EXPORT_RESULT, true, path));
                            stopForegroundAndSelf();
                        }

                        @Override
                        public void onFailure(String message) {
                            Log.e(TAG, "Export failed: " + message);
                            if (mNotification != null) {
                                mNotification.post(
                                        getString(R.string.download_output_fail),
                                        false);
                            }
                            RxBus.getInstance().post(new RxEvent(
                                    RxEvent.EVENT_EXPORT_RESULT, false, message));
                            stopForegroundAndSelf();
                        }
                    });
        });
    }

    private void stopForegroundAndSelf() {
        mIsExporting = false;
        ThreadRunUtils.runOnMainThread(() -> {
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
        });
    }

    public static Intent createIntent(android.content.Context context, long comicId, int exportType) {
        Intent intent = new Intent(context, ExportService.class);
        intent.putExtra(Extra.EXTRA_ID, comicId);
        intent.putExtra(Extra.EXTRA_EXPORT_TYPE, exportType);
        return intent;
    }
}
