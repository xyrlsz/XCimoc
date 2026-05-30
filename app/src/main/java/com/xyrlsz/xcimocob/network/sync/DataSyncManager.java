package com.xyrlsz.xcimocob.network.sync;

import android.text.TextUtils;
import android.util.Log;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 自动数据同步管理器
 * <p>
 * 在后台静默同步数据到 data_server，无需用户手动操作。
 * - 监听 RxBus 数据变更事件（收藏、阅读、标签），防抖后自动同步
 * - 监听应用前后台切换，回到前台时自动同步全部数据
 * - 仅在开启自动同步、已登录、已配置服务器地址时执行
 * - 同步失败静默处理，不弹 Toast
 */
public class DataSyncManager {

    private static final String TAG = "DataSyncManager";
    private static final long DEBOUNCE_MS = 3000; // 3 秒防抖

    private static volatile DataSyncManager sInstance;

    private final CompositeDisposable mDisposable = new CompositeDisposable();
    private final ComicManager mComicManager;

    /**
     * 应用是否在前台
     */
    private final AtomicBoolean mIsForeground = new AtomicBoolean(false);

    private DataSyncManager() {
        // 使用 App 实例作为 AppGetter（App 实现了 AppGetter 接口）
        mComicManager = ComicManager.getInstance(App.getApp());
    }

