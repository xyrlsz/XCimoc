package com.xyrlsz.xcimocob.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.Transient;
import io.objectbox.converter.PropertyConverter;
import okhttp3.Headers;

/**
 * Created by Hiroshi on 2016/8/20.
 */
@Entity
public class ImageUrl {
    public static final int STATE_NULL = 0;
    public static final int STATE_PAGE_1 = 1;
    public static final int STATE_PAGE_2 = 2;

    @Id(assignable = true)
    private long id; // 唯一标识
    @Index
    private long comicChapter;
    private int num; // 章节的第几页
    @Convert(dbType = String.class, converter = ImageUrlStringConverter.class)
    private List<String> urls;
    private String chapter; // 所属章节
    private int state; // 切图时表示状态 这里可以改为编号 比如长图可以切为多张方便加载
    private int height; // 图片高度
    private int width; // 图片宽度
    private boolean lazy; // 懒加载
    private boolean loading; // 正在懒加载
    private boolean success; // 图片显示成功
    private boolean download; // 下载的图片
    @Transient
    private Headers headers;

    public ImageUrl(long id, long comicChapter, int num, String[] urls, String chapter, int state,
                    boolean lazy) {
        this(id, comicChapter, num, urls, chapter, state, 0, 0, lazy, false, false, false, null);
    }

    public ImageUrl(long id, long comicChapter, int num, String url, boolean lazy) {
        this(id, comicChapter, num, new String[]{url}, null, STATE_NULL, 0, 0, lazy, false, false,
                false, null);
    }

    public ImageUrl(long id, long comicChapter, int num, String[] urls, String chapter, int state,
                    boolean lazy, Headers headers) {
        this(id, comicChapter, num, urls, chapter, state, 0, 0, lazy, false, false, false, headers);
    }

    public ImageUrl(
            long id, long comicChapter, int num, String url, boolean lazy, Headers headers) {
        this(id, comicChapter, num, new String[]{url}, null, STATE_NULL, 0, 0, lazy, false, false,
                false, headers);
    }

    public ImageUrl(long id, long comicChapter, int num, String[] urls, String chapter, int state,
                    int height, int width, boolean lazy, boolean loading, boolean success, boolean download) {
        this.id = id;
        this.comicChapter = comicChapter;
        this.num = num;
        this.urls = Arrays.asList(urls);
        this.chapter = chapter;
        this.state = state;
        this.height = height;
        this.width = width;
        this.lazy = lazy;
        this.loading = loading;
        this.success = success;
        this.download = download;
    }

    public ImageUrl(long id, long comicChapter, int num, String[] urls, String chapter, int state,
                    int height, int width, boolean lazy, boolean loading, boolean success, boolean download,
                    Headers headers) {
        this.id = id;
        this.comicChapter = comicChapter;
        this.num = num;
        this.urls = Arrays.asList(urls);
        this.chapter = chapter;
        this.state = state;
        this.height = height;
        this.width = width;
        this.lazy = lazy;
        this.loading = loading;
        this.success = success;
        this.download = download;
        this.headers = headers;
    }

    public ImageUrl() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(String[] urls) {
        this.urls = Arrays.asList(urls);
    }

    public String getUrl() {
        return urls.get(0);
    }

    public void setUrl(String url) {
        this.urls = Collections.singletonList(url);
    }

    public String getChapter() {
        return chapter;
    }

    public void setChapter(String chapter) {
        this.chapter = chapter;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public long getSize() {
        return (long) height * width;
    }

    public boolean isLazy() {
        return lazy;
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isDownload() {
        return download;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ImageUrl && Objects.equals(((ImageUrl) o).id, id);
    }

    public long getComicChapter() {
        return this.comicChapter;
    }

    public void setComicChapter(long comicChapter) {
        this.comicChapter = comicChapter;
    }

    public boolean getLazy() {
        return this.lazy;
    }

    public void setLazy(boolean lazy) {
        this.lazy = lazy;
    }

    public boolean getLoading() {
        return this.loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public boolean getSuccess() {
        return this.success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean getDownload() {
        return this.download;
    }

    public void setDownload(boolean download) {
        this.download = download;
    }

    public Headers getHeaders() {
        return this.headers;
    }

    public void setHeaders(Headers headers) {
        this.headers = headers;
    }

    public static class ImageUrlStringConverter implements PropertyConverter<List<String>, String> {
        private static final String SPLIT = "##XCimoc:ImageUrl##";

        @Override
        public List<String> convertToEntityProperty(String databaseValue) {
            if (databaseValue == null) return null;
            return Arrays.asList(databaseValue.split(SPLIT));
        }

        @Override
        public String convertToDatabaseValue(List<String> entityProperty) {
            if (entityProperty == null) return null;
            StringBuilder sb = new StringBuilder();
            for (String str : entityProperty) {
                sb.append(str).append(SPLIT);
            }
            return sb.toString();
        }
    }
}
