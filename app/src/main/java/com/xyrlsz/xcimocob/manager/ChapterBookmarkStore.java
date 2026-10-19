package com.xyrlsz.xcimocob.manager;

import android.content.Context;
import android.content.SharedPreferences;

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

    private static String getKey(long sourceComic) {
        return "comic_" + sourceComic;
    }
}
