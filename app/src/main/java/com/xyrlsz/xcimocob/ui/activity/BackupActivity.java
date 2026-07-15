package com.xyrlsz.xcimocob.ui.activity;

import static com.xyrlsz.xcimocob.core.Backup.BACKUP;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.Constants;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.presenter.BackupPresenter;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;
import com.xyrlsz.xcimocob.saf.WebDavCimocDocumentFile;
import com.xyrlsz.xcimocob.ui.fragment.dialog.ChoiceDialogFragment;
import com.xyrlsz.xcimocob.ui.fragment.dialog.MessageDialogFragment;
import com.xyrlsz.xcimocob.ui.view.BackupView;
import com.xyrlsz.xcimocob.ui.widget.WebDavConfDialog;
import com.xyrlsz.xcimocob.ui.widget.preference.CheckBoxPreference;
import com.xyrlsz.xcimocob.utils.DocumentUtils;
import com.xyrlsz.xcimocob.utils.PermissionUtils;
import com.xyrlsz.xcimocob.utils.StringUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;



/**
 * Created by Hiroshi on 2016/10/19.
 */

public class BackupActivity extends BackActivity implements BackupView {

    public static final int DIALOG_REQUEST_RESTORE_DELETE = 4;
    private static final int DIALOG_REQUEST_RESTORE_COMIC = 0;
    private static final int DIALOG_REQUEST_RESTORE_TAG = 1;
    private static final int DIALOG_REQUEST_RESTORE_SETTINGS = 2;
    private static final int DIALOG_REQUEST_RESTORE_CLEAR = 3;
    CimocDocumentFile mCimocDocumentFile;

    View mLayoutView;
    CheckBoxPreference mSaveComicAuto;
    CheckBoxPreference mSaveComicCloudAuto;
    LinearLayout mWebDavLayout;
    private BackupPresenter mPresenter;

    @Override
    protected BasePresenter initPresenter() {
        mPresenter = new BackupPresenter();
        mPresenter.attachView(this);
        return mPresenter;
    }

    @Override
    protected void initViewById() {
        super.initViewById();
        mLayoutView = findViewById(R.id.backup_layout);
        mSaveComicAuto = findViewById(R.id.backup_save_comic_auto);
        mSaveComicCloudAuto = findViewById(R.id.backup_cloud_sync);
        mWebDavLayout = findViewById(R.id.webdav_layout);
    }

    @Override
    protected void initView() {
        super.initView();
        mSaveComicAuto.bindPreference(PreferenceManager.PREF_BACKUP_SAVE_COMIC, true);
        mSaveComicCloudAuto.bindPreference(PreferenceManager.PREF_BACKUP_SAVE_COMIC_CLOUD, false);
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.WEBDAV_SHARED, MODE_PRIVATE);
        String webdavUrl = sharedPreferences.getString(Constants.WEBDAV_SHARED_URL, "");
        String webdavUsername = sharedPreferences.getString(Constants.WEBDAV_SHARED_USERNAME, "");
        String webdavPassword = sharedPreferences.getString(Constants.WEBDAV_SHARED_PASSWORD, "");
        if (webdavUrl.isEmpty() || webdavUsername.isEmpty() || webdavPassword.isEmpty()) {
            mWebDavLayout.setVisibility(View.GONE);
        } else {
            mWebDavLayout.setVisibility(View.VISIBLE);
        }

