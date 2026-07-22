package com.xyrlsz.xcimocob.manager;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stub for PreferenceManager
 */
public class PreferenceManager {
    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        this.prefs = new SharedPreferences();
    }

    public SharedPreferences getSharedPreferences() {
        return prefs;
    }

    public int getInt(String key, int def) { return prefs.getInt(key, def); }
    public String getString(String key, String def) { return prefs.getString(key, def); }
    public boolean getBoolean(String key, boolean def) { return prefs.getBoolean(key, def); }
    public void putString(String key, String value) { prefs.edit().putString(key, value).apply(); }
    public void putInt(String key, int value) { prefs.edit().putInt(key, value).apply(); }
    public void putBoolean(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
}
