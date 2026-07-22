package android.content;

import android.content.res.Resources;

/**
 * Stub for android.content.Context
 */
public class Context {
    public static final int MODE_PRIVATE = 0;
    public static final int MODE_WORLD_READABLE = 1;

    public String getPackageName() { return "com.xyrlsz.xcimocob.test"; }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return new SharedPreferences();
    }

    public ContentResolver getContentResolver() { return new ContentResolver(); }

    public Resources getResources() { return new Resources(); }

    public String getString(int id) { return ""; }

    public class ContentResolver {}
}
