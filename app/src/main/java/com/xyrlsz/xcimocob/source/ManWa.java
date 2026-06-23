package com.xyrlsz.xcimocob.source;

import com.xyrlsz.xcimocob.core.Manga;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.parser.NodeIterator;
import com.xyrlsz.xcimocob.parser.SearchIterator;
import com.xyrlsz.xcimocob.parser.UrlFilter;
import com.xyrlsz.xcimocob.soup.Node;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.StringUtils;

import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import okhttp3.Headers;
import okhttp3.Request;

public class ManWa extends MangaParser {
    public static final int TYPE = 115;
    public static final String DEFAULT_TITLE = "漫蛙";
    private static final String baseUrl = "https://manwawang.com";
    private static final String picBaseUrl = "https://img1.baipiaoguai.org";
    private static final String UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36";

    public ManWa(Source source) {
        init(source);
        setParseImagesUseWebParser(true);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, baseUrl);
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws Exception {
        if (page != 1) {
            return null;
        }
        String url = baseUrl + "/search?key=" + keyword;
        return new Request.Builder()
                .url(url)
                .addHeader("user-agent", UA)
                .build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        Node body = new Node(html);
        List<Node> resList = body.list(".manga-i-list-item");
        if (resList.isEmpty()) {
            return null;
        }
        return new NodeIterator(resList) {
            @Override
            protected Comic parse(Node node) {
                String title = node.text(".manga-i-list-title");
                String cover = node.src(".manga-i-cover");
                String cid = node.href("a").replace("/comic/", "")
                        .replace("/", "");
                return new Comic(TYPE, cid, title, cover, "", "");
            }
        };
    }

    @Override
    public String getUrl(String cid) {
        return baseUrl + "/comic/" + cid;
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("manwawang.com"));
    }

    @Override
    public Request getInfoRequest(String cid) {
        return new Request.Builder()
                .url(getUrl(cid))
                .addHeader("user-agent", UA)
                .build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException, JSONException {
        Node body = new Node(html);
        String title = body.text(".detail-main-title");
        String cover = body.src(".detail-bar-img");
        List<Node> infoList = body.list(".detail-main-subtitle > span");
        String author = "";
        for (Node info : infoList) {
            if (info.text().contains("作者")) {
                author = info.text().split("：")[1].strip();
            }
        }
        String intro = body.text(".detail-main-content");
        comic.setInfo(title, cover, "", intro, author, isFinish(html));
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        int i = 0;
        List<Node> chapterNodes = body.list(".detail-list > .detail-list-item > a");
        for (Node node : chapterNodes) {
            String title = node.text();
            String path = node.href();
            Long id = IdCreator.createChapterId(sourceComic, i++);
            list.add(new Chapter(id, sourceComic, title, path));
        }

        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("%s/%s", baseUrl, path);
        return new Request.Builder()
                .addHeader("user-agent", UA)
                .url(url).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException, JSONException {
        List<ImageUrl> list = new ArrayList<>();
        Node body = new Node(html);
        List<Node> imageNodes = body.list("#chapterPic > img");
        for (int i = 1; i <= imageNodes.size(); i++) {
            long comicChapter = chapter.getId();
            long id = IdCreator.createImageId(comicChapter, i);
            String imgUrl = imageNodes.get(i - 1).attr("data-src");
            list.add(new ImageUrl(id, comicChapter, i, imgUrl, false, getHeader()));
        }
        return list;
    }

    @Override
    public Headers getHeader() {
        return Headers.of("Referer", baseUrl.concat("/"), "user-agent", UA);
    }
}
