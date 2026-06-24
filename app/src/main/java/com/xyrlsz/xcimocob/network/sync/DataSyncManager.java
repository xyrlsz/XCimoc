package com.xyrlsz.xcimocob.network.sync;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            if (!createClient()) return false;

            uploadComics(mClient, token);
            downloadAndMergeComics(mClient, token);

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
            if (!createClient()) return false;

            uploadComics(mClient, token);
            uploadSettings(mClient, token);
            downloadAndMergeComics(mClient, token);
            downloadSettings(mClient, token);

            Log.d(TAG, "Full bidirectional sync done");
            return true;
        }).subscribeOn(Schedulers.io()).subscribe(
                r -> {
                }, t -> Log.w(TAG, "Full bidirectional sync failed", t));
    }

    // ==================== 共享的数据同步方法 ====================

    @Nullable
    private DataSyncClient mClient;

    /** 创建或获取 DataSyncClient 实例（复用 OkHttpClient） */
    private boolean createClient() {
        String url = App.getPreferenceManager().getString(PreferenceManager.PREF_DATA_SERVER_URL, "");
        if (TextUtils.isEmpty(url)) return false;
        if (mClient == null || !mClient.isSameBaseUrl(url)) {
            mClient = new DataSyncClient(url);
        }
        return true;
    }

    /** 上传本地漫画到服务端（包括历史删除标记） */
    private void uploadComics(DataSyncClient client, String token) throws Exception {
        List<Comic> comics = mComicManager.listFavoriteOrHistory();
        List<DataSyncModels.ComicSyncItem> items = new ArrayList<>(comics.size() + 8);
        // 记录已出现在常规上传中的漫画 key，避免与清除标记冲突
        Set<String> uploadedKeys = new HashSet<>();
        for (Comic c : comics) {
            items.add(buildComicSyncItem(c));
            uploadedKeys.add(c.getSource() + ":" + c.getCid());
        }

        // 附加标记了"历史已删除"的漫画，通知服务端清除历史
        // 跳过那些已在本地重新有了 history/favorite 的漫画
        Set<String> deletedKeys = getHistoryDeletedKeys();
        if (!deletedKeys.isEmpty()) {
            for (String key : deletedKeys) {
                if (uploadedKeys.contains(key)) {
                    // 漫画已在本地重新有了数据（用户又收藏或阅读了），不再发送清除标记
                    continue;
                }
                String[] parts = key.split(":", 2);
                if (parts.length == 2) {
                    DataSyncModels.ComicSyncItem item = new DataSyncModels.ComicSyncItem();
                    try {
                        item.source = Integer.parseInt(parts[0]);
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid history deleted key (source not int): " + key);
                        continue;
                    }
                    item.cid = parts[1];
                    item.clear_history = true;
                    items.add(item);
                }
            }
        }

        client.syncComics(token, items);

        // 上传成功后清除所有删除标记
        if (!deletedKeys.isEmpty()) {
            clearHistoryDeletedKeys();
        }
    }

    /** 从服务端下载漫画并合并到本地 */
    private void downloadAndMergeComics(DataSyncClient client, String token) throws Exception {
        List<DataSyncModels.ComicServerItem> serverComics = client.listComics(token);
        if (serverComics == null) return;

        for (DataSyncModels.ComicServerItem s : serverComics) {
            Comic local = mComicManager.load(s.source, s.cid);
            if (local == null) {
                local = createComicFromServer(s);
                mComicManager.insert(local);
            } else {
                mergeServerComic(local, s);
            }
        }
    }

    /** 敏感 key 列表：不上传到服务器，也不从服务器覆盖本地 */
    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            PreferenceManager.PREFERENCES_USER_TOCKEN,
            PreferenceManager.PREFERENCES_USER_NAME,
            PreferenceManager.PREFERENCES_USER_EMAIL,
            PreferenceManager.PREFERENCES_USER_ID,
            PreferenceManager.PREF_DATA_SERVER_URL,
            PreferenceManager.PREF_DATA_SERVER_AUTO_SYNC
    ));

    /** 上传本地设置到服务端（过滤掉敏感 key） */
    private void uploadSettings(DataSyncClient client, String token) throws Exception {
        Map<String, ?> allPrefs = App.getPreferenceManager().getAll();
        List<DataSyncModels.SettingItem> settingItems = new ArrayList<>();
        for (Map.Entry<String, ?> e : allPrefs.entrySet()) {
            if (e.getValue() != null && !SENSITIVE_KEYS.contains(e.getKey())) {
                settingItems.add(new DataSyncModels.SettingItem(e.getKey(), e.getValue().toString()));
            }
        }
        client.syncSettings(token, settingItems);
    }

    /** 从服务端下载设置并合并到本地（跳过敏感 key，避免覆盖本地 token 等） */
    private void downloadSettings(DataSyncClient client, String token) throws Exception {
        List<DataSyncModels.SettingServerItem> serverSettings = client.listSettings(token);
        if (serverSettings == null) return;

        PreferenceManager pm = App.getPreferenceManager();
        for (DataSyncModels.SettingServerItem s : serverSettings) {
            if (s.key != null && s.value != null && !SENSITIVE_KEYS.contains(s.key)) {
                pm.putObject(s.key, s.value);
            }
        }
    }

    /** 构建漫画同步项 */
    private static DataSyncModels.ComicSyncItem buildComicSyncItem(Comic c) {
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
        return item;
    }

    /** 从服务端数据创建新 Comic */
    private static Comic createComicFromServer(DataSyncModels.ComicServerItem s) {
        Comic local = new Comic();
        local.setId(0);
        local.setSource(s.source);
        local.setCid(s.cid);
        local.setTitle(s.title);
        local.setCover(s.cover);
        local.setUpdate(s.update);
        local.setFinish(s.finish);
        local.setFavorite(s.favorite);
        local.setHistory(s.history);
        local.setLast(s.last);
        local.setPage(s.page);
        local.setChapter(s.chapter);
        if (s.chapter_count != null) local.setChapterCount(s.chapter_count);
        return local;
    }

    /** 将服务端漫画数据合并到本地（服务端数据优先） */
    private void mergeServerComic(Comic local, DataSyncModels.ComicServerItem s) {
        boolean changed = false;
        if (s.favorite != null && (local.getFavorite() == null || s.favorite > local.getFavorite())) {
            local.setFavorite(s.favorite);
            changed = true;
        }
        // 如果本地明确标记了"历史已删除"，则不从服务端恢复历史
        boolean historyDeleted = isHistoryDeleted(s.source, s.cid);
        if (!historyDeleted && s.history != null && (local.getHistory() == null || s.history > local.getHistory())) {
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
            if (s.chapter_count != null) local.setChapterCount(s.chapter_count);
            changed = true;
        }
        if (changed) mComicManager.update(local);
    }

    // ==================== 历史删除标记追踪 ====================

    private static final String PREF_HISTORY_DELETED_KEYS = "history_deleted_keys";

    /**
     * 添加一条"历史已删除"的漫画标记（source:cid）
     */
    public static void markHistoryDeleted(int source, String cid) {
        Set<String> keys = getHistoryDeletedKeys();
        keys.add(source + ":" + cid);
        saveHistoryDeletedKeys(keys);
    }

    /**
     * 批量添加"历史已删除"标记
     */
    public static void markHistoryDeleted(List<Comic> comics) {
        Set<String> keys = getHistoryDeletedKeys();
        for (Comic c : comics) {
            keys.add(c.getSource() + ":" + c.getCid());
        }
        saveHistoryDeletedKeys(keys);
    }

    /**
     * 检查某漫画是否被标记为"历史已删除"
     */
    public static boolean isHistoryDeleted(int source, String cid) {
        return getHistoryDeletedKeys().contains(source + ":" + cid);
    }

    /**
     * 获取所有"历史已删除"标记（供 BackupPresenter 上传使用）
     */
    public static Set<String> getHistoryDeletedKeysForUpload() {
        return getHistoryDeletedKeys();
    }

    /**
     * 获取所有"历史已删除"标记
     */
    private static Set<String> getHistoryDeletedKeys() {
        String json = App.getPreferenceManager().getString(PREF_HISTORY_DELETED_KEYS, "");
        if (json.isEmpty()) return new HashSet<>();
        Set<String> result = new HashSet<>();
        // 简单格式：逗号分隔，例如 "1:abc,2:def"
        for (String key : json.split(",")) {
            String trimmed = key.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 持久化"历史已删除"标记集合
     */
    private static void saveHistoryDeletedKeys(Set<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (sb.length() > 0) sb.append(",");
            sb.append(key);
        }
        App.getPreferenceManager().putString(PREF_HISTORY_DELETED_KEYS, sb.toString());
    }

    /**
     * 清除所有"历史已删除"标记（上传成功后调用）
     */
    private static void clearHistoryDeletedKeys() {
        App.getPreferenceManager().putString(PREF_HISTORY_DELETED_KEYS, "");
    }

    /**
     * 供 BackupPresenter 上传成功后调用的公开清除方法
     */
    public static void clearHistoryDeletedKeysAfterUpload() {
        clearHistoryDeletedKeys();
    }

}
