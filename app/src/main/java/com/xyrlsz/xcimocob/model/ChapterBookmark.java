package com.xyrlsz.xcimocob.model;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;

/**
 * 章节书签与备注实体
 */
@Entity
public class ChapterBookmark {

    @Id
    private long id;
    @Index
    private long sourceComic;
    private String path;
    private boolean bookmarked;
    private String note;
    private long createdAt;
    private long updatedAt;

    public ChapterBookmark() {
    }

    public ChapterBookmark(long sourceComic, String path) {
        this.sourceComic = sourceComic;
        this.path = path;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSourceComic() {
        return sourceComic;
    }

    public void setSourceComic(long sourceComic) {
        this.sourceComic = sourceComic;
    }

    public String getPath() {
        return path == null ? "" : path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isBookmarked() {
        return bookmarked;
    }

    public void setBookmarked(boolean bookmarked) {
        this.bookmarked = bookmarked;
    }

    public String getNote() {
        return note == null ? "" : note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
