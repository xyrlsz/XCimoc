package com.xyrlsz.xcimocob.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.component.DialogCaller;
import com.xyrlsz.xcimocob.global.Extra;
import com.xyrlsz.xcimocob.manager.ChapterManager;
import com.xyrlsz.xcimocob.manager.ChapterBookmarkStore;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.misc.Switcher;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.ui.adapter.BaseAdapter;
import com.xyrlsz.xcimocob.ui.adapter.ChapterAdapter;
import com.xyrlsz.xcimocob.ui.fragment.dialog.EditorDialogFragment;
import com.xyrlsz.xcimocob.ui.widget.ViewUtils;
import com.xyrlsz.xcimocob.utils.PermissionUtils;
import com.xyrlsz.xcimocob.utils.ScreenUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;





/**
 * Created by Hiroshi on 2016/11/14.
 */

public class ChapterActivity extends BackActivity implements BaseAdapter.OnItemClickListener, DialogCaller {

    private static final int REQUEST_CODE_BOOKMARK_NOTE = 1;

    /**
     * 章节列表内存缓存，避免通过 Intent 传递大列表导致 Binder 事务过大。
     */
    private static final ConcurrentHashMap<Long, ArrayList<Chapter>> sChapterCache = new ConcurrentHashMap<>();
    private static final AtomicLong sChapterKeyGenerator = new AtomicLong(0);

    RecyclerView mRecyclerView;
    CoordinatorLayout mCoordinatorLayout;

    private ChapterAdapter mChapterAdapter;
    private boolean isAscendMode;
    private boolean isButtonMode;
    private long mChapterKey = -1;
    private long mBookmarkComicId = -1L;
    private List<Switcher<Chapter>> mAllChapters;
    private Set<String> mBookmarkPaths = new HashSet<>();
    private Chapter mPendingBookmarkNote;
    private Handler mHandler = new Handler();
    private RecyclerView.OnItemTouchListener mListener = new CustomTouchListener();
    private RecyclerView.ItemDecoration mDecoration;

    public static Intent createIntent(Context context, ArrayList<Chapter> list) {
        return createIntent(context, list, -1L);
    }

    public static Intent createIntent(Context context, ArrayList<Chapter> list, long sourceComic) {
        Intent intent = new Intent(context, ChapterActivity.class);
        if (sourceComic == -1L && list != null && !list.isEmpty()) {
            sourceComic = list.get(0).getSourceComic();
        }
        long key = sChapterKeyGenerator.incrementAndGet();
        sChapterCache.put(key, list);
        intent.putExtra(Extra.EXTRA_CHAPTER_KEY, key);
        if (sourceComic != -1L) {
            intent.putExtra(Extra.EXTRA_SOURCE_COMIC, sourceComic);
        }
        return intent;
    }

    public static long putResultList(ArrayList<Chapter> list) {
        long key = sChapterKeyGenerator.incrementAndGet();
        sChapterCache.put(key, list);
        return key;
    }

    public static ArrayList<Chapter> takeResultList(long key) {
        return key == -1L ? null : sChapterCache.remove(key);
    }

    @Override
    protected void initViewById() {
        super.initViewById();
        mRecyclerView = findViewById(R.id.chapter_recycler_view);
        mCoordinatorLayout = findViewById(R.id.chapter_coordinator_layout);
    }

