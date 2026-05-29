package com.xyrlsz.xcimocob.ui.view;

import com.xyrlsz.xcimocob.component.DialogCaller;

/**
 * Created by Hiroshi on 2016/10/19.
 */

public interface BackupView extends BaseView, DialogCaller {

    void onBackupSaveSuccess(int size);

    void onBackupSaveFail();

    void onBackupRestoreSuccess();

    void onBackupRestoreFail();

    void onComicFileLoadSuccess(String[] file);

    void onTagFileLoadSuccess(String[] file);

    void onSettingsFileLoadSuccess(String[] file);

    void onClearFileLoadSuccess(String[] file);

    void onFileLoadFail();

    void onClearBackupSuccess();

    void onClearBackupFail();

    void onUploadSuccess();

    void onUploadFail();

    // ========== 数据同步服务器回调（上传） ==========

    void onDataSyncLoginSuccess(String username);

    void onDataSyncLogoutSuccess();

    void onDataSyncStart();

    void onDataSyncComicSuccess(int synced, int skipped);

    void onDataSyncSettingSuccess(int synced);

    void onDataSyncTagSuccess();

    void onDataSyncAllSuccess();

    void onDataSyncError(String message);

    // ========== 数据同步服务器回调（下载/恢复） ==========

    void onDataSyncDownloadStart();

    void onDataSyncDownloadComicSuccess(int count);

    void onDataSyncDownloadSettingSuccess(int count);

    void onDataSyncDownloadTagSuccess();

    void onDataSyncDownloadAllSuccess();

    void onDataSyncDownloadError(String message);
}
