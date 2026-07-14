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
import java.util.LinkedList;
import java.util.List;

import okhttp3.Headers;
import okhttp3.Request;

public class DongManHi extends MangaParser {
    public static final int TYPE = 118;
    public static final String DEFAULT_TITLE = "动漫嗨";
    public static final String baseUrl = "https://www.dongmanhi.com";

    public DongManHi(Source source) {
        init(source);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, baseUrl);
    }

    @Override
    public String getUrl(String cid) {
        return baseUrl + "/manhua/" + cid + "/";
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("www.dongmanhi.com"));
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException, Exception {
        if (page == 1) {
            return new Request.Builder()
                    .url(baseUrl + "/search?page=" + page + "&title=" + keyword)
                    .headers(getHeader())
                    .build();
        }
        return null;
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        Node body = new Node(html);
        return new NodeIterator(body.list("ul.mh-list > li > div.mh-item")) {
            @Override
            protected Comic parse(Node node) {
                String cid = StringUtils.getNumber(node.attr("a", "href"));
                String title = node.text(".mh-item-detali > .title");
                String cover = node.src(".mh-cover");
                return new Comic(TYPE, cid, title, cover, "", "");
            }
        };
    }

    @Override
    public Request getInfoRequest(String cid) {
        return new Request.Builder().url(getUrl(cid)).headers(getHeader()).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException, JSONException {
        Node body = new Node(html);
        String title = body.text(".detail-info-title");
        String cover = body.src(".detail-info-cover");
        List<Node> nodes = body.list(".detail-info-tip > span");
        String author = "";
        boolean status = false;
        for (Node node : nodes) {
            if (node.text().contains("作者：")) {
                author = node.text().replace("作者：", "");
            }
            if (node.text().contains("状态：")) {
                status = isFinish(node.text().replace("状态：", ""));
            }
        }
        String intro = body.text(".detail-info-content");
        comic.setInfo(title, cover, "", intro, author, status);
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        Node body = new Node(html);
        List<Chapter> list = new LinkedList<>();
        int i = 0;
        for (Node node : body.list("li.detail-list-form-item")) {
            String title = node.text("a");
            String path = node.attr("a", "href");
            long id = IdCreator.createChapterId(sourceComic, i++);
            list.add(new Chapter(id, sourceComic, title, path));
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        return new Request.Builder().url(path).headers(getHeader()).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException, JSONException {
        List<ImageUrl> list = new LinkedList<>();
        Node body = new Node(html);
        int i = 1;
        for (Node node : body.list("#cp_img > div")) {
            String url = node.attr("img", "data-original");
            if (StringUtils.isEmpty(url)) {
                url = node.attr("img", "src");
            }
            long comicChapter = chapter.getId();
            long id = IdCreator.createImageId(chapter.getId(), i++);
            list.add(new ImageUrl(id, comicChapter, i, url, false, getHeader()));
        }
        return list;
    }

    @Override
    public Headers getHeader() {
        return new Headers.Builder()
                .add("referer", baseUrl.concat("/"))
                .add("user-agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
                .build();
    }
}
