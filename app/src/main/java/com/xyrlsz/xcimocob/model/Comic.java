package com.xyrlsz.xcimocob.model;

import android.util.Pair;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.Transient;
import io.objectbox.annotation.Unique;
import io.objectbox.converter.PropertyConverter;

/**
 * Created by Hiroshi on 2016/7/20.
 */
@Entity
public class Comic {
    @Transient
    public Object note;
    @Id(assignable = true)
    private long id;
    @Convert(dbType = String.class, converter = SourceCidConverter.class)
    @Unique
    private Pair<Integer, String> sourceCid;
    @Index
    private int source;
    private String cid;
    private String title;
    private String cover;
    private boolean highlight;
    private boolean local;
    private String update;
    private Boolean finish;
    private Long favorite;
    private Long history;
    private Long download;
    private String last;
    private Integer page;
    private String chapter;
    private String url;
    private Integer chapterCount;
    private String intro;
    private String author;

    public Comic(int source, String cid, String title, String cover, String update, String author) {
        this(0, source, cid, title, cover == null ? "" : cover, false, false, update, null, null,
                null, null, null, null, null, null, 0, null, null);
        this.author = author;
    }

    public Comic(int source, String cid) {
        this.source = source;
        this.cid = cid;
        this.sourceCid = new Pair<>(source, cid);
    }

    public Comic(int source, String cid, String title, String cover, long download) {
        this(0, source, cid, title, cover == null ? "" : cover, false, false, null, null, null,
                null, download, null, null, null, null, 0, null, null);
    }

    public Comic(Long id, int source, String cid, String title, String cover, boolean highlight,
                 boolean local, String update, Boolean finish, Long favorite, Long history, Long download,
                 String last, Integer page, String chapter, String url, Integer chapterCount, String intro,
                 String author) {
        this(id == null ? 0 : id, source, cid, title, cover, highlight, local, update, finish, favorite, history, download, last, page, chapter, url, chapterCount, intro, author);

    }

    public Comic(long id, int source, String cid, String title, String cover, boolean highlight,
                 boolean local, String update, Boolean finish, Long favorite, Long history, Long download,
                 String last, Integer page, String chapter, String url, Integer chapterCount, String intro,
                 String author) {
        this.id = id;
        this.source = source;
        this.cid = cid;
        this.title = title;
        this.cover = cover;
        this.highlight = highlight;
        this.local = local;
        this.update = update;
        this.finish = finish;
        this.favorite = favorite;
        this.history = history;
        this.download = download;
        this.last = last;
        this.page = page;
        this.chapter = chapter;
        this.url = url;
        this.chapterCount = chapterCount;
        this.intro = intro;
        this.author = author;
        this.sourceCid = new Pair<>(source, cid);
    }

    public Comic() {
    }

    public void copyFrom(Comic comic) {
        this.setInfo(comic.getTitle(), comic.getCover(), comic.getUpdate(), comic.getIntro(),
                comic.getAuthor(), comic.getFinish());
        this.setUrl(comic.getUrl());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Comic && ((Comic) o).id == (id);
    }

    public void setInfo(
            String title, String cover, String update, String intro, String author, boolean finish) {
        if (title != null) {
            this.title = title;
        }
        if (cover != null) {
            this.cover = cover;
        }
        if (update != null) {
            this.update = update;
        }
        if (intro != null) {
            this.intro = intro;
        }
        if (author != null) {
            this.author = author;
        }
        this.finish = finish;
        this.highlight = false;
    }

    public String getIntro() {
        return this.intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
    }

    public String getAuthor() {
        return this.author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Integer getPage() {
        return this.page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public String getLast() {
        return this.last;
    }

    public void setLast(String last) {
        this.last = last;
    }

    public Long getHistory() {
        return this.history;
    }

    public void setHistory(Long history) {
        this.history = history;
    }

    public Long getFavorite() {
        return this.favorite;
    }

    public void setFavorite(Long favorite) {
        this.favorite = favorite;
    }

    public String getUpdate() {
        return this.update;
    }

    public void setUpdate(String update) {
        this.update = update;
    }

    public String getCover() {
        return this.cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCid() {
        return this.cid;
    }

    public void setCid(String cid) {
        this.cid = cid;
    }

    public int getSource() {
        return this.source;
    }

    public void setSource(int source) {
        this.source = source;
    }

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean getHighlight() {
        return this.highlight;
    }

    public void setHighlight(boolean highlight) {
        this.highlight = highlight;
    }

    public Long getDownload() {
        return this.download;
    }

    public void setDownload(Long download) {
        this.download = download;
    }

    public Boolean getFinish() {
        return this.finish;
    }

    public void setFinish(Boolean finish) {
        this.finish = finish;
    }

    public boolean getLocal() {
        return this.local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public String getChapter() {
        return this.chapter;
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getChapterCount() {
        return chapterCount;
    }

    public void setChapterCount(Integer count) {
        this.chapterCount = count;
    }

    public Pair<Integer, String> getSourceCid() {
        return this.sourceCid;
    }

    public void setSourceCid(Pair<Integer, String> sourceCid) {
        this.sourceCid = sourceCid;
    }

    public static class SourceCidConverter implements PropertyConverter<Pair<Integer, String>, String> {
        private static final String SPLIT = "##XCimoc:SourceCid##";

        @Override
        public Pair<Integer, String> convertToEntityProperty(String databaseValue) {
            if (databaseValue == null) {
                return null;
            }
            String[] split = databaseValue.split(SPLIT);
            return new Pair<>(Integer.parseInt(split[0]), split[1]);
        }

        @Override
        public String convertToDatabaseValue(Pair<Integer, String> entityProperty) {
            if (entityProperty == null) {
                return null;
            }
            return entityProperty.first + SPLIT + entityProperty.second;
        }
    }
}
