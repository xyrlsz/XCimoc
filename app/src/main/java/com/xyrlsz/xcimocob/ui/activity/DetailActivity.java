package com.xyrlsz.xcimocob.ui.activity;

import static com.xyrlsz.xcimocob.ui.activity.SearchActivity.SEARCH_AUTHOR;
import static com.xyrlsz.xcimocob.ui.activity.SearchActivity.SEARCH_TITLE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.imagepipeline.core.ImagePipelineFactory;
import com.google.common.collect.Lists;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.core.WebDavConf;
import com.xyrlsz.xcimocob.fresco.ComicFrescoHeaders;
import com.xyrlsz.xcimocob.fresco.ControllerBuilderSupplierFactory;
import com.xyrlsz.xcimocob.fresco.ImagePipelineFactoryBuilder;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.Task;
import com.xyrlsz.xcimocob.presenter.BasePresenter;
import com.xyrlsz.xcimocob.presenter.DetailPresenter;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;
import com.xyrlsz.xcimocob.service.DownloadService;
import com.xyrlsz.xcimocob.ui.adapter.BaseAdapter;
import com.xyrlsz.xcimocob.ui.adapter.DetailAdapter;
import com.xyrlsz.xcimocob.ui.view.DetailView;
import com.xyrlsz.xcimocob.utils.STConvertUtils;
import com.xyrlsz.xcimocob.utils.StringUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import okhttp3.Headers;

/**
 * Created by Hiroshi on 2016/7/2.
 */
public class DetailActivity extends CoordinatorActivity implements DetailView {

    public static final int REQUEST_CODE_DOWNLOAD = 0;

    private static final String SAVED_STATE_BACKUP_COUNT = "saved_state_backup_count";

    private DetailAdapter mDetailAdapter;
    private DetailPresenter mPresenter;
    private ImagePipelineFactory mImagePipelineFactory;

    private boolean mAutoBackup;
    private boolean mAutoBackupCloud;
    private int mBackupCount;

    public static Intent createIntent(Context context, Long id, int source, String cid) {
        Intent intent = new Intent(context, DetailActivity.class);
        intent.putExtra(Extra.EXTRA_ID, id);
        intent.putExtra(Extra.EXTRA_SOURCE, source);
        intent.putExtra(Extra.EXTRA_CID, cid);
        return intent;
    }

    @Override
    protected BasePresenter initPresenter() {
        mPresenter = new DetailPresenter();
        mPresenter.attachView(this);
        return mPresenter;
    }

    @Override
    protected void initView() {
        super.initView();
        findViewById(R.id.coordinator_action_button).setOnClickListener(v -> onActionButtonClick());
        findViewById(R.id.coordinator_action_button2).setOnClickListener(v -> onActionButton2Click());
    }

    @Override
    protected BaseAdapter initAdapter() {
        mDetailAdapter = new DetailAdapter(this, new ArrayList<Chapter>());
        mRecyclerView.setHasFixedSize(false);
        mRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return mDetailAdapter;
    }

    @Override
    protected RecyclerView.LayoutManager initLayoutManager() {
        return new GridLayoutManager(this, 3);
    }

    @Override
    protected void initData() {
        mAutoBackup = mPreference.getBoolean(PreferenceManager.PREF_BACKUP_SAVE_COMIC, true);
        mAutoBackupCloud = mPreference.getBoolean(PreferenceManager.PREF_BACKUP_SAVE_COMIC_CLOUD, false);
        mBackupCount = mPreference.getNumber(PreferenceManager.PREF_BACKUP_SAVE_COMIC_COUNT, 0).intValue();
        long id = getIntent().getLongExtra(Extra.EXTRA_ID, -1);
        int source = getIntent().getIntExtra(Extra.EXTRA_SOURCE, -1);
        String cid = getIntent().getStringExtra(Extra.EXTRA_CID);
        mPresenter.load(id, source, cid);

        // 启用下拉刷新
        enablePullRefresh(() -> {
            mPresenter.refresh();
            // 网络加载完成后停止刷新动画（由 onChapterLoadSuccess / onParseError 触发）
        });
    }

    @Override
    protected void saveState(Bundle outState) {
        super.saveState(outState);
        outState.putInt(SAVED_STATE_BACKUP_COUNT, mBackupCount);
    }

