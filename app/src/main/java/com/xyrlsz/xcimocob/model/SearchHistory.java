package com.xyrlsz.xcimocob.model;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

/**
 * 搜索历史记录实体
 * Created by XCimoc on 2026/07/22.
 */
@Entity
public class SearchHistory {

    @Id
    private long id;
    private String keyword;
    private long timestamp;

    public SearchHistory() {
    }

    public SearchHistory(long id, String keyword, long timestamp) {
        this.id = id;
        this.keyword = keyword;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SearchHistory && ((SearchHistory) o).id == id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
