package android.os;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub for android.os.Parcel
 */
public class Parcel {
    private final ArrayList<Object> mData = new ArrayList<>();

    public static Parcel obtain() {
        return new Parcel();
    }

    public void recycle() {}

    public int dataSize() { return 0; }
    public int dataPosition() { return 0; }
    public void setDataPosition(int pos) {}

    public void writeInt(int val) { mData.add(val); }
    public void writeLong(long val) { mData.add(val); }
    public void writeString(String val) { mData.add(val); }
    public void writeByte(byte val) { mData.add(val); }

    public int readInt() { return popInt(); }
    public long readLong() { return popLong(); }
    public String readString() { return popString(); }
    public byte readByte() { return popByte(); }

    private int popInt() {
        for (int i = 0; i < mData.size(); i++) {
            if (mData.get(i) instanceof Integer) return (Integer) mData.remove(i);
        }
        return 0;
    }

    private long popLong() {
        for (int i = 0; i < mData.size(); i++) {
            if (mData.get(i) instanceof Long) return (Long) mData.remove(i);
        }
        return 0;
    }

    private String popString() {
        for (int i = 0; i < mData.size(); i++) {
            if (mData.get(i) instanceof String) return (String) mData.remove(i);
        }
        return null;
    }

    private byte popByte() {
        for (int i = 0; i < mData.size(); i++) {
            if (mData.get(i) instanceof Byte) return (Byte) mData.remove(i);
        }
        return 0;
    }

    public void writeList(List<?> list) { mData.add(list); }
    public void readList(List<?> list, ClassLoader loader) {}
    public void writeTypedList(List<? extends Parcelable> list) { mData.add(list); }
    public <T extends Parcelable> ArrayList<T> readTypedList(Parcelable.Creator<T> creator) {
        return new ArrayList<>();
    }
    public void writeTypedObject(Parcelable obj, int flags) { mData.add(obj); }
    public <T extends Parcelable> T readTypedObject(Parcelable.Creator<T> creator) {
        return null;
    }

    public void writeStringArray(String[] val) { mData.add(val); }
    public String[] readStringArray() { return new String[0]; }

    public void writeStrongBinder(IBinder binder) {}
    public IBinder readStrongBinder() { return null; }

    // Stub for IBinder
    public interface IBinder {}

    public interface Creator<T> {
        T createFromParcel(Parcel source);
        T[] newArray(int size);
    }
}
