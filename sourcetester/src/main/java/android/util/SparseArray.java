package android.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Stub for android.util.SparseArray
 */
public class SparseArray<E> {
    private final Map<Integer, E> mMap = new HashMap<>();

    public E get(int key) {
        return mMap.get(key);
    }

    public E get(int key, E valueIfKeyNotFound) {
        return mMap.getOrDefault(key, valueIfKeyNotFound);
    }

    public void put(int key, E value) {
        mMap.put(key, value);
    }

    public void append(int key, E value) {
        mMap.put(key, value);
    }

    public void delete(int key) {
        mMap.remove(key);
    }

    public void remove(int key) {
        mMap.remove(key);
    }

    public void clear() {
        mMap.clear();
    }

    public int size() {
        return mMap.size();
    }

    public int keyAt(int index) {
        return (Integer) mMap.keySet().toArray()[index];
    }

    public E valueAt(int index) {
        return (E) mMap.values().toArray()[index];
    }

    public int indexOfKey(int key) {
        return mMap.containsKey(key) ? 0 : -1;
    }

    public void setValueAt(int index, E value) {
        int key = keyAt(index);
        mMap.put(key, value);
    }
}
