package com.xyrlsz.xcimocob.service;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.core.Download;
import com.xyrlsz.xcimocob.core.Manga;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.manager.ChapterManager;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.manager.TaskManager;
import com.xyrlsz.xcimocob.misc.NotificationWrapper;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Task;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;
import com.xyrlsz.xcimocob.utils.DocumentUtils;
import com.xyrlsz.xcimocob.utils.FrescoUtils;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.CacheControl;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Hiroshi on 2016/9/1.
 */
public class DownloadService extends Service implements AppGetter {

    private static final String NOTIFICATION_DOWNLOAD = "NOTIFICATION_DOWNLOAD";

    private LongSparseArray<Pair<Worker, Future>> mWorkerArray;
    private ExecutorService mExecutorService;
    private OkHttpClient mHttpClient;
    private NotificationWrapper mNotification;
    private TaskManager mTaskManager;
    private SourceManager mSourceManager;
    private ComicManager mComicManager;
    private ChapterManager mChapterManager;
    private ContentResolver mContentResolver;
    private int mTaskTotalCount;      // 总任务数（含已完成）
    private int mTaskDoneCount;       // 已完成任务数
    private int mCumulativeMax;       // 累计总页数（所有任务合计）
    private int mCumulativeProgress;  // 累计已下载页数

    public static Intent createIntent(Context context, Task task) {
        ArrayList<Task> list = new ArrayList<>(1);
        list.add(task);
        return createIntent(context, list);
    }

