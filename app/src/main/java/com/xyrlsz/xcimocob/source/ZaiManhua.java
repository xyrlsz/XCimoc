package com.xyrlsz.xcimocob.source;

import static android.content.Context.MODE_PRIVATE;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_AREA;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_ORDER;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_PROGRESS;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_READER;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_SUBJECT;
import static com.xyrlsz.xcimocob.parser.MangaCategory.getParseFormatMap;

import android.content.SharedPreferences;
import android.util.Pair;

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
import com.xyrlsz.xcimocob.parser.UrlFilterWithCidQueryKey;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.StringUtils;
import com.xyrlsz.xcimocob.utils.TimestampUtils;
import com.xyrlsz.xcimocob.utils.ZaiManhuaSignUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.Request;

public class ZaiManhua extends MangaParser {

    public static final int TYPE = 12;
    public static final String DEFAULT_TITLE = "再漫画";
    private static final String baseUrl = "https://m.zaimanhua.com";
    private static final String pcBaseUrl = "https://manhua.zaimanhua.com";
    private static final String apiBaseUrl = "https://v4api.zaimanhua.com";
    private final SharedPreferences sharedPreferences;
    //    private List<UrlFilter> filter = new ArrayList<>();
    String TOKEN = "";
    String UID;
    long EXP = 0;
    String username;
    String passwdMd5;