    @Override
    protected void initView() {
        isButtonMode = mPreference.getBoolean(PreferenceManager.PREF_CHAPTER_BUTTON_MODE, false);
        mAllChapters = getAdapterList();
        mBookmarkComicId = getIntent().getLongExtra(Extra.EXTRA_SOURCE_COMIC, -1L);
        if (mBookmarkComicId == -1L && !mAllChapters.isEmpty()) {
            mBookmarkComicId = mAllChapters.get(0).getElement().getSourceComic();
        }
        loadBookmarks();
        mChapterAdapter = new ChapterAdapter(this, new ArrayList<>(mAllChapters));
        mChapterAdapter.setBookmarks(mBookmarkPaths);
        if (ChapterBookmarkStore.isEnabled()) {
            mChapterAdapter.setOnBookmarkClickListener(this::toggleBookmark);
            mChapterAdapter.setOnBookmarkNoteListener(chapter -> {
                mPendingBookmarkNote = chapter;
                String note = ChapterBookmarkStore.getNote(this, mBookmarkComicId, chapter.getPath());
                EditorDialogFragment.newInstance(R.string.chapter_bookmark_note, note,
                        REQUEST_CODE_BOOKMARK_NOTE, true).show(getSupportFragmentManager(), null);
                return true;
            });
        }
        mDecoration = mChapterAdapter.getItemDecoration();
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemAnimator(null);
        mRecyclerView.setAdapter(mChapterAdapter);
        switchMode();
        ScreenUtils.bindResponsiveGrid(mRecyclerView, 4, 92, 8);
        findViewById(R.id.chapter_action_button).setOnClickListener(v -> onActionButtonClick());
        ViewCompat.setOnApplyWindowInsetsListener(mCoordinatorLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    systemBars.bottom
            );
            return insets;
        });
    }

    private void switchMode() {
        mChapterAdapter.setButtonMode(isButtonMode);
        if (isButtonMode) {
            mChapterAdapter.setOnItemClickListener(null);
            mRecyclerView.setLayoutManager(new GridLayoutManager(this, 4));
            mRecyclerView.addItemDecoration(mDecoration);
            mRecyclerView.addOnItemTouchListener(mListener);
            mRecyclerView.setPadding((int) ViewUtils.dpToPixel(4, this), (int) ViewUtils.dpToPixel(10, this), (int) ViewUtils.dpToPixel(4, this), 0);
            ScreenUtils.applyResponsiveGrid(mRecyclerView, 4, 92, 8);
        } else {
            mChapterAdapter.setOnItemClickListener(this);
            mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            mRecyclerView.removeItemDecoration(mDecoration);
            mRecyclerView.removeOnItemTouchListener(mListener);
            mRecyclerView.setPadding(0, 0, 0, 0);
        }
    }

    private List<Switcher<Chapter>> getAdapterList() {
        isAscendMode = mPreference.getBoolean(PreferenceManager.PREF_CHAPTER_ASCEND_MODE, false);
        long key = getIntent().getLongExtra(Extra.EXTRA_CHAPTER_KEY, -1);
        // 读取但不移除缓存：Activity 可能被系统重建（「不保留活动」/内存回收）后以同一 Intent
        // 再次创建，此时仍需读到同一份列表；缓存改在 Activity 真正结束时（onDestroy）清理。
        mChapterKey = key;
        List<Chapter> list = key != -1 ? sChapterCache.get(key) : null;
        if (list == null) {
            long sourceComic = getIntent().getLongExtra(Extra.EXTRA_SOURCE_COMIC, -1L);
            if (sourceComic != -1L) {
                list = ChapterManager.getInstance(this).getChapterList(sourceComic);
            }
        }
        if (list == null) {
            list = new ArrayList<>();
        }
        List<Switcher<Chapter>> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); ++i) {
            result.add(new Switcher<>(list.get(i), list.get(i).isDownload()));
        }
        if (isAscendMode) {
            Collections.reverse(result);
        }
        return result;
    }

    private void loadBookmarks() {
        if (mBookmarkComicId == -1L) {
            return;
        }
        mBookmarkPaths = ChapterBookmarkStore.getBookmarks(this, mBookmarkComicId);
    }

    private void toggleBookmark(Chapter chapter) {
        String path = chapter.getPath();
        if (path == null || path.isEmpty()) {
            return;
        }
        boolean bookmarked = !mBookmarkPaths.contains(path);
        ChapterBookmarkStore.setBookmarked(this, mBookmarkComicId, path, bookmarked);
        mBookmarkPaths = ChapterBookmarkStore.getBookmarks(this, mBookmarkComicId);
        mChapterAdapter.setBookmarks(mBookmarkPaths);
        mChapterAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDialogResult(int requestCode, android.os.Bundle bundle) {
        if (requestCode == REQUEST_CODE_BOOKMARK_NOTE && mPendingBookmarkNote != null) {
            String note = bundle.getString(DialogCaller.EXTRA_DIALOG_RESULT_VALUE, "");
            ChapterBookmarkStore.saveNote(this, mBookmarkComicId,
                    mPendingBookmarkNote.getPath(), note);
            mPendingBookmarkNote = null;
            mBookmarkPaths = ChapterBookmarkStore.getBookmarks(this, mBookmarkComicId);
            mChapterAdapter.setBookmarks(mBookmarkPaths);
            mChapterAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chapter, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (!isProgressBarShown()) {
            int __id = item.getItemId();
            if (__id == R.id.chapter_all) {
                    for (Switcher<Chapter> switcher : mChapterAdapter.getDateSet()) {
                        switcher.setEnable(true);
                    }
                    mChapterAdapter.notifyDataSetChanged();
            } else if (__id == R.id.chapter_none) {
                    for (Switcher<Chapter> switcher : mChapterAdapter.getDateSet()) {
                        if (!(switcher.getElement().isDownload())) {
                            switcher.setEnable(false);
                        }
                    }
                    mChapterAdapter.notifyDataSetChanged();
            } else if (__id == R.id.chapter_opposition) {
                    for (Switcher<Chapter> switcher : mChapterAdapter.getDateSet()) {
                        if (!(switcher.getElement().isDownload())) {
                            switcher.setEnable(!switcher.isEnable());
                        }
                    }
                    mChapterAdapter.notifyDataSetChanged();
            } else if (__id == R.id.chapter_sort) {
                    mChapterAdapter.reverse();
                    isAscendMode = !isAscendMode;
                    mPreference.putBoolean(PreferenceManager.PREF_CHAPTER_ASCEND_MODE, isAscendMode);
            } else if (__id == R.id.chapter_switch_view) {
                    isButtonMode = !isButtonMode;
                    switchMode();
                    mPreference.putBoolean(PreferenceManager.PREF_CHAPTER_BUTTON_MODE, isButtonMode);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(View view, int position) {
        Switcher<Chapter> switcher = mChapterAdapter.getItem(position);
        if (!switcher.getElement().isDownload()) {
            switcher.switchEnable();
            mChapterAdapter.notifyItemChanged(position);
        }
    }

    void onActionButtonClick() {
        ArrayList<Chapter> list = new ArrayList<>();
        for (Switcher<Chapter> switcher : mChapterAdapter.getDateSet()) {
            if (!switcher.getElement().isDownload() && switcher.isEnable()) {
                list.add(switcher.getElement());
            }
        }

        if (list.isEmpty()) {
            showSnackbar(R.string.chapter_download_empty);
        } else if (PermissionUtils.hasStoragePermission(this)) {
            Intent intent = new Intent();
            intent.putExtra(Extra.EXTRA_CHAPTER_KEY, putResultList(list));
            setResult(Activity.RESULT_OK, intent);
            finish();
        } else {
            showSnackbar(R.string.chapter_download_perm_fail);
        }
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_chapter;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 正常结束（isFinishing）才清理缓存；被系统重建销毁时（isFinishing=false）保留，
        // 使重建后的实例仍能通过同一 key 取到章节列表。
        if (isFinishing() && mChapterKey != -1) {
            sChapterCache.remove(mChapterKey);
        }
    }

    @Override
    protected String getDefaultTitle() {
        return getString(R.string.chapter);
    }

    class CustomTouchListener implements RecyclerView.OnItemTouchListener {
        private int mLastPosition = -1;
        private boolean isLongPress = false;

        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            if (isBookmarkTouch(rv, e)) {
                mHandler.removeCallbacksAndMessages(null);
                mLastPosition = -1;
                return false;
            }
            if (isLongPress) {
                return true;
            }

            View itemView = rv.findChildViewUnder(e.getX(), e.getY());
            int pos = itemView == null ? RecyclerView.NO_POSITION : rv.getChildAdapterPosition(itemView);
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mLastPosition = pos;
                    if (mLastPosition != -1) {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                isLongPress = true;
                                update(mLastPosition);
                            }
                        }, 500);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mLastPosition != pos) {
                        mHandler.removeCallbacksAndMessages(null);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    mHandler.removeCallbacksAndMessages(null);
                    if (pos != -1 && mLastPosition == pos) {
                        update(pos);
                    }
                    break;
            }
            return false;
        }

        private boolean isBookmarkTouch(RecyclerView rv, MotionEvent event) {
            View itemView = rv.findChildViewUnder(event.getX(), event.getY());
            if (itemView == null) {
                return false;
            }
            View bookmark = itemView.findViewById(R.id.item_chapter_bookmark);
            if (bookmark == null || bookmark.getVisibility() != View.VISIBLE) {
                return false;
            }
            float x = event.getX() - itemView.getLeft();
            float y = event.getY() - itemView.getTop();
            return x >= bookmark.getLeft() && x <= bookmark.getRight()
                    && y >= bookmark.getTop() && y <= bookmark.getBottom();
        }

        @Override
        public void onTouchEvent(RecyclerView rv, MotionEvent e) {
            int pos = rv.getChildAdapterPosition(Objects.requireNonNull(rv.findChildViewUnder(e.getX(), e.getY())));
            switch (e.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (pos != -1 && mLastPosition != pos) {
                        update(pos);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    isLongPress = false;
                    break;
            }
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        }

        private void update(int pos) {
            mLastPosition = pos;
            mChapterAdapter.getItem(pos).switchEnable();
            mChapterAdapter.notifyItemChanged(pos);
        }
    }

}
