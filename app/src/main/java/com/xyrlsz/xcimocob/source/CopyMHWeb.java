package com.xyrlsz.xcimocob.source;

import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_AREA;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_ORDER;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_PROGRESS;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_SUBJECT;
import static com.xyrlsz.xcimocob.parser.MangaCategory.getParseFormatMap;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.common.collect.Lists;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.Constants;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.JsonIterator;
import com.xyrlsz.xcimocob.parser.MangaCategory;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.parser.SearchIterator;
import com.xyrlsz.xcimocob.parser.UrlFilter;
import com.xyrlsz.xcimocob.soup.Node;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

public class CopyMHWeb extends MangaParser {
    public static final int TYPE = 27;
    public static final String DEFAULT_TITLE = "拷贝漫画Web";
    public static final String website = "https://www.copy3000.com";
    private final SharedPreferences sharedPreferences;
    public String searchApi;

    public CopyMHWeb(Source source) {
        init(source, new Category());
        setParseInfoUseWebParser(true);
        setParseImagesUseWebParser(true);
        sharedPreferences = App.getAppContext().getSharedPreferences(Constants.COPYMG_SHARED, Context.MODE_PRIVATE);
        searchApi = sharedPreferences.getString(Constants.COPYMG_SHARED_SEARCH_API, "/api/kb/web/searchci/comics");
        refreshSearchApi();
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, website);
    }

    private void refreshSearchApi() {
        String url = website + "/search?q=a&q_type=";
        Request request = new Request.Builder()
                .headers(getHeader())
                .url(url)
                .build();
        App.getHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {

            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String text = response.body().string();

                Pattern pattern = Pattern.compile("const countApi = \"([^\"]+)\"");
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    searchApi = matcher.group(1);
                    sharedPreferences.edit().putString(Constants.COPYMG_SHARED_SEARCH_API, searchApi).apply();
                }
            }
        });
    }

    @Override
    public String getUrl(String cid) {
        return StringUtils.format("%s/comic/%s", website, cid);
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("www.mangacopy.com", "comic/(\\w+)", 1));
        filter.add(new UrlFilter("www.copy20.com", "comic/(\\w+)", 1));
        filter.add(new UrlFilter("www.2025copy.com", "comic/(\\w+)", 1));
        filter.add(new UrlFilter("www.2026copy.com", "comic/(\\w+)", 1));
        filter.add(new UrlFilter("www.copy3000.com", "comic/(\\w+)", 1));
    }

    @Override
    public String getUA() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0";
    }


    @Override
    public Request getSearchRequest(String keyword, int page) {
        if (page == 1) {
            String url = website + searchApi + "?offset=0&platform=2&limit=12&q=" + keyword + "&q_type=";
            return new Request.Builder()
                    .headers(getHeader())
                    .url(url)
                    .build();
        }
        return null;
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        JSONObject data = new JSONObject(html);
        return new JsonIterator(data.getJSONObject("results").getJSONArray("list")) {
            @Override
            protected Comic parse(JSONObject object) throws JSONException {
                String cid = object.getString("path_word");
                String title = object.getString("name");
                String cover = object.getString("cover");
                String author = "";
                for (int i = 0; i < object.getJSONArray("author").length(); ++i) {
                    author += object.getJSONArray("author").getJSONObject(i).getString("name").trim();
                    if (i < object.getJSONArray("author").length() - 1) {
                        author += ",";
                    }
                }
                return new Comic(TYPE, cid, title, cover, null, author);
            }
        };

    }

    @Override
    public Request getInfoRequest(String cid) {
        return new Request.Builder()
                .headers(getHeader())
                .url(getUrl(cid))
                .build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) {
        Node body = new Node(html);
        String title = body.text("div.comicParticulars-title-right > ul > li > h6");
        String cover = body.attr("div.comicParticulars-left-img > img", "data-src");
        String update = body.text("div.comicParticulars-title-right ul li:contains(最後更新：) span.comicParticulars-right-txt");
        if (StringUtils.isEmpty(update)) {
            update = body.text("div.comicParticulars-title-right ul li:contains(最后更新：) span.comicParticulars-right-txt");
        }
        List<Node> authorList = body.list("div.comicParticulars-title-right ul li:contains(作者：) a");
        StringBuilder author = new StringBuilder();
        for (int i = 0; i < authorList.size(); i++) {
            if (i < authorList.size() - 1) {
                author.append(authorList.get(i).text()).append(",");
            } else {
                author.append(authorList.get(i).text());
            }
        }
        String intro = body.text("p.intro");
        boolean status = isFinish(html);
        comic.setInfo(title, cover, update, intro, author.toString(), status);
        return comic;
    }


    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic)
            throws JSONException {

        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        List<Node> tabGroup = body.list(".table-default-box");
        List<Node> tabGroupName = body.list(".upLoop > span");
        for (int i = 0; i < tabGroup.size(); i++) {
            Node tabNode = tabGroup.get(i);
            Node allTab = tabNode.list(".tab-content > .tab-pane").get(0);
            List<Node> chapterNodes = allTab.list("ul > a");
            chapterNodes = Lists.reverse(chapterNodes);
            for (Node node : chapterNodes) {
                String title = node.attr("title");
                if (title == null || title.isEmpty()) {
                    title = node.text("li").trim();
                }
                String path = node.href();
                if (!title.isEmpty()) {
                    list.add(new Chapter(null, sourceComic, title, path, tabGroupName.get(i).text()));
                }
            }
        }

        for (int j = 0; j < list.size(); j++) {
            long id = IdCreator.createChapterId(sourceComic, j);
            list.get(j).setId(id);
        }
        return list;
    }


    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public String parseCheck(String html) {
        Node body = new Node(html);
        String res = body.text("div.comicParticulars-title-right ul li:contains(最後更新：) span.comicParticulars-right-txt");
        if (StringUtils.isEmpty(res)) {
            res = body.text("div.comicParticulars-title-right ul li:contains(最后更新：) span.comicParticulars-right-txt");
        }
        return res;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = website + path;
        return new Request.Builder()
                .headers(getHeader())
                .url(url)
                .build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws JSONException {
        List<ImageUrl> list = new ArrayList<>();
        Node body = new Node(html);
        List<Node> imageNodes = body.list("ul.comicContent-list > li");
        for (int i = 1; i <= imageNodes.size(); i++) {
            long comicChapter = chapter.getId();
            long id = IdCreator.createImageId(comicChapter, i);
            String imgUrl = imageNodes.get(i - 1).list("img").get(0).attr("data-src");
            imgUrl = imgUrl.replaceAll("c\\d+x\\.[a-zA-Z]+$", "c" + 1500 + "x.webp");
            list.add(new ImageUrl(id, comicChapter, i, imgUrl, false));
        }
        return list;
    }

    @Override
    public Headers getHeader() {
        return Headers.of("user-agent", getUA());
    }

    @Override
    public Request getCategoryRequest(String format, int page) {

        Map<Integer, String> map = getParseFormatMap(format);
        int limit = 50;
        int offset = (page - 1) * limit;
        String url = StringUtils.format(
                "%s/comics?theme=" +
                        map.get(CATEGORY_SUBJECT) +
                        "&status=" +
                        map.get(CATEGORY_PROGRESS) +
                        "&region=" +
                        map.get(CATEGORY_AREA) +
                        "&ordering=" +
                        map.get(CATEGORY_ORDER) +
                        "&offset=" +
                        offset +
                        "&limit=" +
                        limit,
                website);
        return new Request.Builder()
                .headers(getHeader())
                .url(url)
                .build();
    }

    @Override
    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new ArrayList<>();
        Node body = new Node(html);
        Node targetDiv = body.getChild("div.row.exemptComic-box");
        if (targetDiv.get() != null) {
            String listAttr = targetDiv.attr("list");
            String jsonString = listAttr
                    .replace("&#x27;", "\"")
                    .replace("&quot;", "\"");
            try {
                JSONArray array = new JSONArray(jsonString);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject jsonObject = array.getJSONObject(i);
                    String cid = jsonObject.getString("path_word");
                    String title = jsonObject.getString("name");
                    String cover = jsonObject.getString("cover");
                    list.add(new Comic(TYPE, cid, title, cover, null, null));
                }

            } catch (JSONException ignored) {

            }
        }
        return list;
    }

    private static class Category extends MangaCategory {
        @Override
        public boolean isComposite() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getSubject() {
            List<Pair<String, String>> list = new ArrayList<>();

            list.add(Pair.create("全部", ""));
            list.add(Pair.create("愛情", "aiqing"));
            list.add(Pair.create("歡樂向", "huanlexiang"));
            list.add(Pair.create("冒險", "maoxian"));
            list.add(Pair.create("奇幻", "qihuan"));
            list.add(Pair.create("百合", "baihe"));
            list.add(Pair.create("校园", "xiaoyuan"));
            list.add(Pair.create("科幻", "kehuan"));
            list.add(Pair.create("東方", "dongfang"));
            list.add(Pair.create("耽美", "danmei"));
            list.add(Pair.create("生活", "shenghuo"));
            list.add(Pair.create("格鬥", "gedou"));
            list.add(Pair.create("轻小说", "qingxiaoshuo"));
            list.add(Pair.create("悬疑", "xuanyi"));
            list.add(Pair.create("其他", "qita"));
            list.add(Pair.create("神鬼", "shengui"));
            list.add(Pair.create("职场", "zhichang"));
            list.add(Pair.create("TL", "teenslove"));
            list.add(Pair.create("萌系", "mengxi"));
            list.add(Pair.create("治愈", "zhiyu"));
            list.add(Pair.create("長條", "changtiao"));
            list.add(Pair.create("四格", "sige"));
            list.add(Pair.create("节操", "jiecao"));
            list.add(Pair.create("舰娘", "jianniang"));
            list.add(Pair.create("竞技", "jingji"));
            list.add(Pair.create("搞笑", "gaoxiao"));
            list.add(Pair.create("伪娘", "weiniang"));
            list.add(Pair.create("热血", "rexue"));
            list.add(Pair.create("励志", "lizhi"));
            list.add(Pair.create("性转换", "xingzhuanhuan"));
            list.add(Pair.create("彩色", "COLOR"));
            list.add(Pair.create("後宮", "hougong"));
            list.add(Pair.create("美食", "meishi"));
            list.add(Pair.create("侦探", "zhentan"));
            list.add(Pair.create("AA", "aa"));
            list.add(Pair.create("音乐舞蹈", "yinyuewudao"));
            list.add(Pair.create("魔幻", "mohuan"));
            list.add(Pair.create("战争", "zhanzheng"));
            list.add(Pair.create("历史", "lishi"));
            list.add(Pair.create("异世界", "yishijie"));
            list.add(Pair.create("惊悚", "jingsong"));
            list.add(Pair.create("机战", "jizhan"));
            list.add(Pair.create("都市", "dushi"));
            list.add(Pair.create("穿越", "chuanyue"));
            list.add(Pair.create("恐怖", "kongbu"));
            list.add(Pair.create("C100", "comiket100"));
            list.add(Pair.create("重生", "chongsheng"));
            list.add(Pair.create("C99", "comiket99"));
            list.add(Pair.create("C101", "comiket101"));
            list.add(Pair.create("C97", "comiket97"));
            list.add(Pair.create("C96", "comiket96"));
            list.add(Pair.create("生存", "shengcun"));
            list.add(Pair.create("宅系", "zhaixi"));
            list.add(Pair.create("武侠", "wuxia"));
            list.add(Pair.create("C98", "C98"));
            list.add(Pair.create("C95", "comiket95"));
            list.add(Pair.create("FATE", "fate"));
            list.add(Pair.create("转生", "zhuansheng"));
            list.add(Pair.create("無修正", "Uncensored"));
            list.add(Pair.create("仙侠", "xianxia"));
            list.add(Pair.create("LoveLive", "loveLive"));
            return list;
        }

        @Override
        protected boolean hasOrder() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getOrder() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("更新時間（倒序）", "-datetime_updated"));
            list.add(Pair.create("熱度（倒序）", "-popular"));
            list.add(Pair.create("更新時間", "datetime_updated"));
            list.add(Pair.create("熱度", "popular"));
            return list;
        }

        @Override
        protected boolean hasArea() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getArea() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("日漫", "0"));
            list.add(Pair.create("韩漫", "1"));
            list.add(Pair.create("美漫", "2"));
            return list;
        }

        @Override
        protected boolean hasProgress() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getProgress() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("连载中", "0"));
            list.add(Pair.create("已完结", "1"));
            list.add(Pair.create("短篇", "2"));
            return list;
        }
    }
}