    public ZaiManhua(Source source) {
        init(source, new Category());
        sharedPreferences = App.getAppContext().getSharedPreferences(Constants.ZAI_SHARED, MODE_PRIVATE);
        UID = sharedPreferences.getString(Constants.ZAI_SHARED_UID, "0");
        username = sharedPreferences.getString(Constants.ZAI_SHARED_USERNAME, "");
        passwdMd5 = sharedPreferences.getString(Constants.ZAI_SHARED_PASSWD_MD5, "");
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, baseUrl);
    }


    private long getEXP() {
        EXP = sharedPreferences.getLong(Constants.ZAI_SHARED_EXP, 0);
        return EXP;
    }

    private String getTOKEN() {
        TOKEN = sharedPreferences.getString(Constants.ZAI_SHARED_TOKEN, "");
        return TOKEN;
    }


    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("zaimanhua.com"));
        filter.add(new UrlFilterWithCidQueryKey("m.zaimanhua.com", "id"));
    }

    @Override
    public Request getSearchRequest(String keyword, int page) {
        if (page == 1) {
            String url = StringUtils.format("%s/app/v1/search/index?keyword=%s&source=0&page=1&size=24&platform=android&_v=2.2.4&_c=101_01_01_000", apiBaseUrl, keyword);
            return new Request.Builder().url(url).build();
        }
        return null;
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        try {
            JSONObject jsonObject = new JSONObject(html);
            JSONObject data = jsonObject.getJSONObject("data");
            JSONArray list = data.getJSONArray("list");
            return new JsonIterator(list) {
                @Override
                protected Comic parse(JSONObject object) {
                    try {
//                        String cid = object.getString("comic_py");
                        String cid = object.getString("id");
                        String title = object.getString("title");
                        String cover = object.getString("cover");
                        String author = object.optString("authors");
 
                        return new Comic(TYPE, cid, title, cover, null, author);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getUrl(String cid) {
        return StringUtils.format("https://m.zaimanhua.com/pages/comic/detail?id=%s", cid);
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = StringUtils.format("%s/app/v1/comic/detail/%s?_v=2.2.4&platform=android&_v=2.2.4&_c=101_01_01_000", apiBaseUrl, cid);
        return new Request.Builder().url(url).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) {
        if (getTOKEN().isEmpty()) {
            HintUtils.showToast(App.getAppContext(), "再漫画未登录可能导致漫画无法阅读");
        }
        long timestamp = System.currentTimeMillis() / 1000;
        if (timestamp > getEXP() && !getTOKEN().isEmpty() && !passwdMd5.isEmpty()) {
            ZaiManhuaSignUtils.LoginWithPasswdMd5(App.getAppContext(), new ZaiManhuaSignUtils.LoginCallback() {
                @Override
                public void onSuccess() {
                    HintUtils.showToast(App.getAppContext(), "再漫画自动登录成功");
                }

                @Override
                public void onFail() {
                    HintUtils.showToast(App.getAppContext(), "再漫画自动登录失败");
                }
            }, username, passwdMd5);

        }

        try {
            JSONObject jsonObject = new JSONObject(html);
            JSONObject data = jsonObject.getJSONObject("data").getJSONObject("data");
            String intro = data.getString("description");
            String title = data.getString("title");
            String cover = data.getString("cover");
            StringBuilder author = new StringBuilder();
            JSONArray authors = data.getJSONArray("authors");
            for (int i = 0; i < authors.length(); i++) {
                JSONObject obj = authors.getJSONObject(i);
                author.append(obj.getString("tag_name"));
                if (i < authors.length() - 1) {
                    author.append(",");
                }
            }
            String update = TimestampUtils.formatTimestampSeconds(data.getLong("last_updatetime"));
            boolean status = isFinish(html);
            comic.setInfo(title, cover, update, intro, author.toString(), status);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) {
        List<Chapter> list = new LinkedList<>();

        try {
            JSONObject jsonObject = new JSONObject(html);
            JSONObject data = jsonObject.getJSONObject("data").getJSONObject("data");
            JSONArray allJsonArray = data.getJSONArray("chapters");
            int k = 1;
            for (int i = 0; i < allJsonArray.length(); i++) {
                JSONArray JSONArray = allJsonArray.getJSONObject(i).getJSONArray("data");
                String tag = allJsonArray.getJSONObject(i).getString("title");
                for (int j = 0; j < JSONArray.length(); ++j) {
                    JSONObject chapter = JSONArray.getJSONObject(j);
                    String title = chapter.getString("chapter_title");
                    String chapter_id = chapter.getString("chapter_id");
                    Long id = IdCreator.createChapterId(sourceComic, k++);
                    list.add(new Chapter(id, sourceComic, title, chapter_id, tag));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("%s/app/v1/comic/chapter/%s/%s?platform=android&_v=2.2.4&_c=101_01_01_000", pcBaseUrl, cid, path);
        return new Request.Builder().url(url)
                .addHeader("User-Agent", "Dart/3.6 (dart:io)")
                .addHeader("platform", "android")
                .addHeader("authorization", "Bearer " + getTOKEN())
                .build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) {
        List<ImageUrl> list = new LinkedList<>();
        try {
            JSONObject jsonObject;
            JSONArray array;
            jsonObject = new JSONObject(html);
            array = jsonObject
                    .getJSONObject("data")
                    .getJSONObject("data")
                    .getJSONArray("page_url");
            for (int i = 0; i != array.length(); ++i) {
                Long comicChapter = chapter.getId();
                Long id = IdCreator.createChapterId(comicChapter, i);
                String url = array.getString(i);
                list.add(new ImageUrl(id, comicChapter, i + 1, url, false));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Headers getHeader() {
        return Headers.of("Referer", "https://manhua.zaimanhua.com/", "user-agent", "Dalvik/2.1.0 (Linux; U; Android 12; SM-N9700 Build/SP1A.210812.016);");
    }

    @Override
    public Request getCategoryRequest(String format, int page) {

        Map<Integer, String> map = getParseFormatMap(format);
        int limit = 20;
        String url = StringUtils.format("%s/app/v1/comic/filter/list?page=" +
                        page +
                        "&sortType=" +
                        map.get(CATEGORY_ORDER) +
                        "&theme=" +
                        map.get(CATEGORY_SUBJECT) +
                        "&cate=" +
                        map.get(CATEGORY_READER) +
                        "&status=" +
                        map.get(CATEGORY_PROGRESS) +
                        "&zone=" +
                        map.get(CATEGORY_AREA) +
//                        "&platform=android&timestamp=" +
//                        System.currentTimeMillis() / 1000 +
//                        "&_v=2.3.1&_c=101_01_01_000" +
                        "&size=" +
                        limit
                , apiBaseUrl);

        return new Request.Builder().url(url)
                .addHeader("User-Agent", "Dart/3.6 (dart:io)")
                .addHeader("platform", "android")
                .addHeader("authorization", "Bearer " + getTOKEN())
                .build();
    }

    @Override
    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new ArrayList<>();
        JSONObject data;
        try {
            data = new JSONObject(html).getJSONObject("data");
            JSONArray comics = data.getJSONArray("comicList");
            for (int i = 0; i < comics.length(); i++) {
                JSONObject object = comics.getJSONObject(i);
                String cid = object.getString("id");
                String title = object.getString("name");
                String cover = object.getString("cover");
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
            list.add(new Pair<>("全部", "0"));
            list.add(new Pair<>("冒险", "4"));
            list.add(new Pair<>("欢乐向", "5"));
            list.add(new Pair<>("格斗", "6"));
            list.add(new Pair<>("科幻", "7"));
            list.add(new Pair<>("爱情", "8"));
            list.add(new Pair<>("侦探", "9"));
            list.add(new Pair<>("竞技", "10"));
            list.add(new Pair<>("魔法", "11"));
            list.add(new Pair<>("神鬼", "12"));
            list.add(new Pair<>("校园", "13"));
            list.add(new Pair<>("惊悚", "14"));
            list.add(new Pair<>("其他", "16"));
            list.add(new Pair<>("四格", "17"));
            list.add(new Pair<>("亲情", "3242"));
            list.add(new Pair<>("ゆり", "3243"));
            list.add(new Pair<>("秀吉", "3244"));
            list.add(new Pair<>("悬疑", "3245"));
            list.add(new Pair<>("纯爱", "3246"));
            list.add(new Pair<>("热血", "3248"));
            list.add(new Pair<>("泛爱", "3249"));
            list.add(new Pair<>("历史", "3250"));
            list.add(new Pair<>("战争", "3251"));
            list.add(new Pair<>("萌系", "3252"));
            list.add(new Pair<>("宅系", "3253"));
            list.add(new Pair<>("治愈", "3254"));
            list.add(new Pair<>("励志", "3255"));
            list.add(new Pair<>("武侠", "3324"));
            list.add(new Pair<>("机战", "3325"));
            list.add(new Pair<>("音乐舞蹈", "3326"));
            list.add(new Pair<>("美食", "3327"));
            list.add(new Pair<>("职场", "3328"));
            list.add(new Pair<>("西方魔幻", "3365"));
            list.add(new Pair<>("高清单行", "4459"));
            list.add(new Pair<>("TS", "4518"));
            list.add(new Pair<>("东方", "5077"));
            list.add(new Pair<>("魔幻", "5806"));
            list.add(new Pair<>("奇幻", "5848"));
            list.add(new Pair<>("节操", "6219"));
            list.add(new Pair<>("轻小说", "6316"));
            list.add(new Pair<>("颜艺", "6437"));
            list.add(new Pair<>("搞笑", "7568"));
            list.add(new Pair<>("仙侠", "7900"));  // 修正：从23388改为7900
            list.add(new Pair<>("舰娘", "13627")); // 修正：从7900改为13627
            list.add(new Pair<>("动画", "17192")); // 修正：从13627改为17192
            list.add(new Pair<>("AA", "18522"));  // 修正：从17192改为18522
            list.add(new Pair<>("福瑞", "23323"));
            list.add(new Pair<>("生存", "23388")); // 修正：从23323改为23388
            list.add(new Pair<>("日常", "30788")); // 修正：从23388改为30788
            list.add(new Pair<>("画集", "31137")); // 修正：从30788改为31137
            list.add(new Pair<>("2025冬", "34093")); // 修正：从31137改为34093
            return list;
        }

        @Override
        protected boolean hasOrder() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getOrder() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("更新", "1"));
            list.add(Pair.create("人气", "2"));
            return list;
        }

        @Override
        protected boolean hasArea() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getArea() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", "0"));
            list.add(Pair.create("日本", "2304"));
            list.add(Pair.create("韩国", "2305"));
            list.add(Pair.create("欧美", "2306"));
            list.add(Pair.create("港台", "2307"));
            list.add(Pair.create("内地", "2308"));
            list.add(Pair.create("其他", "8435"));
            return list;
        }

        @Override
        protected boolean hasProgress() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getProgress() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", "0"));
            list.add(Pair.create("连载中", "2309"));
            list.add(Pair.create("已完结", "2310")); // 修正：从2309改为2310
            list.add(Pair.create("短篇", "29205"));
            return list;
        }

        @Override
        protected boolean hasReader() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getReader() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(Pair.create("全部", "0"));
            list.add(Pair.create("少年漫画", "3262"));
            list.add(Pair.create("少女漫画", "3263"));
            list.add(Pair.create("青年漫画", "3264"));
            list.add(Pair.create("女青漫画", "13626")); // 修正：从29205改为13626
            return list;
        }
    }
}
