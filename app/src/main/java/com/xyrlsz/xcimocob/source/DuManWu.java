package com.xyrlsz.xcimocob.source;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.JsonIterator;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.parser.SearchIterator;
import com.xyrlsz.xcimocob.parser.UrlFilter;
import com.xyrlsz.xcimocob.soup.Node;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by xyrlsz on 2025/01/15.
 */

public class DuManWu extends MangaParser {

    public static final int TYPE = 104;
    public static final String DEFAULT_TITLE = "读漫屋";
    private static final String baseUrl = "http://dumanwu1.com";

    public DuManWu(Source source) {
        init(source);
        setParseImagesUseWebParser(true);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, baseUrl);
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException {
        if (page == 1) {
            String url = baseUrl + "/s";
            int index = Math.min(keyword.length(), 12);
            RequestBody body = RequestBody.create("k=" + keyword.substring(0, index), MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"));
            return new Request.Builder().url(url).post(body).build();
        }
        return null;
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        try {
            JSONObject object = new JSONObject(html);
            JSONArray data = object.getJSONArray("data");
            return new JsonIterator(data) {
                @Override
                protected Comic parse(JSONObject object) throws JSONException {
                    String cid = object.getString("id");
                    String cover = object.getString("imgurl");
                    String title = object.getString("name");
                    String update = object.getString("remarks");

                    return new Comic(TYPE, cid, title, cover, update, "");
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getUrl(String cid) {
        return baseUrl + "/" + cid;
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("dumanwu.com", ".*", 0));
        filter.add(new UrlFilter("dumanwu1.com", ".*", 0));
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = baseUrl + "/" + cid;
        return new Request.Builder().url(url).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException {
        Node body = new Node(html);
        String title = body.text(".banner-title");
        String cover = body.attr(".banner-pic", "data-src");
        String[] tmp = body.text(".author").split(" ");
        StringBuilder author = new StringBuilder();
        String update = "";

        for (String data : tmp) {
            if (data.contains("作者")) {
                author = new StringBuilder(data.replace("作者：", ""));
            } else if (data.contains("月") && data.contains("日")) {
                update = data;
            } else if (!data.contains("同步")) {
                author.append(",").append(data);
            }

        }
        String intro = body.text(".introduction");
        comic.setInfo(title, cover, update, intro, author.toString(), false);
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) {
        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        List<Node> chapterNodes = body.list(".chaplist-box > ul > li > a");

        int i = 0;

        for (Node chapterNode : chapterNodes) {
            String title = chapterNode.text();
            String path = "";
            try {
                String[] parts = chapterNode.href().split("/");
                if (parts.length > 2) {
                    path = parts[2].replace(".html", "");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            list.add(new Chapter(IdCreator.createChapterId(sourceComic, i++), sourceComic, title, path));
        }

        if (html.contains("chaplist-more")) {
            RequestBody reqBody = RequestBody.create(MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"), "id=" + comic.getCid());
            Request request = new Request.Builder().url(baseUrl + "/morechapter").post(reqBody).build();
            try {
                Response response = App.getHttpClient().newCall(request).execute();
                if (response.isSuccessful()) {
                    JSONObject object = new JSONObject(response.body().string());
                    JSONArray data = object.getJSONArray("data");
                    for (int j = 0; j < data.length(); j++) {
                        JSONObject item = data.getJSONObject(j);
                        String title = item.getString("chaptername");
                        String path = item.getString("chapterid");
                        Long id = IdCreator.createChapterId(sourceComic, j + i);
                        list.add(new Chapter(id, sourceComic, title, path));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return list;
    }


    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("%s/%s/%s.html", baseUrl, cid, path);
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) {
        List<ImageUrl> list = new ArrayList<>();
        Node body = new Node(html);
        List<Node> imageNodes = body.list(".main_img > .chapter-img-box");
        for (int i = 1; i <= imageNodes.size(); i++) {
            long comicChapter = chapter.getId();
            long id = IdCreator.createImageId(comicChapter, i);
            String imgUrl = imageNodes.get(i - 1).attr("img", "data-src");
            list.add(new ImageUrl(id, comicChapter, i, imgUrl, false));
        }
        return list;
    }


    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public Headers getHeader() {
        return Headers.of("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Safari/537.36");
    }
}