    @Override
    protected void restoreData(Bundle savedInstanceState) {
        super.restoreData(savedInstanceState);
        if (savedInstanceState.containsKey(SAVED_STATE_BACKUP_COUNT)) {
            mBackupCount = savedInstanceState.getInt(SAVED_STATE_BACKUP_COUNT);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAutoBackup) {
            mPreference.putNumber(PreferenceManager.PREF_BACKUP_SAVE_COMIC_COUNT, mBackupCount);
        }
        if (mAutoBackupCloud && WebDavConf.isInit) {
            mPreference.putNumber(PreferenceManager.PREF_BACKUP_SAVE_COMIC_COUNT, mBackupCount);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mImagePipelineFactory != null) {
            mImagePipelineFactory.getImagePipeline().clearMemoryCaches();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (!isProgressBarShown()) {
            int __id = item.getItemId();
//                case R.id.detail_history:
//                    if (!mDetailAdapter.getDateSet().isEmpty()) {
//                        String path = mPresenter.getComic().getLast();
//                        if (path == null) {
//                            path = mDetailAdapter.getItem(mDetailAdapter.getDateSet().size() - 1).getPath();
//                        }
//                        startReader(path);
//                    }
//                    break;
            if (__id == R.id.detail_download) {
                    if (!mDetailAdapter.getDateSet().isEmpty()) {
                        Intent intent1 = ChapterActivity.createIntent(this, new ArrayList<>(mDetailAdapter.getDateSet()));
                        startActivityForResult(intent1, REQUEST_CODE_DOWNLOAD);
                    }
//                case R.id.detail_tag:
//                    if (mPresenter.getComic().getFavorite() != null) {
//                        intent = TagEditorActivity.createIntent(this, mPresenter.getComic().getId());
//                        startActivity(intent);
//                    } else {
//                        showSnackbar(R.string.detail_tag_favorite);
//                    }
//                    break;
            } else if (__id == R.id.detail_search_title) {
                    if (!StringUtils.isEmpty(mPresenter.getComic().getTitle())) {
//                        if(App.getPreferenceManager().getBoolean(PreferenceManager.PREF_OTHER_FIREBASE_EVENT, true)) {
//                            Bundle bundle = new Bundle();
//                            bundle.putString(FirebaseAnalytics.Param.CONTENT, mPresenter.getComic().getTitle());
//                            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "byTitle");
//                            bundle.putInt(FirebaseAnalytics.Param.SOURCE, mPresenter.getComic().getSource());
//                            FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
//                            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SEARCH, bundle);
//                        }
                        Intent intent2 = ResultActivity.createIntent(this, mPresenter.getComic().getTitle(), null, ResultActivity.LAUNCH_MODE_SEARCH, SEARCH_TITLE);
                        startActivity(intent2);
                    } else {
                        showSnackbar(R.string.common_keyword_empty);
                    }
            } else if (__id == R.id.detail_search_author) {
                    if (!StringUtils.isEmpty(mPresenter.getComic().getAuthor())) {
                        Intent intent3 = ResultActivity.createIntent(this, mPresenter.getComic().getAuthor(), null, ResultActivity.LAUNCH_MODE_SEARCH, SEARCH_AUTHOR);
                        startActivity(intent3);
                    } else {
                        showSnackbar(R.string.common_keyword_empty);
                    }
            } else if (__id == R.id.detail_share_url) {
                    String url = mPresenter.getComic().getUrl();
                    Intent intent4 = new Intent(Intent.ACTION_SEND);
                    intent4.setType("text/plain");
                    intent4.putExtra(Intent.EXTRA_TEXT, url);
                    intent4.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(Intent.createChooser(intent4, url));
                    // firebase analytics
//                    if(App.getPreferenceManager().getBoolean(PreferenceManager.PREF_OTHER_FIREBASE_EVENT, true)) {
//                        Bundle bundle = new Bundle();
//                        bundle.putString(FirebaseAnalytics.Param.CONTENT, url);
//                        bundle.putInt(FirebaseAnalytics.Param.SOURCE, mPresenter.getComic().getSource());
//                        FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
//                        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SHARE, bundle);
//                    }
            } else if (__id == R.id.detail_reverse_list) {
                    mDetailAdapter.reverse();
//                case R.id.detail_disqus:
//                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.home_page_cimqus_url) + "/cimoc/" + mPresenter.getComic().getTitle()));
//                    try {
//                        startActivity(intent);
//                    } catch (Exception e) {
//                        showSnackbar(R.string.about_resource_fail);
//                    }
//                    break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_DOWNLOAD:
                    showProgressDialog();
                    List<Chapter> list = data.getParcelableArrayListExtra(Extra.EXTRA_CHAPTER);
                    mPresenter.addTask(mDetailAdapter.getDateSet(), list);
                    break;
            }
        }
    }

    void onActionButtonClick() {
        if (mPresenter.getComic().getFavorite() != null) {
            mPresenter.unfavoriteComic();
            increment();
            mActionButton.setImageResource(R.drawable.ic_favorite_border_white_24dp);
            showSnackbar(R.string.detail_unfavorite);
        } else {
            mPresenter.favoriteComic();
            increment();
            mActionButton.setImageResource(R.drawable.ic_favorite_white_24dp);
            showSnackbar(R.string.detail_favorite);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到详情页，强制同步一次数据库状态
        mPresenter.checkDatabaseStatus();
    }

    void onActionButton2Click() {
        if (!mDetailAdapter.getDateSet().isEmpty()) {
            String path = mPresenter.getComic().getLast();
            if (path == null) {
                path = mDetailAdapter.getItem(mDetailAdapter.getDateSet().size() - 1).getPath();
            }
            startReader(path);
        }
    }

    @Override
    public void onItemClick(View view, int position) {
        if (position > 0 && position - 1 < mDetailAdapter.getDateSet().size()) {
            String path = mDetailAdapter.getItem(position - 1).getPath();
            if (!StringUtils.isEmpty(path)) {
                startReader(path);
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public boolean onItemLongClick(View view, int position) {
        if (position == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_selectable_text, null);
            TextView textViewTitle = dialogView.findViewById(R.id.textViewTitle);
            TextView textViewMessage = dialogView.findViewById(R.id.textViewMessage);
            textViewTitle.setText(STConvertUtils.convert(mDetailAdapter.title));
            textViewMessage.setText(mDetailAdapter.author + "\n\n" + STConvertUtils.convert(mDetailAdapter.intro));
            textViewMessage.setTextIsSelectable(true);
            textViewTitle.setTextIsSelectable(true);

            if (ThemeUtils.isDarkMode(this)) {
                textViewTitle.setTextColor(Color.WHITE);
                textViewMessage.setTextColor(Color.WHITE);
            } else {
                textViewTitle.setTextColor(Color.BLACK);
                textViewMessage.setTextColor(Color.BLACK);
            }
           
            builder.setView(dialogView)
                    .setPositiveButton(R.string.dialog_close, null)
                    .show();
        }
        return false;
    }


    private void startReader(String path) {
        long id = mPresenter.updateLast(path);
        mDetailAdapter.setLast(path);
        int mode = mPreference.getNumber(PreferenceManager.PREF_READER_MODE, PreferenceManager.READER_MODE_PAGE).intValue();
        List<Chapter> c = mDetailAdapter.getDateSet();
        if (mDetailAdapter.isReversed()) {
            c = Lists.reverse(mDetailAdapter.getDateSet());
        }
        Intent intent = ReaderActivity.createIntent(DetailActivity.this, id, c, mode);
        startActivity(intent);
    }

    @Override
    public void onLastChange(String last) {
        mDetailAdapter.setLast(last);
    }


    @Override
    public void onTaskAddSuccess(ArrayList<Task> list) {
        Intent intent = DownloadService.createIntent(this, list);
        startService(intent);
        updateChapterList(list);
        showSnackbar(R.string.detail_download_queue_success);
        hideProgressDialog();
    }

    private void updateChapterList(List<Task> list) {
        Set<String> set = new HashSet<>();
        for (Task task : list) {
            set.add(task.getPath());
        }
        for (Chapter chapter : mDetailAdapter.getDateSet()) {
            if (set.contains(chapter.getPath())) {
                chapter.setDownload(true);
            }
        }
    }

    @Override
    public void onTaskAddFail() {
        hideProgressDialog();
        showSnackbar(R.string.detail_download_queue_fail);
    }

    @Override
    public void onChapterDownloadStatusChanged(List<Chapter> list) {
        for (Chapter chapter : list) {
            for (Chapter adapterChapter : mDetailAdapter.getDateSet()) {
                if (adapterChapter.getPath().equals(chapter.getPath())) {
                    adapterChapter.setDownload(chapter.isDownload());
                    adapterChapter.setComplete(chapter.isComplete());
                    adapterChapter.setCount(chapter.getCount());
                }
            }
        }
        mDetailAdapter.notifyDataSetChanged();
    }

    @Override
    public void onComicLoadSuccess(Comic comic) {
        mDetailAdapter.setInfo(comic.getCover(), comic.getTitle(), comic.getAuthor(),
                comic.getIntro(), comic.getFinish(), comic.getUpdate(), comic.getLast(), SourceManager.getInstance(getAppInstance()).getParser(comic.getSource()).getTitle());

        if (comic.getTitle() != null && comic.getCover() != null) {
            Headers headers = SourceManager.getInstance(this).getParser(comic.getSource()).getHeader();
            ComicFrescoHeaders.setHeaders(headers);
            mImagePipelineFactory = ImagePipelineFactoryBuilder.build(this, headers, false);
            mDetailAdapter.setControllerSupplier(ControllerBuilderSupplierFactory.get(this, mImagePipelineFactory));

            int resId = comic.getFavorite() != null ? R.drawable.ic_favorite_white_24dp : R.drawable.ic_favorite_border_white_24dp;
            mActionButton.setImageResource(resId);
            mActionButton.setVisibility(View.VISIBLE);
            mActionButton2.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onChapterLoadSuccess(List<Chapter> list) {
        hideProgressBar();
        stopPullRefresh();
        if (mPresenter.getComic().getTitle() != null && mPresenter.getComic().getCover() != null) {
            mDetailAdapter.setData(list);
        }
//        if(App.getPreferenceManager().getBoolean(PreferenceManager.PREF_OTHER_FIREBASE_EVENT, true)) {
//            Bundle bundle = new Bundle();
//            bundle.putString(FirebaseAnalytics.Param.CONTENT, mPresenter.getComic().getTitle());
//            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "Title");
//            bundle.putInt(FirebaseAnalytics.Param.SOURCE, mPresenter.getComic().getSource());
//            bundle.putBoolean(FirebaseAnalytics.Param.SUCCESS, true);
//            FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
//            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle);
//        }
    }

    @Override
    public void onPreLoadSuccess(List<Chapter> list, Comic comic) {
        // 先用缓存章节展示，提升加载速度（不隐藏进度条，网络完成后会替换）
        mDetailAdapter.setInfo(comic.getCover(), comic.getTitle(), comic.getAuthor(),
                comic.getIntro(), comic.getFinish(), comic.getUpdate(), comic.getLast(), SourceManager.getInstance(getAppInstance()).getParser(comic.getSource()).getTitle());

        if (comic.getTitle() != null && comic.getCover() != null) {
            mDetailAdapter.setData(list);

            Headers headers = SourceManager.getInstance(this).getParser(comic.getSource()).getHeader();
            ComicFrescoHeaders.setHeaders(headers);
            mImagePipelineFactory = ImagePipelineFactoryBuilder.build(this, headers, false);
            mDetailAdapter.setControllerSupplier(ControllerBuilderSupplierFactory.get(this, mImagePipelineFactory));

            int resId = comic.getFavorite() != null ? R.drawable.ic_favorite_white_24dp : R.drawable.ic_favorite_border_white_24dp;
            mActionButton.setImageResource(resId);
            mActionButton.setVisibility(View.VISIBLE);
            mActionButton2.setVisibility(View.VISIBLE);
        }
        // 进度条保持转动，网络完成后 onChapterLoadSuccess 会用最新章节替换并关闭进度条
    }

    @Override
    public void onParseError() {
//        if(App.getPreferenceManager().getBoolean(PreferenceManager.PREF_OTHER_FIREBASE_EVENT, true)) {
//            Bundle bundle = new Bundle();
//            bundle.putString(FirebaseAnalytics.Param.CONTENT, mPresenter.getComic().getTitle());
//            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "Title");
//            bundle.putInt(FirebaseAnalytics.Param.SOURCE, mPresenter.getComic().getSource());
//            bundle.putBoolean(FirebaseAnalytics.Param.SUCCESS, false);
//            FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
//            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle);
//        }
        hideProgressBar();
        stopPullRefresh();
        showSnackbar(R.string.common_parse_error);


    }

    private void increment() {
        if (mAutoBackup && ++mBackupCount == 10) {
            mBackupCount = 0;
            mPreference.putNumber(PreferenceManager.PREF_BACKUP_SAVE_COMIC_COUNT, 0);
            mPresenter.backup(getAppInstance().getDocumentFile());
        }
        if (mAutoBackupCloud && ++mBackupCount == 10 && WebDavConf.isInit) {
            mBackupCount = 0;
            mPreference.putNumber(PreferenceManager.PREF_BACKUP_SAVE_COMIC_COUNT, 0);
            mPresenter.backup(CimocDocumentFile.fromWebDav());
        }
    }

    @Override
    protected String getDefaultTitle() {
        return getString(R.string.detail);
    }

}