    public static DataSyncManager getInstance() {
        if (sInstance == null) {
            synchronized (DataSyncManager.class) {
                if (sInstance == null) {
                    sInstance = new DataSyncManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * 初始化：开始监听数据变更事件。
     * 在 App.onCreate() 中调用。
     */
    public void init() {
        if (mDisposable.size() > 0) {
            return; // 已经初始化
        }
        Log.d(TAG, "DataSyncManager initialized");
        listenDataChanges();
    }

    /**
     * 释放资源，停止监听
     */
    public void destroy() {
        mDisposable.clear();
    }

    // ==================== 生命周期 ====================

    /**
     * 应用首次启动时调用（一次性的，仅用于冷启动）。
     * 在 App.onCreate() 中 init() 完毕后调用。
     */
    public void onAppStart() {
        mIsForeground.set(true);
        Log.d(TAG, "App cold start -> try full bidirectional sync");
        trySyncNow();
    }

    /**
     * 应用进入前台时调用 → 全量双向同步
     */
    public void onForeground() {
        mIsForeground.set(true);
        Log.d(TAG, "App foreground -> try full bidirectional sync");
        trySyncNow();
    }

    /**
     * 立即触发一次全量双向同步（跳过防抖间隔检查）。
     * 用于登录/注册成功后主动同步。
     */
    public void triggerNow() {
        Log.d(TAG, "Trigger immediate full bidirectional sync");
        trySyncNow();
    }

    /**
     * 内部：无条件执行一次全量双向同步，更新计时器
     */
    private void trySyncNow() {
        if (!shouldSync()) return;
        mLastFullSync = System.currentTimeMillis();
        doSyncAllBidirectional();
    }

    /**
     * 应用进入后台时调用
     */
    public void onBackground() {
        mIsForeground.set(false);
    }

    // ==================== 事件监听（按数据类型拆分） ====================

    private void listenDataChanges() {
        // 漫画事件（收藏/取消收藏/阅读/信息更新）→ 仅同步漫画
        mDisposable.add(RxBus.getInstance().toObservable(RxEvent.EVENT_COMIC_FAVORITE)
                .debounce(DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(e -> triggerDebounced(SyncType.COMIC),
                        t -> Log.w(TAG, "EVENT_COMIC_FAVORITE error", t)));

        mDisposable.add(RxBus.getInstance().toObservable(RxEvent.EVENT_COMIC_UNFAVORITE)
                .debounce(DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(e -> triggerDebounced(SyncType.COMIC),
                        t -> Log.w(TAG, "EVENT_COMIC_UNFAVORITE error", t)));

        mDisposable.add(RxBus.getInstance().toObservable(RxEvent.EVENT_COMIC_READ)
                .debounce(DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(e -> triggerDebounced(SyncType.COMIC),
                        t -> Log.w(TAG, "EVENT_COMIC_READ error", t)));

        mDisposable.add(RxBus.getInstance().toObservable(RxEvent.EVENT_COMIC_UPDATE)
                .debounce(DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(e -> triggerDebounced(SyncType.COMIC),
                        t -> Log.w(TAG, "EVENT_COMIC_UPDATE error", t)));


    }

    // ==================== 防抖触发 ====================

    private enum SyncType {COMIC, ALL}

    /**
     * 各类同步的上次执行时间
     */
    private long mLastComicSync = 0, mLastFullSync = 0;
    /**
     * 各类同步的最小间隔
     */
    private static final long INTERVAL_COMIC = 8_000;  // 漫画 8s
    private static final long INTERVAL_FULL = 60_000; // 全量 60s（前台触发）

    private synchronized void triggerDebounced(SyncType type) {
        if (!shouldSync()) return;

        long now = System.currentTimeMillis();
        switch (type) {
            case COMIC:
                if (now - mLastComicSync < INTERVAL_COMIC) return;
                mLastComicSync = now;
                doSyncComicsBidirectional();
                break;
            case ALL:
                if (now - mLastFullSync < INTERVAL_FULL) return;
                mLastFullSync = now;
                doSyncAllBidirectional();
                break;
        }
    }

    // ==================== 权限检查 ====================

    private boolean shouldSync() {
        if (!mIsForeground.get()) return false;
        PreferenceManager pm = App.getPreferenceManager();
        if (!pm.getBoolean(PreferenceManager.PREF_DATA_SERVER_AUTO_SYNC, true)) return false;
        String token = pm.getString(PreferenceManager.PREFERENCES_USER_TOCKEN, "");
        String url = pm.getString(PreferenceManager.PREF_DATA_SERVER_URL, "");
        return !TextUtils.isEmpty(token) && !TextUtils.isEmpty(url);
    }

    // ==================== 漫画双向同步（上传+下载，仅漫画） ====================

    private void doSyncComicsBidirectional() {
        Observable.fromCallable(() -> {
            String token = DataSyncClient.ensureValidToken();
            if (token == null) return false;
            PreferenceManager pm = App.getPreferenceManager();
            String url = pm.getString(PreferenceManager.PREF_DATA_SERVER_URL, "");
            if (TextUtils.isEmpty(url)) return false;

            DataSyncClient client = new DataSyncClient(url);

            // 上传本地漫画
            List<Comic> comics = mComicManager.listFavoriteOrHistory();
            List<DataSyncModels.ComicSyncItem> items = new ArrayList<>(comics.size());
            for (Comic c : comics) {
                DataSyncModels.ComicSyncItem item = new DataSyncModels.ComicSyncItem();
                item.source = c.getSource();
                item.cid = c.getCid();
                item.title = c.getTitle();
                item.cover = c.getCover();
                item.update = c.getUpdate();
                item.finish = c.getFinish() != null && c.getFinish();
                item.favorite = c.getFavorite();
                item.history = c.getHistory();
                item.last = c.getLast();
                item.page = c.getPage();
                item.chapter = c.getChapter();
                item.chapter_count = c.getChapterCount();
                items.add(item);
            }
            client.syncComics(token, items);

            // 下载服务器漫画并合并
            List<DataSyncModels.ComicServerItem> serverComics = client.listComics(token);
            if (serverComics != null) {
                for (DataSyncModels.ComicServerItem s : serverComics) {
                    Comic local = mComicManager.load(s.source, s.cid);
                    if (local == null) {
                        local = new Comic();
                        local.setId(0);
                        local.setSource(s.source);
                        local.setCid(s.cid);
                        local.setTitle(s.title);
                        local.setCover(s.cover);
                        local.setUpdate(s.update);
                        local.setFinish(s.finish);
                        local.setHighlight(s.highlight);
                        local.setFavorite(s.favorite);
                        local.setHistory(s.history);
                        local.setLast(s.last);
                        local.setPage(s.page);
                        local.setChapter(s.chapter);
                        if (s.chapter_count != null) local.setChapterCount(s.chapter_count);
                        mComicManager.insert(local);
                    } else {
                        boolean changed = false;
                        if (s.favorite != null && (local.getFavorite() == null || s.favorite > local.getFavorite())) {
                            local.setFavorite(s.favorite);
                            changed = true;
                        }
                        if (s.history != null && (local.getHistory() == null || s.history > local.getHistory())) {
                            local.setHistory(s.history);
                            local.setLast(s.last);
                            local.setPage(s.page);
                            local.setChapter(s.chapter);
                            changed = true;
                        }
                        if ((local.getTitle() == null || local.getTitle().isEmpty()) && s.title != null) {
                            local.setTitle(s.title);
                            local.setCover(s.cover);
                            local.setUpdate(s.update);
                            local.setFinish(s.finish);
                            local.setHighlight(s.highlight);
                            if (s.chapter_count != null) local.setChapterCount(s.chapter_count);
                            changed = true;
                        }
                        if (changed) mComicManager.update(local);
                    }
                }
            }
            Log.d(TAG, "Bidirectional comic sync done");
            return true;
        }).subscribeOn(Schedulers.io()).subscribe(
                r -> {
                }, t -> Log.w(TAG, "Bidirectional comic sync failed", t));
    }

    // ==================== 全量双向同步（漫画+设置，前台触发） ====================

    private void doSyncAllBidirectional() {
        Observable.fromCallable(() -> {
            String token = DataSyncClient.ensureValidToken();
            if (token == null) return false;
            PreferenceManager pm = App.getPreferenceManager();
            String url = pm.getString(PreferenceManager.PREF_DATA_SERVER_URL, "");
            if (TextUtils.isEmpty(url)) return false;

            DataSyncClient client = new DataSyncClient(url);

            // 1. 上传漫画
            List<Comic> comics = mComicManager.listFavoriteOrHistory();
            List<DataSyncModels.ComicSyncItem> comicItems = new ArrayList<>(comics.size());
            for (Comic c : comics) {
                DataSyncModels.ComicSyncItem item = new DataSyncModels.ComicSyncItem();
                item.source = c.getSource();
                item.cid = c.getCid();
                item.title = c.getTitle();
                item.cover = c.getCover();
                item.update = c.getUpdate();
                item.finish = c.getFinish() != null && c.getFinish();
                item.highlight = c.getHighlight();
                item.favorite = c.getFavorite();
                item.history = c.getHistory();
                item.last = c.getLast();
                item.page = c.getPage();
                item.chapter = c.getChapter();
                item.chapter_count = c.getChapterCount();
                comicItems.add(item);
            }
            client.syncComics(token, comicItems);

            // 2. 上传设置
            Map<String, ?> allPrefs = App.getPreferenceManager().getAll();
            List<DataSyncModels.SettingItem> settingItems = new ArrayList<>();
            for (Map.Entry<String, ?> e : allPrefs.entrySet()) {
                if (e.getValue() != null) {
                    settingItems.add(new DataSyncModels.SettingItem(e.getKey(), e.getValue().toString()));
                }
            }
            client.syncSettings(token, settingItems);

            // 3. 下载漫画并合并
            List<DataSyncModels.ComicServerItem> serverComics = client.listComics(token);
            if (serverComics != null) {
                for (DataSyncModels.ComicServerItem s : serverComics) {
                    Comic local = mComicManager.load(s.source, s.cid);
                    if (local == null) {
                        local = new Comic();
                        local.setId(0);
                        local.setSource(s.source);
                        local.setCid(s.cid);
                        local.setTitle(s.title);
                        local.setCover(s.cover);
                        local.setUpdate(s.update);
                        local.setFinish(s.finish);
                        local.setHighlight(s.highlight);
                        local.setFavorite(s.favorite);
                        local.setHistory(s.history);
                        local.setLast(s.last);
                        local.setPage(s.page);
                        local.setChapter(s.chapter);
                        if (s.chapter_count != null) local.setChapterCount(s.chapter_count);
                        mComicManager.insert(local);
                    } else {
                        boolean changed = false;
                        if (s.favorite != null && (local.getFavorite() == null || s.favorite > local.getFavorite())) {
                            local.setFavorite(s.favorite);
                            changed = true;
                        }
                        if (s.history != null && (local.getHistory() == null || s.history > local.getHistory())) {
                            local.setHistory(s.history);
                            local.setLast(s.last);
                            local.setPage(s.page);
                            local.setChapter(s.chapter);
                            changed = true;
                        }
                        if ((local.getTitle() == null || local.getTitle().isEmpty()) && s.title != null) {
                            local.setTitle(s.title);
                            local.setCover(s.cover);
                            local.setUpdate(s.update);
                            local.setFinish(s.finish);
                            local.setHighlight(s.highlight);
                            if (s.chapter_count != null) local.setChapterCount(s.chapter_count);
                            changed = true;
                        }
                        if (changed) mComicManager.update(local);
                    }
                }
            }

            // 5. 下载设置
            List<DataSyncModels.SettingServerItem> serverSettings = client.listSettings(token);
            if (serverSettings != null) {
                for (DataSyncModels.SettingServerItem s : serverSettings) {
                    if (s.key != null && s.value != null) {
                        pm.putObject(s.key, s.value);
                    }
                }
            }

            Log.d(TAG, "Full bidirectional sync done");
            return true;
        }).subscribeOn(Schedulers.io()).subscribe(
                r -> {
                }, t -> Log.w(TAG, "Full bidirectional sync failed", t));
    }

}
