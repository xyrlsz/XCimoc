package com.xyrlsz.xcimocob.source;

import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_AREA;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_ORDER;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_PROGRESS;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_READER;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_SUBJECT;
import static com.xyrlsz.xcimocob.parser.Category.CATEGORY_YEAR;
import static com.xyrlsz.xcimocob.parser.MangaCategory.getParseFormatMap;

import android.util.Pair;

import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.MangaCategory;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.parser.NodeIterator;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.Request;

/**
 * Created by xyrlsz on 2025/01/09.
 */

public class MYCOMIC extends MangaParser {


    public static final int TYPE = 103;
    public static final String DEFAULT_TITLE = "MYCOMIC";
    private static final String baseUrl = "https://mycomic.com";

    public MYCOMIC(Source source) {
        init(source, new Category());
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, baseUrl);
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException {
        if (page != 1) {
            return null;
        }
        String url = baseUrl + "/comics?q=" + keyword + "&page=" + page;
        return new Request.Builder()
                .url(url)
                .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36 Edg/136.0.0.0")
                .build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        Node body = new Node(html);
        return new NodeIterator(body.list(".grid > .group")) {
            @Override
            protected Comic parse(Node node) {
                String title = node.text("[data-flux-subheading]");
//                String author = node.text(".comics-card__info > small");
                String[] tmp = node.href("div > a").split("/");
                String cid = tmp[tmp.length - 1];
                String cover = node.attr("div > a > img", "data-src");
                if (cover.isEmpty()) {
                    cover = node.src("div > a > img");
                }
                return new Comic(TYPE, cid, title, cover, "", "");
            }
        };
    }

    @Override
    public String getUrl(String cid) {
        return baseUrl + "/comics/" + cid;
    }

    @Override
    protected void initUrlFilterList() {
        filter.add(new UrlFilter("mycomic.com"));
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = baseUrl + "/comics/" + cid;
        return new Request.Builder().url(url).header("Referer", baseUrl.concat("/")).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException {
        Node body = new Node(html).getChild("[data-flux-card]");
        String title = body.text("[data-flux-heading]");
        String cover = body.src("div > img");
        String author = body.text(".grow > div > div > span");
        String intro = body.text(".grow > div:nth-child(5)");

        boolean status = isFinish(body.text("[data-flux-badge]"));

        String update = body.attr("time", "datetime");
        comic.setInfo(title, cover, update, intro, author, status);
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) {
        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        List<Node> chapterNodes = body.list(".grow > [x-cloak] > [x-data]");
        List<Node> chapterTypes = body.list(".grow > [x-cloak] > [x-data] > [data-flux-subheading] > div");
        int i = 0;

        for (int k = 0; k < chapterTypes.size(); k++) {
            String type = chapterTypes.get(k).text();
            Node chapterNode = chapterNodes.get(k);
            String chaptersJson = StringUtils.match("chapters:\\s*(\\[.*?\\])", chapterNode.attr("x-data"), 1);
            try {
                JSONArray chaptersData = new JSONArray(chaptersJson);

                for (int j = 0; j < chaptersData.length(); j++) {
                    JSONObject chapter = chaptersData.getJSONObject(j);
                    String title = chapter.getString("title");
                    String path = chapter.getString("id");
                    Long id = IdCreator.createChapterId(sourceComic, i++);
                    list.add(new Chapter(id, sourceComic, title, path, type));
                }
            } catch (JSONException e) {
//                throw new RuntimeException(e);
            }

        }

        return list;
    }


    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("%s/chapters/%s", baseUrl, path);
        return new Request.Builder().url(url).header("Referer", baseUrl.concat("/")).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) {
        List<ImageUrl> list = new ArrayList<>();
        Node body = new Node(html);
        List<Node> imageNodes = body.list("div > div > div > img[x-ref^=page-]");
        for (int i = 1; i <= imageNodes.size(); i++) {
            Long comicChapter = chapter.getId();
            Long id = IdCreator.createImageId(comicChapter, i);
            String imgUrl = imageNodes.get(i - 1).src();
            if (imgUrl.isEmpty()) {
                imgUrl = imageNodes.get(i - 1).attr("data-src");
            }
            list.add(new ImageUrl(id, comicChapter, i, imgUrl, false, getHeader()));
        }

        return list;
    }

    @Override
    public Headers getHeader() {
        Map<String, String> heads = new HashMap<>();
        heads.put("referer", baseUrl.concat("/"));
        return Headers.of(heads);
    }

    @Override
    public Request getCategoryRequest(String format, int page) {
        Map<Integer, String> map = getParseFormatMap(format);
        String url =
                baseUrl + "/comics?filter%5Baudience%5D=" +
                        map.get(CATEGORY_READER) +
                        "&filter%5Bcountry%5D=" +
                        map.get(CATEGORY_AREA) +
                        "&filter%5Btag%5D=" +
                        map.get(CATEGORY_SUBJECT) +
                        "&filter%5Byear%5D=" +
                        map.get(CATEGORY_YEAR) +
                        "&filter%5Bend%5D=" +
                        map.get(CATEGORY_PROGRESS) +
                        "&sort=" +
                        map.get(CATEGORY_ORDER) +
                        "&page=" +
                        page;

        return new Request.Builder().url(url).build();

    }

