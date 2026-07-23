package com.xyrlsz.xcimocob.presenter;

import static com.xyrlsz.xcimocob.utils.WebDavUtils.upload2WebDav;

import android.content.ContentResolver;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.core.Backup;
import com.xyrlsz.xcimocob.manager.ComicManager;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.manager.TagManager;
import com.xyrlsz.xcimocob.manager.TagRefManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.MiniComic;
import com.xyrlsz.xcimocob.model.Tag;
import com.xyrlsz.xcimocob.model.TagRef;
import com.xyrlsz.xcimocob.network.sync.DataSyncClient;
import com.xyrlsz.xcimocob.network.sync.DataSyncManager;
import com.xyrlsz.xcimocob.network.sync.DataSyncModels;
import com.xyrlsz.xcimocob.rx.RxBus;
import com.xyrlsz.xcimocob.rx.RxEvent;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;
import com.xyrlsz.xcimocob.saf.WebDavCimocDocumentFile;
import com.xyrlsz.xcimocob.ui.view.BackupView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Hiroshi on 2016/10/19.
 */

public class BackupPresenter extends BasePresenter<BackupView> {

    private ComicManager mComicManager;
    private TagManager mTagManager;
    private TagRefManager mTagRefManager;
    private ContentResolver mContentResolver;

    @Override
    protected void onViewAttach() {
        mComicManager = ComicManager.getInstance(mBaseView);
        mTagManager = TagManager.getInstance(mBaseView);
        mTagRefManager = TagRefManager.getInstance(mBaseView);
        mContentResolver = mBaseView.getAppInstance().getContentResolver();
    }

    public void loadComicFile(CimocDocumentFile root) {
        mCompositeSubscription.add(Backup.loadFavorite(root)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(file -> mBaseView.onComicFileLoadSuccess(file), throwable -> mBaseView.onFileLoadFail()));
    }

    public void loadTagFile(CimocDocumentFile root) {
        mCompositeSubscription.add(Backup.loadTag(root)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(file -> mBaseView.onTagFileLoadSuccess(file), throwable -> mBaseView.onFileLoadFail()));
    }

    public void loadSettingsFile(CimocDocumentFile root) {
        mCompositeSubscription.add(Backup.loadSettings(root)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(file -> mBaseView.onSettingsFileLoadSuccess(file), throwable -> mBaseView.onFileLoadFail()));
    }

    public void loadClearBackupFile(CimocDocumentFile root) {
        mCompositeSubscription.add(Backup.loadClearBackup(root)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(file -> mBaseView.onClearFileLoadSuccess(file), throwable -> mBaseView.onFileLoadFail()));
    }

    public void saveComic(CimocDocumentFile root) {
        mCompositeSubscription.add(mComicManager.listFavoriteOrHistoryInRx()
                .map(list -> Backup.saveComic(mContentResolver, root, list)).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(size -> {
                    if (size == -1) {
                        mBaseView.onBackupSaveFail();
                    } else {
                        mBaseView.onBackupSaveSuccess(size);
                    }
                }, throwable -> mBaseView.onBackupSaveFail()));
    }

    public void saveTag(CimocDocumentFile root) {
        mCompositeSubscription.add(Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<Integer>) emitter -> {
                    int size = groupAndSaveComicByTag(root);
                    emitter.onNext(size);
                    emitter.onComplete();
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(size -> {
                    if (size == -1) {
                        mBaseView.onBackupSaveFail();
                    } else {
                        mBaseView.onBackupSaveSuccess(size);
                    }
                }, throwable -> mBaseView.onBackupSaveFail()));
    }

    public void saveSettings(CimocDocumentFile root) {
        mCompositeSubscription.add(Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<Integer>) emitter -> {
                        int size = Backup.saveSetting(mContentResolver, root, App.getPreferenceManager().getAll());
                        emitter.onNext(size);
                        emitter.onComplete();
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(size -> {
                    if (size == -1) {
                        mBaseView.onBackupSaveFail();
                    } else {
                        mBaseView.onBackupSaveSuccess(size);
                    }
                }, throwable -> mBaseView.onBackupSaveFail()));
    }

