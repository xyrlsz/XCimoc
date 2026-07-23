package com.xyrlsz.xcimocob.network.sync;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
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
            Log.d(TAG, "[Sync] Starting bidirectional comic sync...");
            String token = DataSyncClient.ensureValidToken();
            if (token == null) {
                Log.w(TAG, "[Sync] Token is null, aborting sync");
                return false;
            }
            if (!createClient()) {
                Log.w(TAG, "[Sync] Failed to create client, aborting sync");
                return false;
            }

            uploadComics(mClient, token);
            downloadAndMergeComics(mClient, token);

            Log.d(TAG, "[Sync] Bidirectional comic sync completed successfully");
            return true;
        }).subscribeOn(Schedulers.io()).subscribe(
                r -> {
                    if (r == Boolean.TRUE) {
                        Log.d(TAG, "[Sync] Sync finished successfully");
                    }
                }, t -> {
                    Log.e(TAG, "[Sync] Bidirectional comic sync failed", t);
                    // 发生错误时尝试显示通知（静默失败的隐患）
                    android.util.Log.e(TAG, "[Sync] Error details: " + t.getMessage());
                });
    }

    // ==================== 全量双向同步（漫画+设置，前台触发） ====================

    private void doSyncAllBidirectional() {
        Observable.fromCallable(() -> {
            Log.d(TAG, "[Sync] Starting full bidirectional sync...");
            String token = DataSyncClient.ensureValidToken();
            if (token == null) {
                Log.w(TAG, "[Sync] Token is null, aborting full sync");
                return false;
            }
            if (!createClient()) {
                Log.w(TAG, "[Sync] Failed to create client, aborting full sync");
                return false;
            }

            uploadComics(mClient, token);
            uploadSettings(mClient, token);
            downloadAndMergeComics(mClient, token);
            downloadSettings(mClient, token);

            Log.d(TAG, "[Sync] Full bidirectional sync completed successfully");
            return true;
        }).subscribeOn(Schedulers.io()).subscribe(
                r -> {
                    if (r == Boolean.TRUE) {
                        Log.d(TAG, "[Sync] Full sync finished successfully");
                    }
                }, t -> {
                    Log.e(TAG, "[Sync] Full bidirectional sync failed", t);
                    android.util.Log.e(TAG, "[Sync] Full sync error details: " + t.getMessage());
                });
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

    /** 上传本地漫画到服务端（包括历史/收藏删除标记） */
    private void uploadComics(DataSyncClient client, String token) throws Exception {
        List<Comic> comics = mComicManager.listFavoriteOrHistory();
        Log.d(TAG, "[Sync] Upload: " + comics.size() + " comics from local");
        List<DataSyncModels.ComicSyncItem> items = new ArrayList<>(comics.size() + 8);
        // 记录已出现在常规上传中的漫画 key，避免与清除标记冲突
        Set<String> uploadedKeys = new HashSet<>();
        for (Comic c : comics) {
            items.add(buildComicSyncItem(c));
            uploadedKeys.add(c.getSource() + ":" + c.getCid());
        }

        // 附加标记了"历史已删除"的漫画，通知服务端清除历史
        Set<String> deletedKeys = getHistoryDeletedKeys();
        if (!deletedKeys.isEmpty()) {
            Log.d(TAG, "[Sync] Upload: " + deletedKeys.size() + " history-deleted markers");
            for (String key : deletedKeys) {
                String[] parts = key.split(":", 2);
                if (parts.length != 2) continue;

                int source;
                try {
                    source = Integer.parseInt(parts[0]);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid history deleted key (source not int): " + key);
                    continue;
                }
                String cid = parts[1];

                if (uploadedKeys.contains(key)) {
                    // 该漫画在本地仍有数据（如收藏），但用户已清除历史记录
                    // 需要标记 clear_history，让服务端也清除历史，避免下次下载时恢复
                    for (DataSyncModels.ComicSyncItem item : items) {
                        if (item.source == source && item.cid != null && item.cid.equals(cid)) {
                            item.clear_history = true;
                            Log.d(TAG, "[Sync]   clear_history on existing comic " + key);
                            break;
                        }
                    }
                } else {
                    DataSyncModels.ComicSyncItem item = new DataSyncModels.ComicSyncItem();
                    item.source = source;
                    item.cid = cid;
                    item.clear_history = true;
                    items.add(item);
                    Log.d(TAG, "[Sync]   standalone clear_history item for " + key);
                }
            }
        }

        // 附加标记了"收藏已取消"的漫画，通知服务端清除收藏
        Set<String> favoriteDeletedKeys = getFavoriteDeletedKeys();
        if (!favoriteDeletedKeys.isEmpty()) {
            Log.d(TAG, "[Sync] Upload: " + favoriteDeletedKeys.size() + " favorite-deleted markers");
            for (String key : favoriteDeletedKeys) {
                String[] parts = key.split(":", 2);
                if (parts.length != 2) continue;

                int source;
                try {
                    source = Integer.parseInt(parts[0]);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid favorite deleted key (source not int): " + key);
                    continue;
                }
                String cid = parts[1];

                if (uploadedKeys.contains(key)) {
                    // 该漫画在本地仍有数据（如历史记录），但用户已取消收藏
                    // 需要标记 clear_favorite，让服务端也清除收藏，避免下次下载时恢复
                    for (DataSyncModels.ComicSyncItem item : items) {
                        if (item.source == source && item.cid != null && item.cid.equals(cid)) {
                            item.clear_favorite = true;
                            Log.d(TAG, "[Sync]   clear_favorite on existing comic " + key);
                            break;
                        }
                    }
                } else {
                    DataSyncModels.ComicSyncItem item = new DataSyncModels.ComicSyncItem();
                    item.source = source;
                    item.cid = cid;
                    item.clear_favorite = true;
                    items.add(item);
                    Log.d(TAG, "[Sync]   standalone clear_favorite item for " + key);
                }
            }
        }

        Log.d(TAG, "[Sync] Upload: sending " + items.size() + " items to server");
        for (DataSyncModels.ComicSyncItem item : items) {
            Log.d(TAG, "[Sync]   -> src=" + item.source + " cid=" + item.cid
                    + " fav=" + item.favorite + " hist=" + item.history
                    + " clrFav=" + item.clear_favorite + " clrHist=" + item.clear_history);
        }
        client.syncComics(token, items);

        // 上传成功后清除所有删除标记
        if (!deletedKeys.isEmpty()) {
            clearHistoryDeletedKeys();
            Log.d(TAG, "[Sync] Cleared " + deletedKeys.size() + " history-deleted markers");
        }
        if (!favoriteDeletedKeys.isEmpty()) {
            clearFavoriteDeletedKeys();
            Log.d(TAG, "[Sync] Cleared " + favoriteDeletedKeys.size() + " favorite-deleted markers");
        }
    }

    /** 从服务端下载漫画并合并到本地，完成后发送 RxBus 事件通知 UI 刷新 */
    private void downloadAndMergeComics(DataSyncClient client, String token) throws Exception {
        List<DataSyncModels.ComicServerItem> serverComics = client.listComics(token);
        if (serverComics == null) {
            Log.d(TAG, "[Sync] Download: server returned null");
            return;
        }
        Log.d(TAG, "[Sync] Download: " + serverComics.size() + " comics from server");

        // 仅追踪本次真正需要通知 UI 的漫画（新增或数据有变更的）
        final List<MiniComic> favoriteList = new LinkedList<>();
        final List<MiniComic> historyList = new LinkedList<>();
        int insertedCount = 0, updatedCount = 0, clearedFavCount = 0, clearedHistCount = 0;

        for (DataSyncModels.ComicServerItem s : serverComics) {
            Comic local = mComicManager.load(s.source, s.cid);
            if (local == null) {
                local = createComicFromServer(s);
                mComicManager.insert(local);
                insertedCount++;
                // 新插入的漫画需要通知 UI
                if (s.favorite != null) {
                    favoriteList.add(new MiniComic(local));
                }
                if (s.history != null) {
                    historyList.add(new MiniComic(local));
                }
            } else {
                boolean changed = mergeServerComic(local, s);
                // 仅当合并后数据有实际变更时才通知 UI，避免重复
                if (changed) {
                    updatedCount++;
                    if (s.favorite == null && local.getFavorite() == null) clearedFavCount++;
                    if (s.history == null && local.getHistory() == null) clearedHistCount++;
                    // 合并后根据本地实际状态通知 UI（可能被清除了收藏/历史）
                    if (local.getFavorite() != null) {
                        favoriteList.add(new MiniComic(local));
                    }
                    if (local.getHistory() != null) {
                        historyList.add(new MiniComic(local));
                    }
                }
            }
        }

        Log.d(TAG, "[Sync] Download: inserted=" + insertedCount + " updated=" + updatedCount
                + " clearedFav=" + clearedFavCount + " clearedHist=" + clearedHistCount);

        // 发送 RxBus 事件通知 UI 刷新
        if (!favoriteList.isEmpty()) {
            RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_COMIC_FAVORITE_RESTORE, favoriteList));
        }
        if (!historyList.isEmpty()) {
            RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_COMIC_HISTORY_RESTORE, historyList));
        }
    }

    /** 敏感 key 列表：不上传到服务器，也不从服务器覆盖本地 */
    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            PreferenceManager.PREFERENCES_USER_TOCKEN,
            PreferenceManager.PREFERENCES_USER_NAME,
            PreferenceManager.PREFERENCES_USER_PASSWORD,
            PreferenceManager.PREFERENCES_USER_EMAIL,
            PreferenceManager.PREFERENCES_USER_ID,
            PreferenceManager.PREF_DATA_SERVER_URL,
            PreferenceManager.PREF_DATA_SERVER_AUTO_SYNC,
            PreferenceManager.PREF_OTHER_STORAGE
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
    private boolean mergeServerComic(Comic local, DataSyncModels.ComicServerItem s) {
        boolean changed = false;
        String key = s.source + ":" + s.cid;
        // 如果本地明确标记了"收藏已取消"，则不从服务端恢复收藏
        boolean favoriteDeleted = isFavoriteDeleted(s.source, s.cid);
        if (favoriteDeleted) {
            // 本地已取消收藏 → 保持本地 null，不恢复
            Log.d(TAG, "[Sync] Merge " + key + ": skip restore favorite (local deleted)");
        } else if (s.favorite != null) {
            // 服务端有收藏 → 取较新的
            if (local.getFavorite() == null || s.favorite > local.getFavorite()) {
                local.setFavorite(s.favorite);
                changed = true;
                Log.d(TAG, "[Sync] Merge " + key + ": update favorite to " + s.favorite);
            }
        } else if (local.getFavorite() != null) {
            // 服务端收藏为 null（另一台设备取消了收藏）→ 同步清除本地
            local.setFavorite(null);
            changed = true;
            Log.d(TAG, "[Sync] Merge " + key + ": clear favorite (server has null)");
        }
        // 如果本地明确标记了"历史已删除"，则不从服务端恢复历史
        boolean historyDeleted = isHistoryDeleted(s.source, s.cid);
        if (historyDeleted) {
            // 本地已删除历史 → 保持本地 null，不恢复
            Log.d(TAG, "[Sync] Merge " + key + ": skip restore history (local deleted)");
        } else if (s.history != null) {
            // 服务端有历史 → 取较新的
            if (local.getHistory() == null || s.history > local.getHistory()) {
                local.setHistory(s.history);
                local.setLast(s.last);
                local.setPage(s.page);
                local.setChapter(s.chapter);
                changed = true;
                Log.d(TAG, "[Sync] Merge " + key + ": update history to " + s.history);
            }
        } else if (local.getHistory() != null) {
            // 服务端历史为 null（另一台设备清除了历史）→ 同步清除本地
            local.setHistory(null);
            local.setLast(null);
            local.setPage(null);
            local.setChapter(null);
            changed = true;
            Log.d(TAG, "[Sync] Merge " + key + ": clear history (server has null)");
        }
        if ((local.getTitle() == null || local.getTitle().isEmpty()) && s.title != null) {
            local.setTitle(s.title);
            local.setCover(s.cover);
            local.setUpdate(s.update);
            local.setFinish(s.finish);
            if (s.chapter_count != null) local.setChapterCount(s.chapter_count);
            changed = true;
        }
        if (changed) {
            mComicManager.update(local);
            Log.d(TAG, "[Sync] Merge " + key + ": saved changes to local DB");
        }
        return changed;
    }

    // ==================== 历史删除标记追踪 ====================

    private static final String PREF_HISTORY_DELETED_KEYS = "history_deleted_keys";
    private static final String PREF_FAVORITE_DELETED_KEYS = "favorite_deleted_keys";

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

    /**
     * 获取所有"收藏已取消"标记（供 BackupPresenter 上传使用）
     */
    public static Set<String> getFavoriteDeletedKeysForUpload() {
        return getFavoriteDeletedKeys();
    }

    /**
     * 供 BackupPresenter 上传成功后调用的公开清除方法
     */
    public static void clearFavoriteDeletedKeysAfterUpload() {
        clearFavoriteDeletedKeys();
    }

    // ==================== 收藏删除标记追踪 ====================

    /**
     * 添加一条"收藏已取消"的漫画标记（source:cid）
     */
    public static void markFavoriteDeleted(int source, String cid) {
        Set<String> keys = getFavoriteDeletedKeys();
        keys.add(source + ":" + cid);
        saveFavoriteDeletedKeys(keys);
    }

    /**
     * 检查某漫画是否被标记为"收藏已取消"
     */
    public static boolean isFavoriteDeleted(int source, String cid) {
        return getFavoriteDeletedKeys().contains(source + ":" + cid);
    }

    /**
     * 获取所有"收藏已取消"标记
     */
    private static Set<String> getFavoriteDeletedKeys() {
        String json = App.getPreferenceManager().getString(PREF_FAVORITE_DELETED_KEYS, "");
        if (json.isEmpty()) return new HashSet<>();
        Set<String> result = new HashSet<>();
        for (String key : json.split(",")) {
            String trimmed = key.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 持久化"收藏已取消"标记集合
     */
    private static void saveFavoriteDeletedKeys(Set<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (sb.length() > 0) sb.append(",");
            sb.append(key);
        }
        App.getPreferenceManager().putString(PREF_FAVORITE_DELETED_KEYS, sb.toString());
    }

    /**
     * 清除所有"收藏已取消"标记（上传成功后调用）
     */
    private static void clearFavoriteDeletedKeys() {
        App.getPreferenceManager().putString(PREF_FAVORITE_DELETED_KEYS, "");
    }

}
