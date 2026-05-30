package com.xyrlsz.xcimocob.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.presenter.BackupPresenter;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.ui.view.BackupView;
import com.xyrlsz.xcimocob.ui.widget.DataServerLoginDialog;
import com.xyrlsz.xcimocob.ui.widget.preference.CheckBoxPreference;
import com.xyrlsz.xcimocob.utils.StringUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;

/**
 * 服务器数据同步界面
 */
public class DataSyncActivity extends BackActivity implements BackupView {

    CheckBoxPreference mAutoSync;
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
        mAutoSync = findViewById(R.id.backup_data_server_auto_sync);
    }

    @Override
    protected void initView() {
        super.initView();
        mAutoSync.bindPreference(PreferenceManager.PREF_DATA_SERVER_AUTO_SYNC, true);

        findViewById(R.id.backup_data_server_config).setOnClickListener(v -> onConfigClick());
        findViewById(R.id.backup_data_server_login).setOnClickListener(v -> onLoginClick());
        findViewById(R.id.backup_data_server_sync_all).setOnClickListener(v -> mPresenter.dataSyncAll());
        findViewById(R.id.backup_data_server_sync_comic).setOnClickListener(v -> mPresenter.dataSyncComic());
        findViewById(R.id.backup_data_server_sync_setting).setOnClickListener(v -> mPresenter.dataSyncSetting());
        findViewById(R.id.backup_data_server_sync_tag).setOnClickListener(v -> mPresenter.dataSyncTag());
        findViewById(R.id.backup_data_server_restore_all).setOnClickListener(v -> mPresenter.dataSyncDownloadAll());
        findViewById(R.id.backup_data_server_restore_comic).setOnClickListener(v -> mPresenter.dataSyncDownloadComic());
        findViewById(R.id.backup_data_server_restore_setting).setOnClickListener(v -> mPresenter.dataSyncDownloadSetting());
        findViewById(R.id.backup_data_server_restore_tag).setOnClickListener(v -> mPresenter.dataSyncDownloadTag());
        findViewById(R.id.backup_data_server_logout).setOnClickListener(v -> onLogoutClick());

        updateLoginStatus();
    }

    private void updateLoginStatus() {
        String token = App.getPreferenceManager().getString(PreferenceManager.PREFERENCES_USER_TOCKEN, "");
        String username = App.getPreferenceManager().getString(PreferenceManager.PREFERENCES_USER_NAME, "");
        com.xyrlsz.xcimocob.ui.widget.Option loginOption = findViewById(R.id.backup_data_server_login);
        View logoutOption = findViewById(R.id.backup_data_server_logout);

        if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(username)) {
            loginOption.setVisibility(View.GONE);
            logoutOption.setVisibility(View.VISIBLE);
            loginOption.setSummary(getString(R.string.data_sync_login_success) + " (" + username + ")");
        } else {
            loginOption.setVisibility(View.VISIBLE);
            logoutOption.setVisibility(View.GONE);
            loginOption.setSummary(getString(R.string.backup_data_server_login_summary));
        }
    }

    void onConfigClick() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this,
                ThemeUtils.getDialogThemeById(ThemeUtils.getThemeId()));
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

    void onLoginClick() {
        int theme = ThemeUtils.getThemeId();
        DataServerLoginDialog dialog = new DataServerLoginDialog(this,
                ThemeUtils.getDialogThemeById(theme),
                (username, password) -> mPresenter.dataSyncLogin(username, password));
        dialog.show();
    }

    void onLogoutClick() {
        new android.app.AlertDialog.Builder(this, ThemeUtils.getDialogThemeById(ThemeUtils.getThemeId()))
                .setTitle(R.string.user_login_logout)
                .setMessage(R.string.user_login_logout_tips)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> mPresenter.dataSyncLogout())
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    @Override
    public void onDialogResult(int requestCode, Bundle bundle) {
        // 未使用
    }

    // ==================== 上传回调 ====================

    @Override
    public void onDataSyncLoginSuccess(String username) {
        hideProgressDialog();
        updateLoginStatus();
        showSnackbar(getString(R.string.data_sync_login_success) + " (" + username + ")");
    }

    @Override
    public void onDataSyncLogoutSuccess() {
        hideProgressDialog();
        updateLoginStatus();
        showSnackbar(R.string.data_sync_logout_success);
    }

    @Override
    public void onDataSyncStart() { showProgressDialog(); }

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

    // ==================== 下载回调 ====================

    @Override
    public void onDataSyncDownloadStart() { showProgressDialog(); }

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

    // ==================== 本地备份/恢复回调（此 Activity 不涉及，空实现） ====================

    @Override public void onBackupSaveSuccess(int size) {}
    @Override public void onBackupSaveFail() {}
    @Override public void onBackupRestoreSuccess() {}
    @Override public void onBackupRestoreFail() {}
    @Override public void onComicFileLoadSuccess(String[] file) {}
    @Override public void onTagFileLoadSuccess(String[] file) {}
    @Override public void onSettingsFileLoadSuccess(String[] file) {}
    @Override public void onClearFileLoadSuccess(String[] file) {}
    @Override public void onFileLoadFail() {}
    @Override public void onClearBackupSuccess() {}
    @Override public void onClearBackupFail() {}
    @Override public void onUploadSuccess() {}
    @Override public void onUploadFail() {}

    @Override
    protected String getDefaultTitle() {
        return getString(R.string.backup_data_server);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_data_sync;
    }

    @Override
    protected View getLayoutView() {
        return findViewById(R.id.data_sync_layout);
    }
}