    public void restoreComic(String filename, CimocDocumentFile root) {
        mCompositeSubscription.add(Backup.restoreComic(mContentResolver, root, filename)
                .doOnNext(this::filterAndPostComic)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> mBaseView.onBackupRestoreSuccess(), new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        throwable.printStackTrace();
                        mBaseView.onBackupRestoreFail();
                    }
                }));
    }

    public void restoreTag(String filename, CimocDocumentFile root) {
        mCompositeSubscription.add(Backup.restoreTag(mContentResolver, root, filename)
                .doOnNext(this::updateAndPostTag)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pair -> mBaseView.onBackupRestoreSuccess(), new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        mBaseView.onBackupRestoreFail();
                    }
                }));
    }

    public void restoreSetting(String filename, CimocDocumentFile root) {
        mCompositeSubscription.add(Backup.restoreSetting(mContentResolver, root, filename)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pair -> mBaseView.onBackupRestoreSuccess(), throwable -> mBaseView.onBackupRestoreFail()));
    }

    public void clearBackup(CimocDocumentFile root) {
        mCompositeSubscription.add(Backup.clearBackup(root)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pair -> mBaseView.onClearBackupSuccess(), throwable -> mBaseView.onClearBackupFail()));
    }

    public void deleteBackup(String filename, CimocDocumentFile root) {
        mCompositeSubscription.add(Backup.deleteBackup(root, filename)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pair -> mBaseView.onClearBackupSuccess(), throwable -> mBaseView.onClearBackupFail()));
    }

    public void uploadBackup2Cloud(CimocDocumentFile src, WebDavCimocDocumentFile dst) {
        mCompositeSubscription.add(upload2WebDav(src, dst, true)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pair -> mBaseView.onUploadSuccess(), throwable -> mBaseView.onUploadFail()));
    }

    private List<Tag> setTagsId(final List<Pair<Tag, List<Comic>>> list) {
        final List<Tag> tags = new LinkedList<>();
        mTagRefManager.runInTx(() -> {
            for (Pair<Tag, List<Comic>> pair : list) {
                Tag tag = mTagManager.load(pair.first.getTitle());
                if (tag == null) {
                    mTagManager.insert(pair.first);
                    tags.add(pair.first);
                } else {
                    pair.first.setId(tag.getId());
                }
            }
        });
        return tags;
    }

    private void updateAndPostTag(final List<Pair<Tag, List<Comic>>> list) {
        List<Tag> tags = setTagsId(list);
        for (Pair<Tag, List<Comic>> pair : list) {
            filterAndPostComic(pair.second);
        }
        mTagRefManager.runInTx(() -> {
            for (Pair<Tag, List<Comic>> pair : list) {
                long tid = pair.first.getId();
                for (Comic comic : pair.second) {
                    TagRef ref = mTagRefManager.load(tid, comic.getId());
                    if (ref == null) {
                        mTagRefManager.insert(new TagRef(null, tid, comic.getId()));
                    }
                }
            }
        });
        RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_TAG_RESTORE, tags));
    }

    private int groupAndSaveComicByTag(CimocDocumentFile file) {
        final List<Pair<Tag, List<Comic>>> list = new LinkedList<>();
        mComicManager.runInTx(() -> {
            for (Tag tag : mTagManager.list()) {
                List<Comic> comics = new LinkedList<>();
                Pair<Tag, List<Comic>> pair = Pair.create(tag, comics);
                for (TagRef ref : mTagRefManager.listByTag(tag.getId())) {
                    comics.add(mComicManager.load(ref.getCid()));
                }
                list.add(pair);
            }
        });
        return Backup.saveTag(mContentResolver, file, list);
    }

    private void filterAndPostComic(final List<Comic> list) {
        final List<Comic> favorite = new LinkedList<>();
        final List<Comic> history = new LinkedList<>();
        mComicManager.runInTx(() -> {
            for (Comic comic : list) {
                Comic temp = mComicManager.load(comic.getSource(), comic.getCid());
                if (temp == null) {
                    mComicManager.insert(comic);
                    if (comic.getHistory() != null) {
                        history.add(comic);
                    }
                    if (comic.getFavorite() != null) {
                        favorite.add(comic);
                    }
                } else {
                    if (temp.getFavorite() == null || temp.getHistory() == null) {
                        if (temp.getFavorite() == null && comic.getFavorite() != null) {
                            temp.setFavorite(comic.getFavorite());
                            favorite.add(comic);
                        }
                        if (temp.getHistory() == null && comic.getHistory() != null) {
                            temp.setHistory(comic.getHistory());
                            if (temp.getLast() == null) {
                                temp.setLast(comic.getLast());
                                temp.setPage(comic.getPage());
                                temp.setChapter(comic.getChapter());
                            }
                            history.add(comic);
                        }
                        mComicManager.update(temp);
                    } else if (!Objects.equals(temp.getHistory(), comic.getHistory())) {
                        temp.setHistory(comic.getHistory());
                        temp.setLast(comic.getLast());
                        temp.setPage(comic.getPage());
                        temp.setChapter(comic.getChapter());

                        mComicManager.update(temp);
                    }
                    // TODO 可能要设置其他域
                    comic.setId(temp.getId());
                    // mComicManager.update(temp);
                }
            }
        });
        postComic(favorite, history);
    }

    private void postComic(List<Comic> favorite, List<Comic> history) {
/*        Collections.sort(favorite, new Comparator<Comic>() {
            @Override
            public int compare(Comic lhs, Comic rhs) {
                return (int) (lhs.getFavorite() - rhs.getFavorite());
            }
        });
        Collections.sort(history, new Comparator<Comic>() {
            @Override
            public int compare(Comic lhs, Comic rhs) {
                return (int) (lhs.getHistory() - rhs.getHistory());
            }
        }); */
        RxBus.getInstance().post(
                new RxEvent(RxEvent.EVENT_COMIC_FAVORITE_RESTORE, convertToMiniComic(favorite)));
        RxBus.getInstance().post(
                new RxEvent(RxEvent.EVENT_COMIC_HISTORY_RESTORE, convertToMiniComic(history)));
    }

    private List<MiniComic> convertToMiniComic(List<Comic> list) {
        List<MiniComic> result = new ArrayList<>(list.size());
        for (Comic comic : list) {
            result.add(new MiniComic(comic));
        }
        return result;
    }

    // ==================== 数据同步服务器 ====================

    /**
     * 获取当前 token
     */
    private String getToken() {
        return App.getPreferenceManager().getString(PreferenceManager.PREFERENCES_USER_TOCKEN, "");
    }

    /**
     * 获取 DataSyncClient 实例，如果未配置则回调错误
     */
    private DataSyncClient getClientOrError() {
        String serverUrl = App.getPreferenceManager().getString(PreferenceManager.PREF_DATA_SERVER_URL, "");
        if (TextUtils.isEmpty(serverUrl)) {
            mBaseView.onDataSyncError(mBaseView.getAppInstance().getString(
                    com.xyrlsz.xcimocob.R.string.data_sync_server_not_configured));
            return null;
        }
        return new DataSyncClient(serverUrl);
    }

    /**
     * 登录到数据同步服务器
     */
    public void dataSyncLogin(final String username, final String password) {
        final DataSyncClient client = getClientOrError();
        if (client == null) return;

        mCompositeSubscription.add(Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<DataSyncModels.LoginResponse>) emitter -> {
            try {
                DataSyncModels.LoginResponse resp = client.login(username, password);
                emitter.onNext(resp);
                emitter.onComplete();
            } catch (Exception e) {
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(resp -> {
            // 保存 token 和用户信息
            PreferenceManager pm = App.getPreferenceManager();
            pm.putString(PreferenceManager.PREFERENCES_USER_TOCKEN, resp.token);
            pm.putString(PreferenceManager.PREFERENCES_USER_NAME, resp.user.username);
            pm.putString(PreferenceManager.PREFERENCES_USER_PASSWORD, password);
            pm.putString(PreferenceManager.PREFERENCES_USER_ID, String.valueOf(resp.user.id));
            mBaseView.onDataSyncLoginSuccess(resp.user.username);
            // 登录成功后立即触发一次双向同步
            DataSyncManager.getInstance().triggerNow();
        }, e -> {
            String msg = getErrorMessage(e);
            mBaseView.onDataSyncError(msg);
        }));
    }

    /**
     * 注销
     */
    public void dataSyncLogout() {
        PreferenceManager pm = App.getPreferenceManager();
        pm.putString(PreferenceManager.PREFERENCES_USER_TOCKEN, "");
        pm.putString(PreferenceManager.PREFERENCES_USER_NAME, "");
        pm.putString(PreferenceManager.PREFERENCES_USER_PASSWORD, "");
        pm.putString(PreferenceManager.PREFERENCES_USER_ID, "");
        mBaseView.onDataSyncLogoutSuccess();
    }

    /**
     * 同步漫画（收藏+历史）到服务器
     */
    public void dataSyncComic() {
        final DataSyncClient client = getClientOrError();
        if (client == null) return;

        mBaseView.onDataSyncStart();

        mCompositeSubscription.add(mComicManager.listFavoriteOrHistoryInRx()
                .map(comics -> {
                    List<DataSyncModels.ComicSyncItem> items = new ArrayList<>(comics.size() + 8);
                    // 记录已出现在常规上传中的漫画 key，避免与清除标记冲突
                    Set<String> uploadedKeys = new HashSet<>();
                    for (Comic comic : comics) {
                        DataSyncModels.ComicSyncItem item = new DataSyncModels.ComicSyncItem();
                        item.source = comic.getSource();
                        item.cid = comic.getCid();
                        item.title = comic.getTitle();
                        item.cover = comic.getCover();
                        item.update = comic.getUpdate();
                        item.finish = comic.getFinish() != null && comic.getFinish();
                        item.favorite = comic.getFavorite();
                        item.history = comic.getHistory();
                        item.last = comic.getLast();
                        item.page = comic.getPage();
                        item.chapter = comic.getChapter();
                        item.chapter_count = comic.getChapterCount();
                        items.add(item);
                        uploadedKeys.add(comic.getSource() + ":" + comic.getCid());
                    }
                    // 附加标记了"历史已删除"的漫画，通知服务端清除历史
                    Set<String> deletedKeys = DataSyncManager.getHistoryDeletedKeysForUpload();
                    if (!deletedKeys.isEmpty()) {
                        for (String key : deletedKeys) {
                            String[] parts = key.split(":", 2);
                            if (parts.length != 2) continue;

                            int source;
                            try {
                                source = Integer.parseInt(parts[0]);
                            } catch (NumberFormatException e) {
                                Log.w("BackupPresenter", "Invalid history deleted key (source not int): " + key);
                                continue;
                            }
                            String cid = parts[1];

                            if (uploadedKeys.contains(key)) {
                                // 该漫画在本地仍有数据（如收藏），但用户已清除历史记录
                                // 需要标记 clear_history，让服务端也清除历史，避免下次下载时恢复
                                for (DataSyncModels.ComicSyncItem item : items) {
                                    if (item.source == source && item.cid != null && item.cid.equals(cid)) {
                                        item.clear_history = true;
                                        break;
                                    }
                                }
                            } else {
                                DataSyncModels.ComicSyncItem delItem = new DataSyncModels.ComicSyncItem();
                                delItem.source = source;
                                delItem.cid = cid;
                                delItem.clear_history = true;
                                items.add(delItem);
                            }
                        }
                    }

                    // 附加标记了"收藏已取消"的漫画，通知服务端清除收藏
                    Set<String> favoriteDeletedKeys = DataSyncManager.getFavoriteDeletedKeysForUpload();
                    if (!favoriteDeletedKeys.isEmpty()) {
                        for (String key : favoriteDeletedKeys) {
                            String[] parts = key.split(":", 2);
                            if (parts.length != 2) continue;

                            int source;
                            try {
                                source = Integer.parseInt(parts[0]);
                            } catch (NumberFormatException e) {
                                Log.w("BackupPresenter", "Invalid favorite deleted key (source not int): " + key);
                                continue;
                            }
                            String cid = parts[1];

                            if (uploadedKeys.contains(key)) {
                                // 该漫画在本地仍有数据（如历史记录），但用户已取消收藏
                                // 需要标记 clear_favorite，让服务端也清除收藏，避免下次下载时恢复
                                for (DataSyncModels.ComicSyncItem item : items) {
                                    if (item.source == source && item.cid != null && item.cid.equals(cid)) {
                                        item.clear_favorite = true;
                                        break;
                                    }
                                }
                            } else {
                                DataSyncModels.ComicSyncItem delItem = new DataSyncModels.ComicSyncItem();
                                delItem.source = source;
                                delItem.cid = cid;
                                delItem.clear_favorite = true;
                                items.add(delItem);
                            }
                        }
                    }
                    return items;
                })
                .flatMap((Function<List<DataSyncModels.ComicSyncItem>, ObservableSource<DataSyncModels.ComicSyncResponse>>) items -> {
                    // 在 IO 线程获取有效 token（自动刷新过期 token）
                    String token = DataSyncClient.ensureValidToken();
                    if (token == null) {
                        return Observable.error(
                                new IOException(mBaseView.getAppInstance().getString(
                                        R.string.data_sync_not_logged_in)));
                    }
                    try {
                        DataSyncModels.ComicSyncResponse resp = client.syncComics(token, items);
                        return Observable.just(resp);
                    } catch (Exception e) {
                        return Observable.error(e);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(resp -> {
                    // 上传成功后清除所有删除标记
                    DataSyncManager.clearHistoryDeletedKeysAfterUpload();
                    DataSyncManager.clearFavoriteDeletedKeysAfterUpload();
                    mBaseView.onDataSyncComicSuccess(resp.synced, resp.skipped);
                }, e -> mBaseView.onDataSyncError(getErrorMessage(e))));
    }

    /**
     * 同步设置到服务器
     */
    public void dataSyncSetting() {
        final DataSyncClient client = getClientOrError();
        if (client == null) return;

        mBaseView.onDataSyncStart();

        mCompositeSubscription.add(Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<DataSyncModels.SettingSyncResponse>) emitter -> {
                    // 在 IO 线程获取有效 token（自动刷新过期 token）
                    String token = DataSyncClient.ensureValidToken();
                    if (token == null) {
                        emitter.onError(new IOException(mBaseView.getAppInstance().getString(
                                com.xyrlsz.xcimocob.R.string.data_sync_not_logged_in)));
                        return;
                    }
                    Map<String, ?> allPrefs = App.getPreferenceManager().getAll();
                    List<DataSyncModels.SettingItem> items = new ArrayList<>();
                    for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                        if (entry.getValue() != null && !SENSITIVE_KEYS.contains(entry.getKey())) {
                            items.add(new DataSyncModels.SettingItem(entry.getKey(), entry.getValue().toString()));
                        }
                    }
                    try {
                        DataSyncModels.SettingSyncResponse resp = client.syncSettings(token, items);
                        emitter.onNext(resp);
                    } catch (Exception e) {
                        emitter.onError(e);
                    }
                    emitter.onComplete();
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(resp -> mBaseView.onDataSyncSettingSuccess(resp.synced), e -> mBaseView.onDataSyncError(getErrorMessage(e))));
    }

    /**
     * 全部同步（漫画+设置）
     */
    public void dataSyncAll() {
        final DataSyncClient client = getClientOrError();
        if (client == null) return;

        mBaseView.onDataSyncStart();

        mCompositeSubscription.add(Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<Boolean>) emitter -> {
                    // 在 IO 线程获取有效 token（自动刷新过期 token）
                    String token = DataSyncClient.ensureValidToken();
                    if (token == null) {
                        emitter.onError(new IOException(mBaseView.getAppInstance().getString(
                                com.xyrlsz.xcimocob.R.string.data_sync_not_logged_in)));
                        return;
                    }
                    try {
                        // 1. 同步漫画
                        List<Comic> comics = mComicManager.listFavoriteOrHistory();
                        List<DataSyncModels.ComicSyncItem> comicItems = new ArrayList<>(comics.size() + 8);
                        // 记录已出现在常规上传中的漫画 key，避免与清除标记冲突
                        Set<String> uploadedKeys = new HashSet<>();
                        for (Comic comic : comics) {
                            DataSyncModels.ComicSyncItem item = new DataSyncModels.ComicSyncItem();
                            item.source = comic.getSource();
                            item.cid = comic.getCid();
                            item.title = comic.getTitle();
                            item.cover = comic.getCover();
                            item.update = comic.getUpdate();
                            item.finish = comic.getFinish() != null && comic.getFinish();
                            item.favorite = comic.getFavorite();
                            item.history = comic.getHistory();
                            item.last = comic.getLast();
                            item.page = comic.getPage();
                            item.chapter = comic.getChapter();
                            item.chapter_count = comic.getChapterCount();
                            comicItems.add(item);
                            uploadedKeys.add(comic.getSource() + ":" + comic.getCid());
                        }

                        // 附加标记了"历史已删除"的漫画
                        Set<String> deletedKeys = DataSyncManager.getHistoryDeletedKeysForUpload();
                        if (!deletedKeys.isEmpty()) {
                            for (String key : deletedKeys) {
                                String[] parts = key.split(":", 2);
                                if (parts.length != 2) continue;

                                int source;
                                try {
                                    source = Integer.parseInt(parts[0]);
                                } catch (NumberFormatException e) {
                                    Log.w("BackupPresenter", "Invalid history deleted key (source not int): " + key);
                                    continue;
                                }
                                String cid = parts[1];

                                if (uploadedKeys.contains(key)) {
                                    for (DataSyncModels.ComicSyncItem item : comicItems) {
                                        if (item.source == source && item.cid != null && item.cid.equals(cid)) {
                                            item.clear_history = true;
                                            break;
                                        }
                                    }
                                } else {
                                    DataSyncModels.ComicSyncItem delItem = new DataSyncModels.ComicSyncItem();
                                    delItem.source = source;
                                    delItem.cid = cid;
                                    delItem.clear_history = true;
                                    comicItems.add(delItem);
                                }
                            }
                        }

                        // 附加标记了"收藏已取消"的漫画
                        Set<String> favoriteDeletedKeys = DataSyncManager.getFavoriteDeletedKeysForUpload();
                        if (!favoriteDeletedKeys.isEmpty()) {
                            for (String key : favoriteDeletedKeys) {
                                String[] parts = key.split(":", 2);
                                if (parts.length != 2) continue;

                                int source;
                                try {
                                    source = Integer.parseInt(parts[0]);
                                } catch (NumberFormatException e) {
                                    Log.w("BackupPresenter", "Invalid favorite deleted key (source not int): " + key);
                                    continue;
                                }
                                String cid = parts[1];

                                if (uploadedKeys.contains(key)) {
                                    for (DataSyncModels.ComicSyncItem item : comicItems) {
                                        if (item.source == source && item.cid != null && item.cid.equals(cid)) {
                                            item.clear_favorite = true;
                                            break;
                                        }
                                    }
                                } else {
                                    DataSyncModels.ComicSyncItem delItem = new DataSyncModels.ComicSyncItem();
                                    delItem.source = source;
                                    delItem.cid = cid;
                                    delItem.clear_favorite = true;
                                    comicItems.add(delItem);
                                }
                            }
                        }

                        client.syncComics(token, comicItems);

                        // 上传成功后清除所有删除标记
                        if (!deletedKeys.isEmpty()) {
                            DataSyncManager.clearHistoryDeletedKeysAfterUpload();
                        }
                        if (!favoriteDeletedKeys.isEmpty()) {
                            DataSyncManager.clearFavoriteDeletedKeysAfterUpload();
                        }

                        // 2. 同步设置（过滤敏感 key）
                        Map<String, ?> allPrefs = App.getPreferenceManager().getAll();
                        List<DataSyncModels.SettingItem> settingItems = new ArrayList<>();
                        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                            if (entry.getValue() != null && !SENSITIVE_KEYS.contains(entry.getKey())) {
                                settingItems.add(new DataSyncModels.SettingItem(entry.getKey(), entry.getValue().toString()));
                            }
                        }
                        client.syncSettings(token, settingItems);

                        emitter.onNext(true);
                        emitter.onComplete();
                    } catch (Exception e) {
                        emitter.onError(e);
                    }
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(success -> mBaseView.onDataSyncAllSuccess(), e -> mBaseView.onDataSyncError(getErrorMessage(e))));
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

    // ==================== 从服务器下载/恢复 ====================

    /**
     * 从服务器恢复漫画（下载 + 合并到本地）
     */
    public void dataSyncDownloadComic() {
        final DataSyncClient client = getClientOrError();
        if (client == null) return;

        mBaseView.onDataSyncDownloadStart();

        mCompositeSubscription.add(Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<Integer>) emitter -> {
                    // 在 IO 线程获取有效 token（自动刷新过期 token）
                    String token = DataSyncClient.ensureValidToken();
                    if (token == null) {
                        emitter.onError(new IOException(mBaseView.getAppInstance().getString(
                                com.xyrlsz.xcimocob.R.string.data_sync_not_logged_in)));
                        return;
                    }
                    try {
                        List<DataSyncModels.ComicServerItem> serverComics = client.listComics(token);
                        if (serverComics == null) {
                            emitter.onNext(0);
                            emitter.onComplete();
                            return;
                        }

                        final List<Comic> favoriteList = new LinkedList<>();
                        final List<Comic> historyList = new LinkedList<>();

                        mComicManager.runInTx(() -> {
                            for (DataSyncModels.ComicServerItem item : serverComics) {
                                // 按 (source, cid) 查找本地漫画
                                Comic local = mComicManager.load(item.source, item.cid);
                                if (local == null) {
                                    // 本地没有 → 新建
                                    local = new Comic();
                                    local.setId(0);
                                    local.setSource(item.source);
                                    local.setCid(item.cid);
                                    local.setTitle(item.title);
                                    local.setCover(item.cover);
                                    local.setUpdate(item.update);
                                    local.setFinish(item.finish);
                                    local.setFavorite(item.favorite);
                                    local.setHistory(item.history);
                                    local.setLast(item.last);
                                    local.setPage(item.page);
                                    local.setChapter(item.chapter);
                                    if (item.chapter_count != null) {
                                        local.setChapterCount(item.chapter_count);
                                    }
                                    mComicManager.insert(local);
                                } else {
                                    // 本地已有 → 合并
                                    boolean changed = false;

                                    // 用服务器端收藏信息合并
                                    boolean favoriteDeleted = DataSyncManager.isFavoriteDeleted(item.source, item.cid);
                                    if (favoriteDeleted) {
                                        // 本地已取消收藏 → 不恢复
                                    } else if (item.favorite != null) {
                                        // 服务端有收藏 → 取较新的
                                        if (local.getFavorite() == null || item.favorite > local.getFavorite()) {
                                            local.setFavorite(item.favorite);
                                            changed = true;
                                        }
                                    } else if (local.getFavorite() != null) {
                                        // 服务端收藏为 null（另一台设备取消了收藏）→ 同步清除本地
                                        local.setFavorite(null);
                                        changed = true;
                                    }

                                    // 用服务器端历史信息合并
                                    boolean historyDeleted = DataSyncManager.isHistoryDeleted(item.source, item.cid);
                                    if (historyDeleted) {
                                        // 本地已删除历史 → 不恢复
                                    } else if (item.history != null) {
                                        // 服务端有历史 → 取较新的
                                        if (local.getHistory() == null || item.history > local.getHistory()) {
                                            local.setHistory(item.history);
                                            local.setLast(item.last);
                                            local.setPage(item.page);
                                            local.setChapter(item.chapter);
                                            changed = true;
                                        }
                                    } else if (local.getHistory() != null) {
                                        // 服务端历史为 null（另一台设备清除了历史）→ 同步清除本地
                                        local.setHistory(null);
                                        local.setLast(null);
                                        local.setPage(null);
                                        local.setChapter(null);
                                        changed = true;
                                    }

                                    // 如果本地没有标题/封面，用服务器的填充
                                    if (local.getTitle() == null || local.getTitle().isEmpty()) {
                                        local.setTitle(item.title);
                                        local.setCover(item.cover);
                                        local.setUpdate(item.update);
                                        local.setFinish(item.finish);
                                        if (item.chapter_count != null) {
                                            local.setChapterCount(item.chapter_count);
                                        }
                                        changed = true;
                                    }
                                    if (changed) {
                                        mComicManager.update(local);
                                    }
                                }

                                // 记录用于发 RxBus 事件（根据合并后的实际本地状态）
                                if (local.getFavorite() != null) {
                                    favoriteList.add(local);
                                }
                                if (local.getHistory() != null) {
                                    historyList.add(local);
                                }
                            }
                        });

                        // 发送事件通知 UI 刷新
                        if (!favoriteList.isEmpty()) {
                            RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_COMIC_FAVORITE_RESTORE, convertToMiniComic(favoriteList)));
                        }
                        if (!historyList.isEmpty()) {
                            RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_COMIC_HISTORY_RESTORE, convertToMiniComic(historyList)));
                        }

                        emitter.onNext(serverComics.size());
                        emitter.onComplete();
                    } catch (Exception e) {
                        emitter.onError(e);
                    }
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(count -> mBaseView.onDataSyncDownloadComicSuccess(count), e -> mBaseView.onDataSyncDownloadError(getErrorMessage(e))));
    }

    /**
     * 从服务器恢复设置
     */
    public void dataSyncDownloadSetting() {
        final DataSyncClient client = getClientOrError();
        if (client == null) return;

        mBaseView.onDataSyncDownloadStart();

        mCompositeSubscription.add(Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<Integer>) emitter -> {
                    // 在 IO 线程获取有效 token（自动刷新过期 token）
                    String token = DataSyncClient.ensureValidToken();
                    if (token == null) {
                        emitter.onError(new IOException(mBaseView.getAppInstance().getString(
                                com.xyrlsz.xcimocob.R.string.data_sync_not_logged_in)));
                        return;
                    }
                    try {
                        List<DataSyncModels.SettingServerItem> serverSettings = client.listSettings(token);
                        if (serverSettings == null) {
                            emitter.onNext(0);
                            emitter.onComplete();
                            return;
                        }

                        PreferenceManager pm = App.getPreferenceManager();
                        int count = 0;
                        for (DataSyncModels.SettingServerItem item : serverSettings) {
                            if (item.key != null && item.value != null && !SENSITIVE_KEYS.contains(item.key)) {
                                pm.putObject(item.key, item.value);
                                count++;
                            }
                        }

                        emitter.onNext(count);
                        emitter.onComplete();
                    } catch (Exception e) {
                        emitter.onError(e);
                    }
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(count -> mBaseView.onDataSyncDownloadSettingSuccess(count), e -> mBaseView.onDataSyncDownloadError(getErrorMessage(e))));
    }

    /**
     * 从服务器恢复全部数据
     */
    public void dataSyncDownloadAll() {
        final DataSyncClient client = getClientOrError();
        if (client == null) return;

        mBaseView.onDataSyncDownloadStart();

        mCompositeSubscription.add(Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<Boolean>) emitter -> {
                    // 在 IO 线程获取有效 token（自动刷新过期 token）
                    String token = DataSyncClient.ensureValidToken();
                    if (token == null) {
                        emitter.onError(new IOException(mBaseView.getAppInstance().getString(
                                com.xyrlsz.xcimocob.R.string.data_sync_not_logged_in)));
                        return;
                    }
                    try {
                        // 1. 恢复漫画
                        List<DataSyncModels.ComicServerItem> serverComics = client.listComics(token);
                        if (serverComics != null) {
                            final List<Comic> favoriteList = new LinkedList<>();
                            final List<Comic> historyList = new LinkedList<>();
                            mComicManager.runInTx(() -> {
                                for (DataSyncModels.ComicServerItem item : serverComics) {
                                    Comic local = mComicManager.load(item.source, item.cid);
                                    if (local == null) {
                                        local = new Comic();
                                        local.setId(0);
                                        local.setSource(item.source);
                                        local.setCid(item.cid);
                                        local.setTitle(item.title);
                                        local.setCover(item.cover);
                                        local.setUpdate(item.update);
                                        local.setFinish(item.finish);
                                        local.setFavorite(item.favorite);
                                        local.setHistory(item.history);
                                        local.setLast(item.last);
                                        local.setPage(item.page);
                                        local.setChapter(item.chapter);
                                        if (item.chapter_count != null) {
                                            local.setChapterCount(item.chapter_count);
                                        }
                                        mComicManager.insert(local);
                                    } else {
                                        boolean changed = false;

                                        // 用服务器端收藏信息合并
                                        boolean favoriteDeleted = DataSyncManager.isFavoriteDeleted(item.source, item.cid);
                                        if (favoriteDeleted) {
                                            // 本地已取消收藏 → 不恢复
                                        } else if (item.favorite != null) {
                                            // 服务端有收藏 → 取较新的
                                            if (local.getFavorite() == null || item.favorite > local.getFavorite()) {
                                                local.setFavorite(item.favorite);
                                                changed = true;
                                            }
                                        } else if (local.getFavorite() != null) {
                                            // 服务端收藏为 null（另一台设备取消了收藏）→ 同步清除本地
                                            local.setFavorite(null);
                                            changed = true;
                                        }

                                        // 用服务器端历史信息合并
                                        boolean historyDeleted = DataSyncManager.isHistoryDeleted(item.source, item.cid);
                                        if (historyDeleted) {
                                            // 本地已删除历史 → 不恢复
                                        } else if (item.history != null) {
                                            // 服务端有历史 → 取较新的
                                            if (local.getHistory() == null || item.history > local.getHistory()) {
                                                local.setHistory(item.history);
                                                local.setLast(item.last);
                                                local.setPage(item.page);
                                                local.setChapter(item.chapter);
                                                changed = true;
                                            }
                                        } else if (local.getHistory() != null) {
                                            // 服务端历史为 null（另一台设备清除了历史）→ 同步清除本地
                                            local.setHistory(null);
                                            local.setLast(null);
                                            local.setPage(null);
                                            local.setChapter(null);
                                            changed = true;
                                        }

                                        if ((local.getTitle() == null || local.getTitle().isEmpty()) && item.title != null) {
                                            local.setTitle(item.title);
                                            local.setCover(item.cover);
                                            local.setUpdate(item.update);
                                            local.setFinish(item.finish);
                                            if (item.chapter_count != null) {
                                                local.setChapterCount(item.chapter_count);
                                            }
                                            changed = true;
                                        }
                                        if (changed) {
                                            mComicManager.update(local);
                                        }
                                    }
                                    if (local.getFavorite() != null) favoriteList.add(local);
                                    if (local.getHistory() != null) historyList.add(local);
                                }
                            });
                            if (!favoriteList.isEmpty()) {
                                RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_COMIC_FAVORITE_RESTORE, convertToMiniComic(favoriteList)));
                            }
                            if (!historyList.isEmpty()) {
                                RxBus.getInstance().post(new RxEvent(RxEvent.EVENT_COMIC_HISTORY_RESTORE, convertToMiniComic(historyList)));
                            }
                        }

                        // 2. 恢复设置（跳过敏感 key）
                        List<DataSyncModels.SettingServerItem> serverSettings = client.listSettings(token);
                        if (serverSettings != null) {
                            PreferenceManager pm = App.getPreferenceManager();
                            for (DataSyncModels.SettingServerItem item : serverSettings) {
                                if (item.key != null && item.value != null && !SENSITIVE_KEYS.contains(item.key)) {
                                    pm.putObject(item.key, item.value);
                                }
                            }
                        }

                        emitter.onNext(true);
                        emitter.onComplete();
                    } catch (Exception e) {
                        emitter.onError(e);
                    }
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(success -> mBaseView.onDataSyncDownloadAllSuccess(), e -> mBaseView.onDataSyncDownloadError(getErrorMessage(e))));
    }

    /**
     * 从异常中提取用户可读的错误信息
     */
    private String getErrorMessage(Throwable e) {
        if (e instanceof DataSyncClient.DataSyncException) {
            return e.getMessage();
        } else if (e instanceof java.net.ConnectException) {
            return "无法连接到服务器，请检查地址和网络";
        } else if (e instanceof java.net.SocketTimeoutException) {
            return "连接超时，请检查服务器状态";
        } else if (e instanceof java.net.UnknownHostException) {
            return "无法解析服务器地址";
        } else if (e instanceof java.io.IOException) {
            return "网络错误: " + e.getMessage();
        }
        return "同步失败: " + e.getMessage();
    }

}
