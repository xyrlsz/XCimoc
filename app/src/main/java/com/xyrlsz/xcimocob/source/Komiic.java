package com.xyrlsz.xcimocob.source;


import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_ORDER;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_PROGRESS;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_SUBJECT;
import static com.xyrlsz.xcimocob.parser.MangaCategory.getParseFormatMap;

import android.content.Context;
import android.util.Pair;

import com.google.common.collect.Lists;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.Constants;
import com.xyrlsz.xcimocob.R;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.JsonIterator;
import com.xyrlsz.xcimocob.parser.MangaCategory;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.parser.SearchIterator;
import com.xyrlsz.xcimocob.parser.UrlFilter;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.KomiicUtils;
import com.xyrlsz.xcimocob.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by xyrlsz on 2025/01/21.
 */

public class Komiic extends MangaParser {

    public static final int TYPE = 106;
    public static final String DEFAULT_TITLE = "komiic";

    // 可自动切换的线路列表
    private static final String[] FALLBACK_URLS = {
            "https://komiic.com",
            "https://komiic.cc"
    };

    private String baseUrl;
    private Source mSource;
    private String _cid = "", _path = "";

    public Komiic(Source source) {
        init(source, new Category());
        mSource = source;
        // 优先使用 source 中保存的 baseUrl，其次 SharedPreferences，否则使用第一个线路
        String savedUrl = source != null ? source.getBaseUrl() : null;
        if (!isValidUrl(savedUrl)) {
            savedUrl = App.getAppContext()
                    .getSharedPreferences(Constants.KOMIIC_SHARED, Context.MODE_PRIVATE)
                    .getString(Constants.KOMIIC_SHARED_BASEURL, null);
        }
        baseUrl = isValidUrl(savedUrl) ? savedUrl : FALLBACK_URLS[0];
        KomiicUtils.setBaseUrl(baseUrl);
        // 在后台线程探测可用域名，避免阻塞主线程
        new Thread(this::probeAvailableDomain).start();
        if (KomiicUtils.checkExpired()) {
            KomiicUtils.refresh(App.getAppContext());
        }
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, FALLBACK_URLS[0]);
    }

    /**
     * 快速探测所有线路，切换到第一个能正常响应的域名
     */
    private void probeAvailableDomain() {
        OkHttpClient probeClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .followRedirects(true)
                .retryOnConnectionFailure(false)
                .build();

        // 从当前 baseUrl 开始检查，如果不行再试其他线路
        int startIdx = 0;
        for (int i = 0; i < FALLBACK_URLS.length; i++) {
            if (FALLBACK_URLS[i].equals(baseUrl)) {
                startIdx = i;
                break;
            }
        }

        for (int i = 0; i < FALLBACK_URLS.length; i++) {
            int idx = (startIdx + i) % FALLBACK_URLS.length;
            String testUrl = FALLBACK_URLS[idx];

            Request req = new Request.Builder()
                    .url(testUrl + "/api/query")
                    .post(RequestBody.create(
                            "{\"operationName\":null,\"variables\":{},\"query\":\"query{__typename}\"}",
                            MediaType.parse("application/json; charset=utf-8")))
                    .build();

            try {
                Response resp = probeClient.newCall(req).execute();
                boolean ok = resp.isSuccessful();
                resp.close();
                if (ok) {
                    if (!testUrl.equals(baseUrl)) {
                        baseUrl = testUrl;
                        KomiicUtils.setBaseUrl(baseUrl);
                        // 持久化
                        if (mSource != null) {
                            mSource.setBaseUrl(baseUrl);
                            SourceManager.getInstance(App.getApp()).update(mSource);
                        }
                    }
                    return;
                }
            } catch (Exception ignored) {
                // 当前线路不可用，继续尝试下一个
            }
        }
    }

    /**
     * 尝试切换到下一个可用线路，并持久化到 Source
     */
    private boolean switchToNextDomain() {
        for (int i = 0; i < FALLBACK_URLS.length; i++) {
            if (FALLBACK_URLS[i].equals(baseUrl)) {
                int nextIndex = (i + 1) % FALLBACK_URLS.length;
                baseUrl = FALLBACK_URLS[nextIndex];
                KomiicUtils.setBaseUrl(baseUrl);
                // 持久化到 Source
                if (mSource != null) {
                    mSource.setBaseUrl(baseUrl);
                    SourceManager.getInstance(App.getApp()).update(mSource);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isValidUrl(String url) {
        return url != null && !url.isEmpty() &&
                (url.startsWith("https://komiic.com") || url.startsWith("https://komiic.cc"));
    }

    /** 获取当前使用的域名（供外部读取当前线路） */
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public Request getSearchRequest(String keyword, int page) {
        if (page == 1) {
            String url = StringUtils.format("%s/api/query", baseUrl);
            String jsonBody = "{"
                    + "\"operationName\":\"searchComicAndAuthorQuery\","
                    + "\"variables\":{\"keyword\":\""
                    + keyword
                    + "\"},"
                    + "\"query\":\"query searchComicAndAuthorQuery($keyword: String!) {\\n  searchComicsAndAuthors(keyword: $keyword) {\\n    comics {\\n      id\\n      title\\n      status\\n      year\\n      imageUrl\\n      authors {\\n        id\\n        name\\n        __typename\\n      }\\n      categories {\\n        id\\n        name\\n        __typename\\n      }\\n      dateUpdated\\n      monthViews\\n      views\\n      favoriteCount\\n      lastBookUpdate\\n      lastChapterUpdate\\n      __typename\\n    }\\n    authors {\\n      id\\n      name\\n      chName\\n      enName\\n      wikiLink\\n      comicCount\\n      views\\n      __typename\\n    }\\n    __typename\\n  }\\n}\""
                    + "}";
            RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));

            return new Request.Builder().url(url).post(requestBody).build();
        }
        return null;
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        try {
            JSONObject data = new JSONObject(html).getJSONObject("data");
            JSONArray comics = data.getJSONObject("searchComicsAndAuthors")
                    .getJSONArray("comics");
            return new JsonIterator(comics) {
                @Override
                protected Comic parse(JSONObject object) throws JSONException {
                    String cid = object.getString("id");
                    String title = object.getString("title");
                    String cover = object.getString("imageUrl");
//                    String status = object.getString("status");

                    String update = KomiicUtils.FormatTime(object.getString("dateUpdated"));
                    String author = "";
                    JSONArray authors = object.getJSONArray("authors");
                    for (int i = 0; i < authors.length(); i++) {
                        if (i != authors.length() - 1) {
                            author += authors.getJSONObject(i).getString("name") + ",";
                        } else {
                            author += authors.getJSONObject(i).getString("name");
                        }
                    }
                    return new Comic(TYPE, cid, title, cover, update, author);
                }
            };

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getUrl(String cid) {
        return baseUrl + "/comic/" + cid;
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("komiic.com"));
        filter.add(new UrlFilter("komiic.cc"));
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = StringUtils.format("%s/api/query", baseUrl);
        String jsonBody = "{"
                + "\"operationName\":\"comicById\","
                + "\"variables\":{\"comicId\":\""
                + cid + "\"},"
                + "\"query\":\"query comicById($comicId: ID!) {\\n  comicById(comicId: $comicId) {\\n    description\\n    id\\n    title\\n    status\\n    year\\n    imageUrl\\n    authors {\\n      id\\n      name\\n      __typename\\n    }\\n    categories {\\n      id\\n      name\\n      __typename\\n    }\\n    dateCreated\\n    dateUpdated\\n    views\\n    favoriteCount\\n    lastBookUpdate\\n    lastChapterUpdate\\n    __typename\\n  }\\n}\""
                + "}";
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));
        return new Request.Builder().url(url).post(requestBody).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws JSONException {

        JSONObject data = new JSONObject(html).getJSONObject("data");
        JSONObject comicObject = data.getJSONObject("comicById");
        String title = comicObject.getString("title");
        String cover = comicObject.getString("imageUrl");
        StringBuilder author = new StringBuilder();
        JSONArray authors = comicObject.getJSONArray("authors");
        for (int i = 0; i < authors.length(); i++) {
            if (i != authors.length() - 1) {
                author.append(authors.getJSONObject(i).getString("name")).append(",");
            } else {
                author.append(authors.getJSONObject(i).getString("name"));
            }
        }

        String update = KomiicUtils.FormatTime(comicObject.getString("dateUpdated"));
        String intro;
        intro = comicObject.getString("description");

        comic.setInfo(title, cover, update, intro, author.toString(), !comicObject.getString("status").equals("ONGOING"));
        return comic;
    }

    @Override
    public Request getChapterRequest(String html, String cid) {
        String jsonBody = "{"
                + "\"operationName\":\"chapterByComicId\","
                + "\"variables\":{\"comicId\":\"" + cid + "\"},"
                + "\"query\":\"query chapterByComicId($comicId: ID!) {\\n  chaptersByComicId(comicId: $comicId) {\\n    id\\n    serial\\n    type\\n    dateCreated\\n    dateUpdated\\n    size\\n    __typename\\n  }\\n}\""
                + "}";
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));
        return new Request.Builder().url(baseUrl + "/api/query").post(requestBody).build();
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        List<Chapter> list = new LinkedList<>();

        JSONObject data = new JSONObject(html).getJSONObject("data");
        if (!data.has("chaptersByComicId")) {
            return list;
        }
        JSONArray chapters = data.getJSONArray("chaptersByComicId");

        List<JSONObject> jsonList = new ArrayList<>();
        for (int i = 0; i < chapters.length(); i++) {
            jsonList.add(chapters.getJSONObject(i));
        }
        Collections.sort(jsonList, (o1, o2) -> {
            try {
                String type1 = o1.getString("type");
                String type2 = o2.getString("type");
                return CharSequence.compare(type1, type2);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return 0;
        });
        for (int i = 0; i < jsonList.size(); i++) {

            String title = jsonList.get(i).getString("serial");
            String path = jsonList.get(i).getString("id");
            String type = jsonList.get(i).getString("type");
            switch (type) {
                case "chapter":
                    type = "话";
                    break;
                case "book":
                    type = "卷";
                    break;
                default:
                    break;
            }
            list.add(new Chapter(null, sourceComic, title, path, type));
        }
        list = Lists.reverse(list);
        for (int j = 0; j < list.size(); j++) {
            Long id = IdCreator.createChapterId(sourceComic, j);
            list.get(j).setId(id);
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String jsonBody = "{"
                + "\"operationName\":\"imagesByChapterId\","
                + "\"variables\":{\"chapterId\":\"" + path + "\"},"
                + "\"query\":\"query imagesByChapterId($chapterId: ID!) {\\n  imagesByChapterId(chapterId: $chapterId) {\\n    id\\n    kid\\n    height\\n    width\\n    __typename\\n  }\\n}\""
                + "}";
        _cid = cid;
        _path = path;
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));
        return new Request.Builder().url(baseUrl + "/api/query").post(requestBody).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws JSONException {

        List<ImageUrl> list = new ArrayList<>();
        String imgBaseUrl = baseUrl + "/api/image/";
        JSONObject data = new JSONObject(html).getJSONObject("data");
        JSONArray images = data.getJSONArray("imagesByChapterId");
        String _cookies = App.getAppContext()
                .getSharedPreferences(Constants.KOMIIC_SHARED, Context.MODE_PRIVATE)
                .getString(Constants.KOMIIC_SHARED_COOKIES, "");
        if (KomiicUtils.checkExpired()) {
            KomiicUtils.refresh(App.getAppContext());
            _cookies = "";
        }
        if (KomiicUtils.checkIsOverImgLimit()) {
            _cookies = "";
        }
        if (KomiicUtils.checkEmptyAccountIsOverImgLimit() && _cookies.isEmpty()) {
            HintUtils.showToast(App.getAppContext(), R.string.limit_over_tip);
        }
        for (int i = 1; i <= images.length(); i++) {
            Long comicChapter = chapter.getId();
            Long id = IdCreator.createImageId(comicChapter, i);
            String imgUrl = imgBaseUrl + images.getJSONObject(i - 1).getString("kid");
            Headers headers = Headers.of("referer", StringUtils.format("%s/comic/%s/chapter/%s", baseUrl, _cid, chapter.getPath()), "cookie", _cookies);
            list.add(new ImageUrl(id, comicChapter, i, imgUrl, false, headers));
        }
        return list;
    }

    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public Headers getHeader() {
        return Headers.of("referer", StringUtils.format("%s/comic/%s/chapter/%s", baseUrl, _cid, _path));
    }


    @Override
    public Request getCategoryRequest(String format, int page) {
        Map<Integer, String> map = getParseFormatMap(format);
        int limit = 30;
        int offset = (page - 1) * limit;
        String sub = Objects.requireNonNull(map.get(CATEGORY_SUBJECT)).isEmpty() ? "" : "\"" + Objects.requireNonNull(map.get(CATEGORY_SUBJECT)) + "\"";
        String json = "{\"operationName\":\"comicByCategories\",\"variables\":{\"categoryId\":" +
                "[" + sub + "]" +
                ",\"pagination\":{\"limit\":" +
                limit +
                ",\"offset\":" +
                offset +
                ",\"orderBy\":\"" +
                map.get(CATEGORY_ORDER) +
                "\",\"asc\":false,\"status\":\"" +
                map.get(CATEGORY_PROGRESS) +
                "\"}},\"query\":\"query comicByCategories($categoryId: [ID!]!, $pagination: Pagination!) {\\n  comicByCategories(categoryId: $categoryId, pagination: $pagination) {\\n    id\\n    title\\n    status\\n    year\\n    imageUrl\\n    authors {\\n      id\\n      name\\n      __typename\\n    }\\n    categories {\\n      id\\n      name\\n      __typename\\n    }\\n    dateUpdated\\n    monthViews\\n    views\\n    favoriteCount\\n    lastBookUpdate\\n    lastChapterUpdate\\n    __typename\\n  }\\n}\"}";
        String url = StringUtils.format("%s/api/query", baseUrl);
        RequestBody requestBody = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        return new Request.Builder().url(url).post(requestBody).build();

    }

    @Override
    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new ArrayList<>();
        JSONObject data;
        try {
            data = new JSONObject(html).getJSONObject("data");
            JSONArray comics = data.getJSONArray("comicByCategories");
            for (int i = 0; i < comics.length(); i++) {
                JSONObject object = comics.getJSONObject(i);
                String cid = object.getString("id");
                String title = object.getString("title");
                String cover = object.getString("imageUrl");
                list.add(new Comic(TYPE, cid, title, cover, null, null));
            }

        } catch (JSONException e) {
            return list;
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
            list.add(Pair.create("愛情", "1"));
            list.add(Pair.create("神鬼", "3"));
            list.add(Pair.create("校園", "4"));
            list.add(Pair.create("搞笑", "5"));
            list.add(Pair.create("生活", "6"));
            list.add(Pair.create("懸疑", "7"));
            list.add(Pair.create("冒險", "8"));
            list.add(Pair.create("恐怖", "9"));
            list.add(Pair.create("職場", "10"));
            list.add(Pair.create("魔幻", "11"));
            list.add(Pair.create("後宮", "2"));
            list.add(Pair.create("魔法", "12"));
            list.add(Pair.create("格鬥", "13"));
            list.add(Pair.create("宅男", "14"));
            list.add(Pair.create("勵志", "15"));
            list.add(Pair.create("耽美", "16"));
            list.add(Pair.create("科幻", "17"));
            list.add(Pair.create("百合", "18"));
            list.add(Pair.create("治癒", "19"));
            list.add(Pair.create("萌系", "20"));
            list.add(Pair.create("熱血", "21"));
            list.add(Pair.create("競技", "22"));
            list.add(Pair.create("推理", "23"));
            list.add(Pair.create("雜誌", "24"));
            list.add(Pair.create("偵探", "25"));
            list.add(Pair.create("偽娘", "26"));
            list.add(Pair.create("美食", "27"));
            list.add(Pair.create("四格", "28"));
            list.add(Pair.create("社會", "31"));
            list.add(Pair.create("歷史", "32"));
            list.add(Pair.create("戰爭", "33"));
            list.add(Pair.create("舞蹈", "34"));
            list.add(Pair.create("武俠", "35"));
            list.add(Pair.create("機戰", "36"));
            list.add(Pair.create("音樂", "37"));
            list.add(Pair.create("體育", "40"));
            list.add(Pair.create("黑道", "42"));
            list.add(Pair.create("腐女", "46"));
            list.add(Pair.create("異世界", "47"));
            list.add(Pair.create("驚悚", "48"));
            list.add(Pair.create("成人", "51"));
            list.add(Pair.create("戰鬥", "54"));
            list.add(Pair.create("復仇", "55"));
            list.add(Pair.create("轉生", "56"));
            list.add(Pair.create("黑暗奇幻", "57"));
            list.add(Pair.create("戲劇", "58"));
            list.add(Pair.create("生存", "59"));
            list.add(Pair.create("策略", "60"));
            list.add(Pair.create("政治", "61"));
            list.add(Pair.create("黑暗", "62"));
            list.add(Pair.create("動作", "64"));
            list.add(Pair.create("性轉換", "70"));
            list.add(Pair.create("日常", "78"));
            list.add(Pair.create("青春", "81"));
            list.add(Pair.create("醫療", "85"));
            list.add(Pair.create("致鬱", "86"));
            list.add(Pair.create("心理", "87"));
            list.add(Pair.create("穿越", "88"));
            list.add(Pair.create("友情", "92"));
            list.add(Pair.create("犯罪", "93"));
            list.add(Pair.create("劇情", "97"));
            list.add(Pair.create("少女", "113"));
            list.add(Pair.create("賭博", "114"));
            list.add(Pair.create("女性向", "123"));
            list.add(Pair.create("溫馨", "129"));
            list.add(Pair.create("同人", "164"));
            list.add(Pair.create("幻想", "183"));
            list.add(Pair.create("成長", "184"));
            list.add(Pair.create("心裡", "185"));
            list.add(Pair.create("溫暖", "186"));
            list.add(Pair.create("戀愛", "187"));
            list.add(Pair.create("奇幻", "189"));
            list.add(Pair.create("驚愕", "204"));
            list.add(Pair.create("懷疑", "214"));
            list.add(Pair.create("驚訝", "219"));
            list.add(Pair.create("同性", "222"));
            list.add(Pair.create("驚奇", "223"));
            list.add(Pair.create("博彩", "227"));
            list.add(Pair.create("末世", "232"));
            return list;
        }


        @Override
        public boolean hasProgress() {
            return true;
        }

        @Override
        public List<Pair<String, String>> getProgress() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", ""));
            list.add(Pair.create("連載", "ONGOING"));
            list.add(Pair.create("完結", "END"));
            return list;
        }

        @Override
        protected boolean hasOrder() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getOrder() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("更新", "DATE_UPDATED"));
            list.add(Pair.create("觀看數", "VIEWS"));
            list.add(Pair.create("喜愛數", "FAVORITE_COUNT"));
            return list;
        }

    }
}
