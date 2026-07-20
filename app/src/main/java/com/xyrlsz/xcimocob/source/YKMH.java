package com.xyrlsz.xcimocob.source;

import android.util.Log;

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

import org.json.JSONArray;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.Request;

public class YKMH extends MangaParser {
    public static final int TYPE = 91;
    public static final String DEFAULT_TITLE = "优酷漫画";
    public static final String mHost = "https://m.ykmh.net/";
    public final String Host = "https://www.ykmh.com/";

    public YKMH(Source source) {
        init(source);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, mHost);
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException {
        Log.d("SourceSearch:", String.valueOf(keyword));
        if (page != 1) {
            return null;
        }
        return new Request.Builder()
                .url(mHost + "search/?keywords=" + keyword + "&page=" + page)
                .addHeader("referer", mHost.concat("/"))
                .addHeader("user-agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                .build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        Node body = new Node(html);
        return new NodeIterator(body.list("#update_list > div.UpdateList > div")) {
            @Override
            protected Comic parse(Node node) {
                Node titleN = node.getChild("div.itemTxt > a");
                String cid = titleN.hrefWithLastSplit();
                String title = titleN.text();
                String cover = node.attr("div.itemImg > a > img", "src");
                String Update = node.text("p.txtItme > span.date");
                String Author = node.text("p > a");
                return new Comic(TYPE, cid, title, cover, Update, Author);
            }
        };
    }

    @Override
    public String getUrl(String cid) {
        return StringUtils.format("%smanhua/%s/", mHost, cid);
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("m.ykmh.net", "/manhua/(\\w.+)"));

    }

    @Override
    public Headers getHeader() {
        return Headers.of("Referer", mHost.concat("/"), "user-agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
    }

    @Override
    public Request getInfoRequest(String cid) {
        Log.d("SourceInfo:", String.valueOf(cid));

        return new Request.Builder()
                .url(mHost.concat("manhua/").concat(cid).concat("/"))
                .addHeader("referer", mHost.concat("/"))
                .addHeader("user-agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                .build();

    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException {
        Node body = new Node(html);
        Node info = body.getChild("div.Introduct_Sub");
        String title = body.text("div#comicName");
        String cover = info.getChild("div#Cover > *").src();
        String update = info.text("p.txtItme > span.date");
        String author = info.getParent("p.txtItme > span.icon01").text();
        String intro = body.getParent("p#full-des #showmore-des").text().replace("展开", "");
        String isFinish = info.getParent("p.txtItme > span.icon01").text();
        boolean finish = isFinish.contains("完结");
        comic.setInfo(title, cover, update, intro, author, finish);
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        List<Node> types = body.list("div.comic-chapters > div > div > span.Title");
        List<Node> groups = body.list("div.chapter-body");
        int i = 0;
        for (int j = 0; j < types.size(); j++) {
            String type = types.get(j).text();
            Node group = groups.get(j);
            for (Node node : group.list("div.chapter-warp ul.Drama > li > a")) {
                String title = node.text();
//            String path = StringUtils.split(node.href(), "/", 3);
                String path = node.hrefWithSubString(1);
                Long id = IdCreator.createChapterId(sourceComic, i++);
                list.add(new Chapter(id, sourceComic, title, path, type));
            }
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        Log.d("SourceImage:", String.valueOf(path));

        return new Request.Builder()
                .url(mHost + path)
                .addHeader("referer", mHost.concat("/"))
                .addHeader("user-agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                .build();
    }


    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException {
        List<ImageUrl> list = new LinkedList<>();

        // 1. 提取 pageImage 的完整 URL，并解析出域名
        String baseDomain = extractDomainFromPageImage(html);
        if (baseDomain == null) {
            baseDomain = "";
        }

        // 2. 提取 chapterImages 数组
        Matcher matcher = Pattern.compile(
                "var chapterImages\\s*=\\s*(\\[[\\s\\S]*?]);",
                Pattern.DOTALL
        ).matcher(html);

        if (!matcher.find()) {
            return Collections.emptyList(); // 或抛出明确异常
        }

        String arrayJson = matcher.group(1);
        try {
            JSONArray array = new JSONArray(arrayJson);
            for (int i = 0; i < array.length(); i++) {
                String url = array.getString(i);
                // 3. 补全域名（如果当前 URL 是相对路径）
                String urlDomain = extractDomainFromPageImage(url);
                String fullUrl;
                if (StringUtils.isEmpty(urlDomain)) {
                    fullUrl = resolveUrl(baseDomain, url);
                } else {
                    fullUrl = url;
                }
                long comicChapter = chapter.getId();
                long id = IdCreator.createImageId(comicChapter, i);
                list.add(new ImageUrl(id, comicChapter, i + 1, fullUrl, false));
            }
        } catch (JSONException e) {
            Log.e("parseImages", "Failed to parse JSON array: " + arrayJson, e);

        }
        return list;
    }

    /**
     * 从 HTML 中提取 pageImage 变量的完整 URL，并返回其域名（协议+主机部分）
     * 例如：https://fm.haotuyk.top/images/... -> https://fm.haotuyk.top
     */
    private String extractDomainFromPageImage(String html) {
        Pattern pattern = Pattern.compile("var pageImage\\s*=\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String pageImageUrl = matcher.group(1);
            try {
                URI uri = new URI(pageImageUrl);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (scheme != null && host != null) {
                    return scheme + "://" + host;
                }
            } catch (URISyntaxException e) {
                Log.w("parseImages", "Invalid pageImage URL: " + pageImageUrl, e);
            }
        }
        return null;
    }

    /**
     * 补全 URL：如果 url 是相对路径（不以 http:// 或 https:// 开头），则拼接 baseDomain
     */
    private String resolveUrl(String baseDomain, String url) {
        if (baseDomain == null || baseDomain.isEmpty()) {
            return url;
        }
        // 如果已经是绝对 URL 或协议相对 URL，直接返回
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//")) {
            return url;
        }
        // 相对路径：确保拼接时没有重复斜杠
        if (url.startsWith("/")) {
            return baseDomain + url;
        } else {
            return baseDomain + "/" + url;
        }
    }

    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }


    @Override
    public String getTitle() {
        return DEFAULT_TITLE;
    }

}
