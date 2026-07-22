package android.content;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stub for android.content.SharedPreferences
 */
public class SharedPreferences {
    private final Map<String, Object> map = new HashMap<>();

    public String getString(String key, String defValue) {
        return (String) map.getOrDefault(key, defValue);
    }

    public int getInt(String key, int defValue) {
        return (int) map.getOrDefault(key, defValue);
    }

    public boolean getBoolean(String key, boolean defValue) {
        return (boolean) map.getOrDefault(key, defValue);
    }

    public long getLong(String key, long defValue) {
        return (long) map.getOrDefault(key, defValue);
    }

    public float getFloat(String key, float defValue) {
        return (float) map.getOrDefault(key, defValue);
    }

    public Set<String> getStringSet(String key, Set<String> defValue) {
        return defValue;
    }

    public boolean contains(String key) { return map.containsKey(key); }

    public Editor edit() { return new Editor(); }

    public class Editor {
        private final Map<String, Object> edits = new HashMap<>();

        public Editor putString(String key, String value) {
            edits.put(key, value);
            return this;
        }

        public Editor putInt(String key, int value) {
            edits.put(key, value);
            return this;
        }

        public Editor putBoolean(String key, boolean value) {
            edits.put(key, value);
            return this;
        }

        public Editor putLong(String key, long value) {
            edits.put(key, value);
            return this;
        }

        public Editor remove(String key) {
            edits.remove(key);
            return this;
        }

        public Editor clear() {
            edits.clear();
            return this;
        }

        public boolean commit() {
            map.putAll(edits);
            return true;
        }

        public void apply() {
            map.putAll(edits);
        }
    }

    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    public interface OnSharedPreferenceChangeListener {
        void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key);
    }
}
