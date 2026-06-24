package com.xyrlsz.xcimocob.source;

import android.annotation.SuppressLint;

import com.google.common.collect.Lists;
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
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.Request;

public class GoDaManHua extends MangaParser {
    public static final int TYPE = 108;
    public static final String DEFAULT_TITLE = "G社漫畫";
    private static final String baseUrl = "https://m.g-mh.org";
    private static final String picBaseUrl = "https://t40-1-4.g-mh.online";
    private static final String apiBaseUrl = "https://api-get-v3.mgsearcher.com";
    private String _mid = "";

    public GoDaManHua(Source source) {
        init(source);
//        setParseImagesUseWebParser(true);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, baseUrl);
    }


    @Override
    protected void initUrlFilterList() {
        super.initUrlFilterList();
        filter.add(new UrlFilter("manhuafree.com", "manga/([\\w-]+)"));
        filter.add(new UrlFilter("m.g-mh.org", "manga/([\\w\\-]+)"));
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws Exception {
        if (page == 1) {
            String url = baseUrl + "/s/" + keyword;
            return new Request.Builder().url(url).build();
        }
        return null;
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        List<Node> list = new Node(html).list(".cardlist > div.pb-2");
        if (list.isEmpty()) {
            return null;
        }
        return new NodeIterator(list) {
            @Override
            protected Comic parse(Node node) {
                String title = node.text(".cardtitle");
                String cover = node.src(".text-center > div > img");
                String cid = node.href("a").split("/")[2];
                return new Comic(TYPE, cid, title, cover, "", "");
            }
        };
    }

    @Override
    public Request getInfoRequest(String cid) {
        return new Request.Builder().url(baseUrl + "/manga/" + cid).build();
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException, JSONException {
        String json = StringUtils.match("<script type=\"application/ld\\+json\">(.*?)</script>", html, 1);
        if (json != null && !json.isEmpty()) {
            JSONObject data = new JSONObject(json);
            String title = data.getString("name");
            String intro = data.getString("description");
            String cover = data.getString("image");
            String status = data.getString("creativeWorkStatus");
            StringBuilder author = new StringBuilder();
            JSONArray authorArray = data.getJSONArray("author");
            for (int i = 0; i < authorArray.length(); i++) {
                JSONObject authorObject = authorArray.getJSONObject(i);
                author.append(authorObject.getString("name"));
                if (i < authorArray.length() - 1) {
                    author.append(",");
                }
            }
            String update = data.getJSONObject("hasPart").getString("datePublished");
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            isoFormat.setTimeZone(TimeZone.getDefault());

            Date date = null;
            try {
                date = isoFormat.parse(update);
            } catch (ParseException e) {
                e.printStackTrace();
            }

            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            outputFormat.setTimeZone(TimeZone.getDefault());

            update = outputFormat.format(Objects.requireNonNull(date));
            comic.setInfo(title, cover, update, intro, author.toString(), isFinish(status));

        }
        Node body = new Node(html);
        _mid = body.id("bookmarkData").attr("data-mid");
        return comic;
    }

    @Override
    public Request getChapterRequest(String html, String cid) {
        return new Request.Builder().url(StringUtils.format(apiBaseUrl + "/api/v2/manga/get?mid=%s&mode=all", _mid))
                .addHeader("referer", baseUrl.concat("/"))
                .build();
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        List<Chapter> list = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(html);
        JSONArray chapters = jsonObject.getJSONObject("data").getJSONArray("chapters");

        for (int i = 0; i < chapters.length(); i++) {
            String title = chapters.getJSONObject(i).getJSONObject("attributes").getString("title");
            String path = chapters.getJSONObject(i).getLong("id") + "";
            list.add(new Chapter(null, sourceComic, title, path));
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
        return new Request.Builder().url(StringUtils.format(apiBaseUrl + "/api/v2/chapter/getinfo?m=%s&c=%s", _mid, path))
                .addHeader("referer", baseUrl.concat("/"))
                .addHeader("Accept", "application/json")
                .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
                .build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException, JSONException {
        List<ImageUrl> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\{.*\\}");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            html = matcher.group(0);
        }
        assert html != null;
        String imagesData = new JSONObject(html)
                .getJSONObject("data")
                .getJSONObject("info")
                .getJSONObject("images")
                .getString("images");
        String json = DecryptUtil.decrypt(imagesData);
        JSONArray images = new JSONArray(json);
        for (int i = 1; i <= images.length(); i++) {
            long comicChapter = chapter.getId();
            long id = IdCreator.createImageId(comicChapter, i);
            String imgUrl = picBaseUrl + images.getJSONObject(i - 1).getString("url");
            list.add(new ImageUrl(id, comicChapter, i, imgUrl, false));
        }
        return list;
    }

    @Override
    public String getUrl(String cid) {
        return StringUtils.format("%s/manga/%s", baseUrl, cid);
    }

    @Override
    public Headers getHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36");
        headers.put("referer", baseUrl.concat("/"));
        return Headers.of(headers);
    }

    public static class DecryptUtil {

        // 前后缀与魔法常量
        private static final String PREFIX = "J7r";
        private static final String SUFFIX = "nQ";
        private static final String MAGIC = "W4s";
        private static final String SEP = "kD";

        // 字符映射表（重要：TABLE2 → TABLE1）
        private static final String TABLE1 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        private static final String TABLE2 = "_-9876543210abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // 分组反转的块大小（对应 JS 中的 _0x240e27 = 7）
        private static final int CHUNK_SIZE = 7;

        /**
         * 主解密函数
         */
        public static String decrypt(String input) {
            if (input == null || !input.startsWith(PREFIX) || !input.endsWith(SUFFIX)) {
//                throw new RuntimeException("x2");
                return "";
            }

            // 1. 去除前后缀
            String body = input.substring(PREFIX.length(), input.length() - SUFFIX.length());

            // 2. 计算有效长度（扣除分隔符和魔数的长度）
            int totalLen = body.length();
            int effectiveLen = totalLen - SEP.length() - MAGIC.length();

            int a = effectiveLen / 3;                                    // part5 的长度
            int b = (int) Math.ceil((effectiveLen - a) / 2.0);          // part1 的长度
            int c = effectiveLen - a - b;                                // part3 的长度

            // 3. 按长度切分五段
            String part1 = body.substring(0, b);
            String part2 = body.substring(b, b + SEP.length());
            String part3 = body.substring(b + SEP.length(), b + SEP.length() + c);
            String part4 = body.substring(b + SEP.length() + c, b + SEP.length() + c + MAGIC.length());
            String part5 = body.substring(b + SEP.length() + c + MAGIC.length());

            // 4. 校验分隔符、魔数以及 part5 长度
            if (!part2.equals(SEP) || !part4.equals(MAGIC) || part5.length() != a) {
                return "";
            }

            // 5. 重组
            String merged = part5 + part1 + part3;

            // 6. 分组反转（奇数索引块反转）
            String reversed = reverseChunks(merged, CHUNK_SIZE);

            // 7. 字符表映射（TABLE2 → TABLE1）
            String mapped = mapChars(reversed);

            // 8. Base64url 解码（映射结果为 URL 安全 Base64）
            byte[] decoded = Base64.getUrlDecoder().decode(mapped);

            return new String(decoded, StandardCharsets.UTF_8);
        }

        /**
         * 分组反转：每 chunkSize 个字符为一组，奇数索引组（1,3,5...）反转
         */
        private static String reverseChunks(String input, int chunkSize) {
            StringBuilder result = new StringBuilder();
            for (int i = 0, idx = 0; i < input.length(); i += chunkSize, idx++) {
                int end = Math.min(i + chunkSize, input.length());
                String chunk = input.substring(i, end);
                if (idx % 2 == 1) { // 奇数索引反转
                    chunk = new StringBuilder(chunk).reverse().toString();
                }
                result.append(chunk);
            }
            return result.toString();
        }

        /**
         * 自定义字符替换：将 TABLE2 中的字符映射为 TABLE1 中的对应字符
         */
        private static String mapChars(String input) {
            StringBuilder sb = new StringBuilder();
            for (char c : input.toCharArray()) {
                int idx = TABLE2.indexOf(c);
                if (idx == -1) {
                    return "";
                }
                sb.append(TABLE1.charAt(idx));
            }
            return sb.toString();
        }
    }
}
