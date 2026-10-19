package com.xyrlsz.xcimocob.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.util.HashSet;
import java.util.Set;

public final class ChapterBookmarkStore {

    private static final String PREFERENCES_NAME = "chapter_bookmarks";

    private ChapterBookmarkStore() {
    }

    public static Set<String> getBookmarks(Context context, long sourceComic) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(preferences.getStringSet(getKey(sourceComic), new HashSet<>()));
    }

    public static void setBookmarked(Context context, long sourceComic, String path, boolean bookmarked) {
        if (sourceComic == -1L || path == null || path.isEmpty()) {
            return;
        }
        Set<String> bookmarks = getBookmarks(context, sourceComic);
        if (bookmarked) {
            bookmarks.add(path);
        } else {
            bookmarks.remove(path);
        }
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        preferences.edit().putStringSet(getKey(sourceComic), bookmarks).apply();
    }

    public static String getNote(Context context, long sourceComic, String path) {
        if (sourceComic == -1L || path == null || path.isEmpty()) {
            return "";
        }
        SharedPreferences preferences = getPreferences(context);
        return preferences.getString(getNoteKey(sourceComic, path), "");
    }

    public static void setNote(Context context, long sourceComic, String path, String note) {
        if (sourceComic == -1L || path == null || path.isEmpty()) {
            return;
        }
        SharedPreferences preferences = getPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        String key = getNoteKey(sourceComic, path);
        if (note == null || note.trim().isEmpty()) {
            editor.remove(key);
        } else {
            editor.putString(key, note.trim());
        }
        editor.apply();
    }

    public static void saveNote(Context context, long sourceComic, String path, String note) {
        setNote(context, sourceComic, path, note);
        if (note != null && !note.trim().isEmpty()) {
            setBookmarked(context, sourceComic, path, true);
        }
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static String getNoteKey(long sourceComic, String path) {
        String encodedPath = Base64.encodeToString(path.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP);
        return "note_" + sourceComic + "_" + encodedPath;
    }

    private static String getKey(long sourceComic) {
        return "comic_" + sourceComic;
    }
}