    public static Intent createIntent(Context context, ArrayList<Task> list) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.putParcelableArrayListExtra(Extra.EXTRA_TASK, list);
        return intent;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new DownloadServiceBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
//        getApplication();
        PreferenceManager manager = App.getPreferenceManager();
        int num = manager.getNumber(PreferenceManager.PREF_DOWNLOAD_THREAD, 2).intValue();
        if (num <= 0) {
            num = 1;
        }
        mWorkerArray = new LongSparseArray<>();
        mExecutorService = Executors.newFixedThreadPool(num);
        mHttpClient = App.getHttpClient();
        mTaskManager = TaskManager.getInstance(this);
        mSourceManager = SourceManager.getInstance(this);
        mContentResolver = getContentResolver();
        mChapterManager = ChapterManager.getInstance(this);
        mComicManager = ComicManager.getInstance(this);
        mTaskTotalCount = 0;
        mTaskDoneCount = 0;
        mCumulativeMax = 0;
        mCumulativeProgress = 0;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_DOWNLOAD_START));
            if (mNotification == null) {
                mNotification = new NotificationWrapper(this, NOTIFICATION_DOWNLOAD,
                        R.drawable.ic_file_download_white_24dp, true);
                mNotification.postIndeterminate(getString(R.string.download_service_doing), true);
                try {
                    startForeground(NOTIFICATION_DOWNLOAD.hashCode(), mNotification.getNotification());
                } catch (Exception e) {
                    Log.e("DownloadService", "startForeground failed", e);
                    // 如果前台服务启动失败，仍然继续执行下载任务
                }
            }
            List<Task> list = intent.getParcelableArrayListExtra(Extra.EXTRA_TASK);
            int taskCount = list != null ? list.size() : 0;
            mTaskTotalCount += taskCount;
            for (Task task : Objects.requireNonNull(list)) {
                Worker worker = new Worker(task);
                Future future = mExecutorService.submit(worker);
                addWorker(task.getId(), worker, future);
            }
            // 使用聚合进度更新通知
            updateNotification();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mNotification != null) {
            mExecutorService.shutdownNow();
            notifyCompleted();
        }
    }

    @Override
    public App getAppInstance() {
        return App.getApp();
    }

    public synchronized void addWorker(long id, Worker worker, Future future) {
        if (mWorkerArray.get(id) == null) {
            mWorkerArray.put(id, Pair.create(worker, future));
        }
    }

    public synchronized void removeDownload(long id) {
        Pair<Worker, Future> pair = mWorkerArray.get(id);
        if (pair != null) {
            pair.second.cancel(true);
            mWorkerArray.remove(id);
        }
    }

    public synchronized void completeDownload(long id) {
        // 记录已完成任务的页数到累计进度
        Pair<Worker, Future> pair = mWorkerArray.get(id);
        if (pair != null) {
            Task task = pair.first.mTask;
            if (task.getMax() > 0) {
                mCumulativeProgress += task.getMax();
            }
        }
        mWorkerArray.remove(id);
        mTaskDoneCount++;
        if (mWorkerArray.isEmpty()) {
            notifyCompleted();
            stopSelf();
        } else {
            // 还有剩余任务，刷新聚合通知
            updateNotification();
        }
    }

    private void notifyCompleted() {
        if (mNotification != null) {
            mNotification.post(getString(R.string.download_service_done), false);
            try {
                stopForeground(true);
            } catch (Exception e) {
                Log.e("DownloadService", "stopForeground failed", e);
            }
            mNotification.cancel();
            mNotification = null;
        }
        mWorkerArray.clear();
        mTaskTotalCount = 0;
        mTaskDoneCount = 0;
        mCumulativeMax = 0;
        mCumulativeProgress = 0;
        RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_DOWNLOAD_STOP));
    }

    /**
     * 计算所有 Worker 的聚合进度并更新通知
     * 显示格式： "下载中 (2/5) — 45%"  或 "下载中 (3) — 解析中"
     */
    public synchronized void updateNotification() {
        if (mNotification == null) return;
        try {
            // 统计当前活跃的 Worker 进度（尚未完成的）
            int activeMax = 0;
            int activeProgress = 0;
            int parsingCount = 0;

            for (int i = 0; i < mWorkerArray.size(); i++) {
                Worker w = mWorkerArray.valueAt(i).first;
                Task task = w.mTask;
                int state = task.getState();
                if (state == Task.STATE_PARSE) {
                    parsingCount++;
                } else if (state == Task.STATE_DOING && task.getMax() > 0) {
                    activeMax += task.getMax();
                    activeProgress += task.getProgress();
                }
            }

            // 总进度 = 已完成 + 进行中
            int totalMax = mCumulativeMax;
            int totalProgress = mCumulativeProgress + activeProgress;

            int totalTasks = mTaskTotalCount;

            if (totalMax > 0) {
                // 有确定进度 → 显示百分比
                int percent = totalProgress * 100 / totalMax;
                String content = getString(R.string.download_service_doing)
                        + " (" + mTaskDoneCount + "/" + totalTasks + ") "
                        + percent + "%";
                mNotification.post(content, totalProgress, totalMax);
            } else if (parsingCount > 0) {
                // 都在解析中 → 显示不定进度
                String content = getString(R.string.download_service_doing)
                        + " (" + mTaskDoneCount + "/" + totalTasks + ") "
                        + getString(R.string.task_parse);
                mNotification.postIndeterminate(content, true);
            } else {
                // 其他情况（如刚启动，总页数尚未解析出来）
                String content = getString(R.string.download_service_doing)
                        + " (" + mTaskDoneCount + "/" + totalTasks + ")";
                mNotification.postIndeterminate(content, true);
            }
        } catch (Exception e) {
            Log.e("DownloadService", "updateNotification failed", e);
        }
    }

    public synchronized void initTask(List<Task> list) {
        for (Task task : list) {
            Pair<Worker, Future> pair = mWorkerArray.get(task.getId());
            if (pair != null) {
                task.setState(pair.first.mTask.getState());
            }
        }
    }

    public class Worker implements Runnable {

        private final Task mTask;
        private final MangaParser mParse;

        Worker(Task task) {
            mTask = task;
            mParse = mSourceManager.getParser(task.getSource());
        }

        @Override
        public void run() {
            try {
                List<ImageUrl> list = onDownloadParse();
                int size = list.size();
                if (size != 0) {
                    CimocDocumentFile dir = Download.updateChapterIndex(mContentResolver, getAppInstance().getDocumentFile(), mTask);
                    if (dir != null) {
                        mTask.setMax(size);
                        mTask.setState(Task.STATE_DOING);
                        // 累加总页数
                        mCumulativeMax += size;
                        // 更新聚合进度通知
                        updateNotification();
                        boolean success = false;
                        for (int i = mTask.getProgress(); i < size; ++i) {
                            onDownloadProgress(i);
                            ImageUrl image = list.get(i);
                            int count = 0;  // 单页下载错误次数
                            success = false; // 是否下载成功
                            while (count++ < 20 && !success) {
                                List<String> urls = image.getUrls();
                                for (int j = 0; !success && j < urls.size(); ++j) {
                                    String url = image.isLazy() ? Manga.getLazyUrl(mParse, urls.get(j)) : urls.get(j);
                                    success = GetCacheAndWrite(dir, i + 1, url);
                                    if (!success) {
                                        Headers imgHeaders = image.getHeaders();
                                        Request request = buildRequest(imgHeaders == null ? mParse.getHeader(url) : imgHeaders, url);
                                        success = RequestAndWrite(dir, request, i + 1, url);
                                    }
                                }
                            }
                            if (!success) {     // 单页下载错误
                                RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_TASK_STATE_CHANGE, Task.STATE_ERROR, mTask.getId()));
                                break;
                            }
                        }
                        if (success) {
                            onDownloadProgress(size);
                        }
                    } else {
                        RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_TASK_STATE_CHANGE, Task.STATE_ERROR, mTask.getId()));
                    }
                } else {
                    RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_TASK_STATE_CHANGE, Task.STATE_ERROR, mTask.getId()));
                }
            } catch (InterruptedIOException e) {
                RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_TASK_STATE_CHANGE, Task.STATE_PAUSE, mTask.getId()));
            } catch (IOException e) {
                RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_TASK_STATE_CHANGE, Task.STATE_ERROR, mTask.getId()));
            }

            completeDownload(mTask.getId());
            Comic comic = mComicManager.load(mTask.getSource(), mTask.getCid());
            Long sourceComic = IdCreator.createSourceComic(comic);
            List<Chapter> chapterList = mChapterManager.getChapterList(sourceComic);
            updateChapterList(chapterList);
        }

        private void updateChapterList(List<Chapter> list) {
            Map<String, Task> map = new HashMap<>();
            Comic comic = mComicManager.load(mTask.getSource(), mTask.getCid());
            for (Task task : mTaskManager.list(comic.getId())) {
                map.put(task.getPath(), task);
            }
            if (!map.isEmpty()) {
                List<Chapter> res = new LinkedList<>();
                for (Chapter chapter : list) {
                    Task task = map.get(chapter.getPath());
                    if (task != null) {
                        chapter.setDownload(true);
                        chapter.setCount(task.getProgress());
                        chapter.setComplete(task.isFinish());
//                        mChapterManager.update(chapter);
                        res.add(chapter);
                    }
                }
                mChapterManager.runInTx(() -> mChapterManager.updateOrInsert(res));
            }

        }

        private boolean GetCacheAndWrite(CimocDocumentFile parent, int num, String url) throws IOException {
            InputStream cacheIs = FrescoUtils.getCacheFileInputStream(url);
            if (cacheIs != null) {
                String displayName = buildFileName(num, url);
                displayName = displayName.replaceAll("[:/(\\\\)(\\?)<>\"(\\|)(\\.)]", "_") + ".jpg";
                CimocDocumentFile file = DocumentUtils.getOrCreateFile(parent, displayName);
                DocumentUtils.writeBinaryToFile(mContentResolver, Objects.requireNonNull(file), cacheIs);
                return true;
            }
            return false;
        }

        private boolean RequestAndWrite(CimocDocumentFile parent, Request request, int num, String url) throws InterruptedIOException {
            if (request != null) {
                try (Response response = mHttpClient.newCall(request).execute()) {
//                    if (mTask.getSource() == 72) {
//                        OkHttpClient mJMTTHttpClient = new OkHttpClient().newBuilder()
//                                .followRedirects(true)
//                                .followSslRedirects(true)
//                                .retryOnConnectionFailure(true)
//                                .addInterceptor(chain -> {
//                                    String url1 = chain.request().url().toString();
//                                    Response response1 = chain.proceed(chain.request());
//                                    if (!url1.toLowerCase().contains("media/photos"))
//                                        return response1;
//                                    int cha = Integer.parseInt(url1.substring(url1.indexOf("photos/") + 7, url1.lastIndexOf("/")));
//                                    if (cha < 220980) return response1;
//                                    byte[] res = new JMTTUtil().decodeImage(response1.body().byteStream());
//                                    MediaType mediaType = MediaType.parse("image/avif,image/webp,image/apng,image/*,*/*");
//                                    ResponseBody outputBytes = ResponseBody.create(mediaType, res);
//                                    return response1.newBuilder().body(outputBytes).build();
//                                })
//                                .build();
//                        response = mJMTTHttpClient.newCall(request).execute();
//                    } else {
                    //                    }
                    if (response.isSuccessful()) {
                        String displayName = buildFileName(num, url);
                        displayName = displayName.replaceAll("[:/(\\\\)(\\?)<>\"(\\|)(\\.)]", "_") + ".jpg";
                        CimocDocumentFile file = DocumentUtils.getOrCreateFile(parent, displayName);
                        DocumentUtils.writeBinaryToFile(mContentResolver, Objects.requireNonNull(file), response.body().byteStream());
                        return true;
                    }
                } catch (SocketTimeoutException e) {
                    e.printStackTrace();
                } catch (InterruptedIOException e) {
                    // 由暂停下载引发，需要抛出以便退出外层循环，结束任务
                    throw e;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        private Request buildRequest(Headers headers, String url) {
            if (StringUtils.isEmpty(url)) {
                return null;
            }

            return new Request.Builder()
                    .cacheControl(new CacheControl.Builder().noStore().build())
                    .headers(headers)
                    .url(url)
                    .get()
                    .build();
        }

        private String buildFileName(int num, String url) {
            String suffix = StringUtils.split(url, "\\.", -1);
            if (suffix == null) {
                suffix = "jpg";
            } else {
                suffix = suffix.split("\\?")[0];
            }
            return StringUtils.format("%03d.%s", num, suffix);
        }

        private List<ImageUrl> onDownloadParse() throws InterruptedIOException {
            mTask.setState(Task.STATE_PARSE);
            RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_TASK_STATE_CHANGE, Task.STATE_PARSE, mTask.getId()));
            return Manga.getImageUrls(mParse, mTask.getSource(), mTask.getCid(), mTask.getPath(), mTask.getTitle(), mChapterManager);
        }

        private void onDownloadProgress(int progress) {
            mTask.setProgress(progress);
            mTaskManager.update(mTask);
            RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_TASK_PROCESS, mTask.getId(), progress, mTask.getMax()));
            // 刷新聚合进度通知（所有任务的合计进度）
            updateNotification();
        }

    }

    public class DownloadServiceBinder extends Binder {

        public DownloadService getService() {
            return DownloadService.this;
        }

    }

}
