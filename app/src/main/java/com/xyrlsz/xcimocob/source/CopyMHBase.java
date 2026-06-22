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
import com.xyrlsz.xcimocob.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

public abstract class CopyMHBase extends MangaParser {

    public static final String website = "https://www.copy3000.com";

    protected final int type;
    protected final SharedPreferences sharedPreferences;
    protected String searchApi;

    public CopyMHBase(Source source, MangaCategory category, int type,
                      boolean useWebParserForInfo, boolean useWebParserForImages) {
        init(source, category);
        this.type = type;
        if (useWebParserForInfo) {
            setParseInfoUseWebParser(true);
        }
        if (useWebParserForImages) {
            setParseImagesUseWebParser(true);
        }
        sharedPreferences = App.getAppContext()
                .getSharedPreferences(Constants.COPYMG_SHARED, Context.MODE_PRIVATE);
        searchApi = sharedPreferences.getString(
                Constants.COPYMG_SHARED_SEARCH_API, "/api/kb/web/searchci/comics");
        refreshSearchApi();
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
                // ignore
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String text = response.body().string();
                Pattern pattern = Pattern.compile("const countApi = \"([^\"]+)\"");
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    searchApi = matcher.group(1);
                    sharedPreferences.edit()
                            .putString(Constants.COPYMG_SHARED_SEARCH_API, searchApi)
                            .apply();
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
    public Request getSearchRequest(String keyword, int page) {
        if (page == 1) {
            String url = website + searchApi
                    + "?offset=0&platform=2&limit=12&q=" + keyword + "&q_type=";
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
                StringBuilder author = new StringBuilder();
                for (int i = 0; i < object.getJSONArray("author").length(); ++i) {
                    author.append(object.getJSONArray("author")
                            .getJSONObject(i).getString("name").trim());
                    if (i < object.getJSONArray("author").length() - 1) {
                        author.append(",");
                    }
                }
                return new Comic(type, cid, title, cover, null, author.toString());
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
        String update = body.text(
                "div.comicParticulars-title-right ul li:contains(最後更新：) span.comicParticulars-right-txt");
        if (StringUtils.isEmpty(update)) {
            update = body.text(
                    "div.comicParticulars-title-right ul li:contains(最后更新：) span.comicParticulars-right-txt");
        }
        List<Node> authorList = body.list(
                "div.comicParticulars-title-right ul li:contains(作者：) a");
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
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public String parseCheck(String html) {
        Node body = new Node(html);
        String res = body.text(
                "div.comicParticulars-title-right ul li:contains(最後更新：) span.comicParticulars-right-txt");
        if (StringUtils.isEmpty(res)) {
            res = body.text(
                    "div.comicParticulars-title-right ul li:contains(最后更新：) span.comicParticulars-right-txt");
        }
        return res;
    }

    @Override
    public Request getCategoryRequest(String format, int page) {
        Map<Integer, String> map = getParseFormatMap(format);
        int limit = 50;
        int offset = (page - 1) * limit;
        String url = StringUtils.format(
                "%s/comics?theme=" + map.get(CATEGORY_SUBJECT)
                        + "&status=" + map.get(CATEGORY_PROGRESS)
                        + "&region=" + map.get(CATEGORY_AREA)
                        + "&ordering=" + map.get(CATEGORY_ORDER)
                        + "&offset=" + offset
                        + "&limit=" + limit,
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
            String listAttr = targetDiv.attr("list")
                    .replace("&#x27;", "\"")
                    .replace("&quot;", "\"");
            try {
                JSONArray array = new JSONArray(listAttr);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject jsonObject = array.getJSONObject(i);
                    String cid = jsonObject.getString("path_word");
                    String title = jsonObject.getString("name");
                    String cover = jsonObject.getString("cover");
                    list.add(new Comic(type, cid, title, cover, null, null));
                }
            } catch (JSONException ignored) {
                // ignore
            }
        }
        return list;
    }

    // ==================== Abstract methods ====================

    @Override
    public abstract Headers getHeader();

    @Override
    public abstract List<Chapter> parseChapter(String html, Comic comic, Long sourceComic)
            throws JSONException;

    @Override
    public abstract Request getImagesRequest(String cid, String path);

    @Override
    public abstract List<ImageUrl> parseImages(String html, Chapter chapter)
            throws JSONException;

    // ==================== Shared inner Category class ====================

    protected static class Category extends MangaCategory {
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
