package com.xyrlsz.xcimocob.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.model.ChapterBookmark;
import com.xyrlsz.xcimocob.model.ChapterBookmark_;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.Query;

/**
 * 章节书签与备注存储，数据保存在 ObjectBox 中。
 */
public final class ChapterBookmarkStore {

    private static final String LEGACY_PREFERENCES_NAME = "chapter_bookmarks";
    private static final String LEGACY_COMIC_PREFIX = "comic_";
    private static final String LEGACY_NOTE_PREFIX = "note_";

    private static volatile boolean sLegacyMigrated;
    private static boolean sLegacyMigrating;

    private ChapterBookmarkStore() {
    }

    public static Set<String> getBookmarks(Context context, long sourceComic) {
        migrateLegacyData(context);
        Set<String> result = new HashSet<>();
        if (sourceComic == -1L) {
            return result;
        }
        Box<ChapterBookmark> box = getBox();
        if (box == null) {
            return result;
        }
        try (Query<ChapterBookmark> query = box.query()
                .equal(ChapterBookmark_.sourceComic, sourceComic)
                .build()) {
            for (ChapterBookmark bookmark : query.find()) {
                if (bookmark.isBookmarked()) {
                    result.add(bookmark.getPath());
                }
            }
        }
        return result;
    }

    public static void setBookmarked(Context context, long sourceComic, String path, boolean bookmarked) {
        if (!isValidKey(sourceComic, path)) {
            return;
        }
        migrateLegacyData(context);
        Box<ChapterBookmark> box = getBox();
        if (box == null) {
            return;
        }
        ChapterBookmark bookmark = find(box, sourceComic, path);
        long now = System.currentTimeMillis();
        if (bookmarked) {
            if (bookmark == null) {
                bookmark = new ChapterBookmark(sourceComic, path);
                bookmark.setCreatedAt(now);
            }
            bookmark.setBookmarked(true);
            bookmark.setUpdatedAt(now);
            box.put(bookmark);
        } else if (bookmark != null) {
            if (bookmark.getNote().isEmpty()) {
                box.remove(bookmark);
            } else {
                bookmark.setBookmarked(false);
                bookmark.setUpdatedAt(now);
                box.put(bookmark);
            }
        }
    }

    public static String getNote(Context context, long sourceComic, String path) {
        if (!isValidKey(sourceComic, path)) {
            return "";
        }
        migrateLegacyData(context);
        Box<ChapterBookmark> box = getBox();
        if (box == null) {
            return "";
        }
        ChapterBookmark bookmark = find(box, sourceComic, path);
        return bookmark == null ? "" : bookmark.getNote();
    }

    public static void setNote(Context context, long sourceComic, String path, String note) {
        if (!isValidKey(sourceComic, path)) {
            return;
        }
        migrateLegacyData(context);
        Box<ChapterBookmark> box = getBox();
        if (box == null) {
            return;
        }
        String text = note == null ? "" : note.trim();
        ChapterBookmark bookmark = find(box, sourceComic, path);
        if (text.isEmpty()) {
            if (bookmark == null) {
                return;
            }
            if (bookmark.isBookmarked()) {
                bookmark.setNote("");
                bookmark.setUpdatedAt(System.currentTimeMillis());
                box.put(bookmark);
            } else {
                box.remove(bookmark);
            }
            return;
        }
        if (bookmark == null) {
            long now = System.currentTimeMillis();
            bookmark = new ChapterBookmark(sourceComic, path);
            bookmark.setCreatedAt(now);
            bookmark.setUpdatedAt(now);
        } else {
            bookmark.setUpdatedAt(System.currentTimeMillis());
        }
        bookmark.setNote(text);
        box.put(bookmark);
    }

    public static void saveNote(Context context, long sourceComic, String path, String note) {
        setNote(context, sourceComic, path, note);
        if (note != null && !note.trim().isEmpty()) {
            setBookmarked(context, sourceComic, path, true);
        }
    }

    private static ChapterBookmark find(Box<ChapterBookmark> box, long sourceComic, String path) {
        try (Query<ChapterBookmark> query = box.query(
                ChapterBookmark_.sourceComic.equal(sourceComic)
                        .and(ChapterBookmark_.path.equal(path)))
                .build()) {
            return query.findFirst();
        }
    }

    private static Box<ChapterBookmark> getBox() {
        App app = App.getApp();
        if (app == null) {
            return null;
        }
        BoxStore boxStore = app.getBoxStore();
        return boxStore == null ? null : boxStore.boxFor(ChapterBookmark.class);
    }

    private static boolean isValidKey(long sourceComic, String path) {
        return sourceComic != -1L && path != null && !path.isEmpty();
    }

    /**
     * 将旧版本 SharedPreferences 中的书签与备注导入数据库，只执行一次。
     */
    private static void migrateLegacyData(Context context) {
        if (sLegacyMigrated || sLegacyMigrating || context == null) {
            return;
        }
        synchronized (ChapterBookmarkStore.class) {
            if (sLegacyMigrated || sLegacyMigrating) {
                return;
            }
            sLegacyMigrating = true;
            try {
                SharedPreferences preferences = context.getApplicationContext()
                        .getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE);
                Map<String, ?> values = preferences.getAll();
                for (Map.Entry<String, ?> entry : values.entrySet()) {
                    migrateLegacyEntry(context, entry.getKey(), entry.getValue());
                }
                if (!values.isEmpty()) {
                    preferences.edit().clear().apply();
                }
                sLegacyMigrated = true;
            } finally {
                sLegacyMigrating = false;
            }
        }
    }

    private static void migrateLegacyEntry(Context context, String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        if (key.startsWith(LEGACY_COMIC_PREFIX) && value instanceof Set) {
            long sourceComic = parseLong(key.substring(LEGACY_COMIC_PREFIX.length()));
            if (sourceComic == -1L) {
                return;
            }
            for (Object path : (Set<?>) value) {
                if (path instanceof String && !((String) path).isEmpty()) {
                    setBookmarked(context, sourceComic, (String) path, true);
                }
            }
        } else if (key.startsWith(LEGACY_NOTE_PREFIX) && value instanceof String) {
            String rest = key.substring(LEGACY_NOTE_PREFIX.length());
            int split = rest.indexOf('_');
            if (split <= 0) {
                return;
            }
            long sourceComic = parseLong(rest.substring(0, split));
            String path = decodePath(rest.substring(split + 1));
            if (sourceComic == -1L || path.isEmpty()) {
                return;
            }
            setNote(context, sourceComic, path, (String) value);
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static String decodePath(String encoded) {
        try {
            byte[] bytes = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
