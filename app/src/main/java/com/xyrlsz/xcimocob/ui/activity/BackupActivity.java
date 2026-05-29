package com.xyrlsz.xcimocob.ui.activity;

import static com.xyrlsz.xcimocob.core.Backup.BACKUP;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.xyrlsz.xcimocob.ui.widget.DataServerLoginDialog;
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
    CheckBoxPreference mDataServerAutoSync;
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
        mDataServerAutoSync = findViewById(R.id.backup_data_server_auto_sync);
        mWebDavLayout = findViewById(R.id.webdav_layout);
    }

    @Override
    protected void initView() {
        super.initView();
        mSaveComicAuto.bindPreference(PreferenceManager.PREF_BACKUP_SAVE_COMIC, true);
        mSaveComicCloudAuto.bindPreference(PreferenceManager.PREF_BACKUP_SAVE_COMIC_CLOUD, false);
        mDataServerAutoSync.bindPreference(PreferenceManager.PREF_DATA_SERVER_AUTO_SYNC, true);
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

        // 数据同步服务器
        findViewById(R.id.backup_data_server_config).setOnClickListener(v -> onDataServerConfigClick());
        findViewById(R.id.backup_data_server_login).setOnClickListener(v -> onDataServerLoginClick());
        findViewById(R.id.backup_data_server_sync_all).setOnClickListener(v -> onDataServerSyncAllClick());
        findViewById(R.id.backup_data_server_sync_comic).setOnClickListener(v -> onDataServerSyncComicClick());
        findViewById(R.id.backup_data_server_sync_setting).setOnClickListener(v -> onDataServerSyncSettingClick());
        findViewById(R.id.backup_data_server_sync_tag).setOnClickListener(v -> onDataServerSyncTagClick());
        findViewById(R.id.backup_data_server_restore_all).setOnClickListener(v -> onDataServerRestoreAllClick());
        findViewById(R.id.backup_data_server_restore_comic).setOnClickListener(v -> onDataServerRestoreComicClick());
        findViewById(R.id.backup_data_server_restore_setting).setOnClickListener(v -> onDataServerRestoreSettingClick());
        findViewById(R.id.backup_data_server_restore_tag).setOnClickListener(v -> onDataServerRestoreTagClick());
        findViewById(R.id.backup_data_server_logout).setOnClickListener(v -> onDataServerLogoutClick());
        updateDataServerLoginStatus();

    }

    /**
     * 更新数据同步服务器登录状态显示
     */
    private void updateDataServerLoginStatus() {
        String token = App.getPreferenceManager().getString(PreferenceManager.PREFERENCES_USER_TOCKEN, "");
        String username = App.getPreferenceManager().getString(PreferenceManager.PREFERENCES_USER_NAME, "");
        if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(username)) {
            findViewById(R.id.backup_data_server_login).setVisibility(View.GONE);
            findViewById(R.id.backup_data_server_logout).setVisibility(View.VISIBLE);
            // 更新登录选项的摘要显示用户名
            com.xyrlsz.xcimocob.ui.widget.Option loginOption = findViewById(R.id.backup_data_server_login);
            loginOption.setSummary(getString(R.string.data_sync_login_success) + " (" + username + ")");
        } else {
            findViewById(R.id.backup_data_server_login).setVisibility(View.VISIBLE);
            findViewById(R.id.backup_data_server_logout).setVisibility(View.GONE);
            com.xyrlsz.xcimocob.ui.widget.Option loginOption = findViewById(R.id.backup_data_server_login);
            loginOption.setSummary(getString(R.string.backup_data_server_login_summary));
        }
    }

    // ==================== 数据同步服务器点击处理 ====================

    void onDataServerConfigClick() {
        // 显示服务器配置对话框
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this, ThemeUtils.getDialogThemeById(ThemeUtils.getThemeId()));
        builder.setTitle(R.string.data_server_config_title);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        final android.widget.EditText urlInput = new android.widget.EditText(this);
        urlInput.setHint(R.string.data_server_url_hint);
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        String currentUrl = App.getPreferenceManager().getString(PreferenceManager.PREF_DATA_SERVER_URL, "");
        urlInput.setText(currentUrl);
        layout.addView(urlInput);

        android.widget.TextView summary = new android.widget.TextView(this);
        summary.setText(R.string.data_server_url_dialog_summary);
        summary.setTextSize(12);
        summary.setPadding(0, 8, 0, 0);
        layout.addView(summary);

        builder.setView(layout);
        builder.setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
            String url = urlInput.getText().toString().trim();
            App.getPreferenceManager().putString(PreferenceManager.PREF_DATA_SERVER_URL, url);
            showSnackbar("服务器地址已保存");
        });
        builder.setNegativeButton(R.string.dialog_negative, null);
        builder.show();
    }

    void onDataServerLoginClick() {
        int theme = ThemeUtils.getThemeId();
        DataServerLoginDialog dialog = new DataServerLoginDialog(this,
                ThemeUtils.getDialogThemeById(theme),
                (username, password) -> mPresenter.dataSyncLogin(username, password));
        dialog.show();
    }

    void onDataServerSyncAllClick() {
        mPresenter.dataSyncAll();
    }

    void onDataServerSyncComicClick() {
        mPresenter.dataSyncComic();
    }

    void onDataServerSyncSettingClick() {
        mPresenter.dataSyncSetting();
    }

    void onDataServerSyncTagClick() {
        mPresenter.dataSyncTag();
    }

    // ========== 从服务器恢复 ==========

    void onDataServerRestoreAllClick() {
        mPresenter.dataSyncDownloadAll();
    }

    void onDataServerRestoreComicClick() {
        mPresenter.dataSyncDownloadComic();
    }

    void onDataServerRestoreSettingClick() {
        mPresenter.dataSyncDownloadSetting();
    }

    void onDataServerRestoreTagClick() {
        mPresenter.dataSyncDownloadTag();
    }

    void onDataServerLogoutClick() {
        new android.app.AlertDialog.Builder(this, ThemeUtils.getDialogThemeById(ThemeUtils.getThemeId()))
                .setTitle(R.string.user_login_logout)
                .setMessage(R.string.user_login_logout_tips)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    mPresenter.dataSyncLogout();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
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

    // ==================== 数据同步服务器回调 ====================

    @Override
    public void onDataSyncLoginSuccess(String username) {
        hideProgressDialog();
        updateDataServerLoginStatus();
        showSnackbar(getString(R.string.data_sync_login_success) + " (" + username + ")");
    }

    @Override
    public void onDataSyncLogoutSuccess() {
        hideProgressDialog();
        updateDataServerLoginStatus();
        showSnackbar(R.string.data_sync_logout_success);
    }

    @Override
    public void onDataSyncStart() {
        showProgressDialog();
    }

    @Override
    public void onDataSyncComicSuccess(int synced, int skipped) {
        hideProgressDialog();
        showSnackbar(getString(R.string.data_sync_comic_success) + " (同步:" + synced + ", 跳过:" + skipped + ")");
    }

    @Override
    public void onDataSyncSettingSuccess(int synced) {
        hideProgressDialog();
        showSnackbar(getString(R.string.data_sync_setting_success) + " (同步:" + synced + ")");
    }

    @Override
    public void onDataSyncTagSuccess() {
        hideProgressDialog();
        showSnackbar(R.string.data_sync_tag_success);
    }

    @Override
    public void onDataSyncAllSuccess() {
        hideProgressDialog();
        showSnackbar(R.string.data_sync_all_success);
    }

    @Override
    public void onDataSyncError(String message) {
        hideProgressDialog();
        showSnackbar(StringUtils.format(getString(R.string.data_sync_fail), message));
    }

    // ========== 数据同步服务器回调（下载/恢复） ==========

    @Override
    public void onDataSyncDownloadStart() {
        showProgressDialog();
    }

    @Override
    public void onDataSyncDownloadComicSuccess(int count) {
        hideProgressDialog();
        showSnackbar(StringUtils.format(getString(R.string.data_sync_download_comic_success), count));
    }

    @Override
    public void onDataSyncDownloadSettingSuccess(int count) {
        hideProgressDialog();
        showSnackbar(StringUtils.format(getString(R.string.data_sync_download_setting_success), count));
    }

    @Override
    public void onDataSyncDownloadTagSuccess() {
        hideProgressDialog();
        showSnackbar(R.string.data_sync_download_tag_success);
    }

    @Override
    public void onDataSyncDownloadAllSuccess() {
        hideProgressDialog();
        showSnackbar(R.string.data_sync_download_all_success);
    }

    @Override
    public void onDataSyncDownloadError(String message) {
        hideProgressDialog();
        showSnackbar(StringUtils.format(getString(R.string.data_sync_fail), message));
    }

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