    @Override
    public List<Comic> parseCategory(String html, int page) {
        List<Comic> list = new ArrayList<>();
        Node body = new Node(html);
        List<Node> comicNodes = body.list("div.grid > div.group");
        for (Node comicNode : comicNodes) {
            String title = comicNode.text("[data-flux-subheading]");
            String cover = comicNode.attr("div > a > img", "data-src");
            if (cover.isEmpty()) {
                cover = comicNode.src("div > a > img");
            }
            String[] tmp = comicNode.href("div > a").split("/");
            String cid = tmp[tmp.length - 1];
            list.add(new Comic(TYPE, cid, title, cover, null, null));
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
            list.add(new Pair<>("所有", ""));
            list.add(new Pair<>("魔幻", "mohuan"));
            list.add(new Pair<>("魔法", "mofa"));
            list.add(new Pair<>("熱血", "rexue"));
            list.add(new Pair<>("冒險", "maoxian"));
            list.add(new Pair<>("懸疑", "xuanyi"));
            list.add(new Pair<>("偵探", "zhentan"));
            list.add(new Pair<>("愛情", "aiqing"));
            list.add(new Pair<>("校園", "xiaoyuan"));
            list.add(new Pair<>("搞笑", "gaoxiao"));
            list.add(new Pair<>("四格", "sige"));
            list.add(new Pair<>("科幻", "kehuan"));
            list.add(new Pair<>("神鬼", "shengui"));
            list.add(new Pair<>("舞蹈", "wudao"));
            list.add(new Pair<>("音樂", "yinyue"));
            list.add(new Pair<>("百合", "baihe"));
            list.add(new Pair<>("後宮", "hougong"));
            list.add(new Pair<>("機戰", "jizhan"));
            list.add(new Pair<>("格鬥", "gedou"));
            list.add(new Pair<>("恐怖", "kongbu"));
            list.add(new Pair<>("萌系", "mengxi"));
            list.add(new Pair<>("武俠", "wuxia"));
            list.add(new Pair<>("社會", "shehui"));
            list.add(new Pair<>("歷史", "lishi"));
            list.add(new Pair<>("耽美", "danmei"));
            list.add(new Pair<>("勵志", "lizhi"));
            list.add(new Pair<>("職場", "zhichang"));
            list.add(new Pair<>("生活", "shenghuo"));
            list.add(new Pair<>("治癒", "zhiyu"));
            list.add(new Pair<>("偽娘", "weiniang"));
            list.add(new Pair<>("黑道", "heidao"));
            list.add(new Pair<>("戰爭", "zhanzheng"));
            list.add(new Pair<>("競技", "jingji"));
            list.add(new Pair<>("體育", "tiyu"));
            list.add(new Pair<>("美食", "meishi"));
            list.add(new Pair<>("腐女", "funv"));
            list.add(new Pair<>("宅男", "zhainan"));
            list.add(new Pair<>("推理", "tuili"));
            list.add(new Pair<>("雜誌", "zazhi"));
            return list;
        }

        @Override
        protected boolean hasOrder() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getOrder() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(new Pair<>("最新上架", ""));
            list.add(new Pair<>("最近更新", "-update"));
            list.add(new Pair<>("最高人氣", "-views"));
            return list;
        }

        @Override
        protected boolean hasArea() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getArea() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(new Pair<>("所有", ""));
            list.add(new Pair<>("日本", "japan"));
            list.add(new Pair<>("港台", "hongkong"));
            list.add(new Pair<>("歐美", "europe"));
            list.add(new Pair<>("內地", "china"));
            list.add(new Pair<>("韓國", "korea"));
            list.add(new Pair<>("其他", "other"));
            return list;
        }

        @Override
        protected boolean hasReader() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getReader() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(new Pair<>("所有", ""));
            list.add(new Pair<>("少女", "shaonv"));
            list.add(new Pair<>("少年", "shaonian"));
            list.add(new Pair<>("青年", "qingnian"));
            list.add(new Pair<>("兒童", "ertong"));
            list.add(new Pair<>("通用", "tongyong"));
            return list;
        }

        @Override
        protected boolean hasProgress() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getProgress() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(new Pair<>("所有", ""));
            list.add(new Pair<>("連載中", "0"));
            list.add(new Pair<>("已完結", "1"));
            return list;
        }

        @Override
        protected boolean hasYear() {
            return true;
        }

        @Override
        protected List<Pair<String, String>> getYear() {
            List<Pair<String, String>> list = new ArrayList<>();
            list.add(new Pair<>("所有", ""));
            list.add(new Pair<>("2025", "2025"));
            list.add(new Pair<>("2024", "2024"));
            list.add(new Pair<>("2023", "2023"));
            list.add(new Pair<>("2022", "2022"));
            list.add(new Pair<>("2021", "2021"));
            list.add(new Pair<>("2020", "2020"));
            list.add(new Pair<>("2019", "2019"));
            list.add(new Pair<>("2018", "2018"));
            list.add(new Pair<>("2017", "2017"));
            list.add(new Pair<>("2016", "2016"));
            list.add(new Pair<>("2015", "2015"));
            list.add(new Pair<>("2014", "2014"));
            list.add(new Pair<>("2013", "2013"));
            list.add(new Pair<>("2012", "2012"));
            list.add(new Pair<>("2011", "2011"));
            list.add(new Pair<>("2010", "2010"));
            list.add(new Pair<>("00年代", "200x"));
            list.add(new Pair<>("90年代", "199x"));
            list.add(new Pair<>("80年代", "198x"));
            list.add(new Pair<>("70年代或更早", "197x"));
            return list;
        }
    }


}
