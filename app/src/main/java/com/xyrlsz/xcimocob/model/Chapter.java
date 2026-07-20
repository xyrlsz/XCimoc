package com.xyrlsz.xcimocob.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

import androidx.annotation.NonNull;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.Unique;
import io.objectbox.converter.PropertyConverter;

/**
 * Created by Hiroshi on 2016/7/2.
 * fixed by Haleydu on 2020/8/25.
 * Modified by lx200916 on 2021/2/7
 */
@Entity
public class Chapter implements Parcelable {
    public final static Parcelable.Creator<Chapter> CREATOR = new Parcelable.Creator<Chapter>() {
        @Override
        public Chapter createFromParcel(Parcel source) {
            return new Chapter(source);
        }

        @Override
        public Chapter[] newArray(int size) {
            return new Chapter[size];
        }
    };
    @Convert(dbType = String.class, converter = SourceComicPathConverter.class)
    @Unique
    private Pair<Long, String> sourceComicPath;
    @Id(assignable = true)
    private long id;
    @Index
    private long sourceComic;
    private String title;
    private String path;
    private int count;
    private boolean complete;
    private boolean download;
    private long tid;
    private String sourceGroup;

    public Chapter(long id, long sourceComic, String title, String path, long tid) {
        this(id, sourceComic, title, path, 0, false, false, tid, "");
    }

    public Chapter(Long id, long sourceComic, String title, String path, long tid) {
        this(id == null ? 0L : id, sourceComic, title, path, 0, false, false, tid, "");
    }

    public Chapter(long id, long sourceComic, String title, String path, String sourceGroup) {
        this(id, sourceComic, title, path, 0, false, false, -1, sourceGroup);
    }

    public Chapter(Long id, long sourceComic, String title, String path, String sourceGroup) {
        this(id == null ? 0L : id, sourceComic, title, path, 0, false, false, -1, sourceGroup);
    }

    public Chapter(long id, long sourceComic, String title, String path) {
        this(id, sourceComic, title, path, 0, false, false, -1, "");
    }

    public Chapter(Long id, long sourceComic, String title, String path) {
        this(id == null ? 0L : id, sourceComic, title, path, 0, false, false, -1, "");
    }

//    public Chapter(String title, String path) {
//        this.title = title;
//        this.path = path;
//        this.count = 0;
//        this.complete = false;
//        this.download = false;
//        this.tid = -1;
//        this.sourceComicPath = new Pair<>(sourceComic, path);
//    }

    public Chapter(Parcel source) {
        this(source.readLong(), source.readLong(), source.readString(), source.readString(),
                source.readInt(), source.readByte() == 1, source.readByte() == 1, source.readLong(),
                "");
    }

    public Chapter(long id, long sourceComic, String title, String path, int progress, boolean b,
                   boolean b1, long id1) {
        this(id, sourceComic, title, path, progress, b, b1, id1, "");
    }

    public Chapter(long id, long sourceComic, String title, String path, int count,
                   boolean complete, boolean download, long tid, String sourceGroup) {
        this.id = id;
        this.sourceComic = sourceComic;
        this.title = title;
        this.path = path;
        this.count = count;
        this.complete = complete;
        this.download = download;
        this.tid = tid;
        this.sourceGroup = sourceGroup;
        this.sourceComicPath = new Pair<>(sourceComic, path);
    }

    public Chapter() {
    }

    public Chapter(Long id, Long sourceComic, String title, String path) {
        this(id == null ? 0L : id, sourceComic == null ? 0L : sourceComic, title, path);
    }

    public Chapter(long id, Long sourceComic, String title, String path) {
        this(id, sourceComic == null ? 0L : sourceComic, title, path);
    }

    public Chapter(Long id, Long sourceComic, String title, String path, String sourceGroup) {
        this(id, sourceComic == null ? 0L : sourceComic, title, path, sourceGroup);
    }

    public Chapter(Long id, Long sourceComic, String title, String path, long tid) {
        this(id, sourceComic == null ? 0L : sourceComic, title, path, tid);
    }

    public String getSourceGroup() {
        return sourceGroup == null ? "" : sourceGroup;
    }

    public void setSourceGroup(String sourceGroup) {
        this.sourceGroup = sourceGroup;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isDownload() {
        return download;
    }

    public long getTid() {
        return tid;
    }

    public void setTid(long tid) {
        this.tid = tid;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Pair<Long, String> getSourceComicPath() {
        return sourceComicPath;
    }

    public void setSourceComicPath(Pair<Long, String> sourceComicPath) {
        this.sourceComicPath = sourceComicPath;
    }

    public long getSourceComic() {
        return sourceComic;
    }

    public void setSourceComic(long sourceComic) {
        this.sourceComic = sourceComic;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Chapter && ((Chapter) o).path.equals(path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeLong(sourceComic);
        dest.writeString(title);
        dest.writeString(path);
        dest.writeInt(count);
        dest.writeByte((byte) (complete ? 1 : 0));
        dest.writeByte((byte) (download ? 1 : 0));
        dest.writeLong(tid);
    }

    public boolean getComplete() {
        return this.complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public boolean getDownload() {
        return this.download;
    }

    public void setDownload(boolean download) {
        this.download = download;
    }

    public static class SourceComicPathConverter implements PropertyConverter<Pair<Long, String>, String> {
        private static final String SPLIT = "##XCimoc:SourceComicPathConverter##";

        @Override
        public Pair<Long, String> convertToEntityProperty(String databaseValue) {
            if (databaseValue == null) {
                return null;
            }
            String[] split = databaseValue.split(SPLIT);
            return new Pair<>(Long.parseLong(split[0]), split[1]);
        }

        @Override
        public String convertToDatabaseValue(Pair<Long, String> entityProperty) {
            if (entityProperty == null) {
                return null;
            }
            return entityProperty.first + SPLIT + entityProperty.second;
        }
    }
}