        findViewById(R.id.backup_save_comic).setOnClickListener(v -> onSaveFavoriteClick());
        findViewById(R.id.backup_save_tag).setOnClickListener(v -> onSaveTagClick());
        findViewById(R.id.backup_save_settings).setOnClickListener(v -> onSaveSettingsClick());
        findViewById(R.id.backup_restore_comic).setOnClickListener(v -> onRestoreFavoriteClick());
        findViewById(R.id.backup_restore_tag).setOnClickListener(v -> onRestoreTagClick());
        findViewById(R.id.backup_restore_settings).setOnClickListener(v -> onRestoreSettingsClick());
        findViewById(R.id.backup_clear_record).setOnClickListener(v -> onClearRecordClick());
        findViewById(R.id.backup_cloud_config).setOnClickListener(v -> onWebDavConfClick());
        findViewById(R.id.backup_cloud_backup).setOnClickListener(v -> onWebDavCloudBackupClick());
        findViewById(R.id.backup_cloud_restore).setOnClickListener(v -> onWebDavCloudRestoreClick());
        findViewById(R.id.backup_cloud_clear).setOnClickListener(v -> onWebDavCloudClearClick());
        findViewById(R.id.backup_cloud_upload).setOnClickListener(v -> onWebDavCloudUploadClick());
        findViewById(R.id.backup_save_settings_cloud).setOnClickListener(v -> onSaveSettingsCloudClick());
        findViewById(R.id.backup_restore_settings_cloud).setOnClickListener(v -> onRestoreSettingsCloudClick());

    }

    void onSaveFavoriteClick() {
        showProgressDialog();
        if (PermissionUtils.hasStoragePermission(this)) {
            mPresenter.saveComic(getAppInstance().getDocumentFile());
        } else {
            onFileLoadFail();
        }
    }


    void onSaveTagClick() {
        showProgressDialog();
        if (PermissionUtils.hasStoragePermission(this)) {
            mPresenter.saveTag(getAppInstance().getDocumentFile());
        } else {
            onFileLoadFail();
        }
    }

    void onSaveSettingsClick() {
        showProgressDialog();
        if (PermissionUtils.hasStoragePermission(this)) {
            mPresenter.saveSettings(getAppInstance().getDocumentFile());
        } else {
            onFileLoadFail();
        }
    }

    void onRestoreFavoriteClick() {
        showProgressDialog();
        if (PermissionUtils.hasStoragePermission(this)) {
            mCimocDocumentFile = getAppInstance().getDocumentFile();
            mPresenter.loadComicFile(mCimocDocumentFile);
        } else {
            onFileLoadFail();
        }
    }

    void onRestoreTagClick() {
        showProgressDialog();
        if (PermissionUtils.hasStoragePermission(this)) {
            mCimocDocumentFile = getAppInstance().getDocumentFile();
            mPresenter.loadTagFile(mCimocDocumentFile);
        } else {
            onFileLoadFail();
        }
    }

    void onRestoreSettingsClick() {
        showProgressDialog();
        if (PermissionUtils.hasStoragePermission(this)) {
            mCimocDocumentFile = getAppInstance().getDocumentFile();
            mPresenter.loadSettingsFile(mCimocDocumentFile);
        } else {
            onFileLoadFail();
        }
    }

    void onClearRecordClick() {
        showProgressDialog();
        if (PermissionUtils.hasStoragePermission(this)) {
            mCimocDocumentFile = getAppInstance().getDocumentFile();
            mPresenter.loadClearBackupFile(mCimocDocumentFile);
        } else {
            onFileLoadFail();
        }
    }

    void onWebDavConfClick() {
        int theme = ThemeUtils.getThemeId();
        WebDavConfDialog dialog = new WebDavConfDialog(this, ThemeUtils.getDialogThemeById(theme), new WebDavConfDialog.SubmitCallBack() {
            @Override
            public void onSuccess() {
                mWebDavLayout.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailed() {
                mWebDavLayout.setVisibility(View.GONE);
            }
        });
        dialog.show();
    }

    void onWebDavCloudBackupClick() {
        showProgressDialog();
        mCimocDocumentFile = CimocDocumentFile.fromWebDav();
        mPresenter.saveComic(mCimocDocumentFile);
    }


    void onWebDavCloudRestoreClick() {
        showProgressDialog();
        mCimocDocumentFile = CimocDocumentFile.fromWebDav();
        mPresenter.loadComicFile(mCimocDocumentFile);
    }

    void onWebDavCloudClearClick() {
        showProgressDialog();
        mCimocDocumentFile = CimocDocumentFile.fromWebDav();
        mPresenter.loadClearBackupFile(mCimocDocumentFile);
    }

    void onWebDavCloudUploadClick() {
        showProgressDialog();
        mCimocDocumentFile = CimocDocumentFile.fromWebDav();
        CimocDocumentFile localCimocDocumentFiles = DocumentUtils.getOrCreateSubDirectory(getAppInstance().getDocumentFile(), BACKUP);
        mPresenter.uploadBackup2Cloud(localCimocDocumentFiles, new WebDavCimocDocumentFile((WebDavCimocDocumentFile) mCimocDocumentFile, BACKUP));
    }

    void onSaveSettingsCloudClick() {
        showProgressDialog();
        mCimocDocumentFile = CimocDocumentFile.fromWebDav();
        mPresenter.saveSettings(mCimocDocumentFile);
    }

    void onRestoreSettingsCloudClick() {
        showProgressDialog();
        mCimocDocumentFile = CimocDocumentFile.fromWebDav();
        mPresenter.loadSettingsFile(mCimocDocumentFile);
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        switch (requestCode) {
            case DIALOG_REQUEST_RESTORE_COMIC:
                showProgressDialog();
                mPresenter.restoreComic(bundle.getString(EXTRA_DIALOG_RESULT_VALUE), mCimocDocumentFile);
                break;
            case DIALOG_REQUEST_RESTORE_TAG:
                showProgressDialog();
                mPresenter.restoreTag(bundle.getString(EXTRA_DIALOG_RESULT_VALUE), mCimocDocumentFile);
                break;
            case DIALOG_REQUEST_RESTORE_SETTINGS:
                showProgressDialog();
                mPresenter.restoreSetting(bundle.getString(EXTRA_DIALOG_RESULT_VALUE), mCimocDocumentFile);
                break;
            case DIALOG_REQUEST_RESTORE_CLEAR:
                showProgressDialog();
                mPresenter.clearBackup(mCimocDocumentFile);
                break;

            case DIALOG_REQUEST_RESTORE_DELETE:
                showProgressDialog();
                mPresenter.deleteBackup(bundle.getString(EXTRA_DIALOG_RESULT_VALUE), mCimocDocumentFile);
                break;
        }
    }

    @Override
    public void onComicFileLoadSuccess(String[] file) {
        showChoiceDialog(R.string.backup_restore_comic, file, DIALOG_REQUEST_RESTORE_COMIC);
    }

    @Override
    public void onTagFileLoadSuccess(String[] file) {
        showChoiceDialog(R.string.backup_restore_tag, file, DIALOG_REQUEST_RESTORE_TAG);
    }

    @Override
    public void onSettingsFileLoadSuccess(String[] file) {
        showChoiceDialog(R.string.backup_restore_settings, file, DIALOG_REQUEST_RESTORE_SETTINGS);
    }

    private void showChoiceDialog(int title, String[] item, int request) {
        hideProgressDialog();
        ChoiceDialogFragment fragment = ChoiceDialogFragment.newInstanceWithDelete(title, item, -1, request);
        fragment.show(getSupportFragmentManager(), null);
    }

    @Override
    public void onClearFileLoadSuccess(String[] file) {
        hideProgressDialog();
        MessageDialogFragment fragment = MessageDialogFragment.newInstance(R.string.backup_clear_record,
                R.string.backup_clear_record_notice_summary, true, DIALOG_REQUEST_RESTORE_CLEAR);
        fragment.show(getSupportFragmentManager(), null);
    }

    @Override
    public void onFileLoadFail() {
        hideProgressDialog();
        showSnackbar(R.string.backup_restore_not_found);
    }

    @Override
    public void onBackupRestoreSuccess() {
        hideProgressDialog();
        showSnackbar(R.string.common_execute_success);
    }

    @Override
    public void onClearBackupSuccess() {
        hideProgressDialog();
        showSnackbar(R.string.common_execute_clear_success);
    }

    @Override
    public void onClearBackupFail() {
        hideProgressDialog();
        showSnackbar(R.string.common_execute_clear_fail);
    }

    @Override
    public void onUploadSuccess() {
        hideProgressDialog();
        showSnackbar(R.string.common_uploud_success);
    }

    @Override
    public void onUploadFail() {
        hideProgressDialog();
        showSnackbar(R.string.common_uploud_fail);
    }

    @Override
    public void onBackupRestoreFail() {
        hideProgressDialog();
        showSnackbar(R.string.common_execute_fail);
    }

    @Override
    public void onBackupSaveSuccess(int size) {
        hideProgressDialog();
        showSnackbar(StringUtils.format(getString(R.string.backup_save_success), size));
    }

    @Override
    public void onBackupSaveFail() {
        hideProgressDialog();
        showSnackbar(R.string.common_execute_fail);
    }

    // ====== 数据同步回调（此界面不处理，由 DataSyncActivity 处理） ======
    @Override public void onDataSyncLoginSuccess(String username) {}
    @Override public void onDataSyncLogoutSuccess() {}
    @Override public void onDataSyncStart() {}
    @Override public void onDataSyncComicSuccess(int synced, int skipped) {}
    @Override public void onDataSyncSettingSuccess(int synced) {}
    @Override public void onDataSyncAllSuccess() {}
    @Override public void onDataSyncError(String message) {}
    @Override public void onDataSyncDownloadStart() {}
    @Override public void onDataSyncDownloadComicSuccess(int count) {}
    @Override public void onDataSyncDownloadSettingSuccess(int count) {}
    @Override public void onDataSyncDownloadAllSuccess() {}
    @Override public void onDataSyncDownloadError(String message) {}

    @Override
    protected String getDefaultTitle() {
        return getString(R.string.drawer_backup);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_backup;
    }

    @Override
    protected View getLayoutView() {
        return mLayoutView;
    }

}
