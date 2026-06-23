package com.xyrlsz.xcimocob.source;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Headers;
import okhttp3.Request;

/**
 * Created by xyrlsz on 2025/02/13.
 */

public class Manhuayu extends MangaParser {
    public static final int TYPE = 107;
    public static final String DEFAULT_TITLE = "漫画鱼";
    private static final String baseUrl = "https://www.manhuayu88.com";

    public Manhuayu(Source source) {
        init(source);
//        setParseImagesUseWebParser(true);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, baseUrl);
    }

    /**
     * 从混淆代码中还原真正的 AES 密钥
     * 对应 JS 中 _0x2e5d43(273, "GpdQ") 经过 RC4 解密后的值
     */
    private static String getAesKey() {
        // 混淆数组中的密文（对应索引 273-236=37 项）
        String encodedKey = "tauplCk/o8ky";
        // RC4 解密的密码（即 _0x2e5d43 的第二个参数）
        String rc4Password = "GpdQ";

        byte[] encryptedBytes = Base64.getDecoder().decode(encodedKey);
        byte[] decryptedBytes = rc4(encryptedBytes, rc4Password);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * RC4 算法实现（与 JS 中 _0xd7df6 完全一致）
     */
    private static byte[] rc4(byte[] input, String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.ISO_8859_1);
        int[] S = new int[256];
        int keyLen = keyBytes.length;

        // KSA
        for (int i = 0; i < 256; i++) {
            S[i] = i;
        }
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + S[i] + (keyBytes[i % keyLen] & 0xFF)) % 256;
            int tmp = S[i];
            S[i] = S[j];
            S[j] = tmp;
        }

        // PRGA
        byte[] output = new byte[input.length];
        int i = 0;
        j = 0;
        for (int k = 0; k < input.length; k++) {
            i = (i + 1) % 256;
            j = (j + S[i]) % 256;
            int tmp = S[i];
            S[i] = S[j];
            S[j] = tmp;
            int keystream = S[(S[i] + S[j]) % 256];
            output[k] = (byte) ((input[k] & 0xFF) ^ keystream);
        }
        return output;
    }

    /**
     * 解密 params 密文，返回明文字符串（JSON）
     */
    public static String decrypt(String base64) throws Exception {
        try {
            byte[] raw = Base64.getDecoder().decode(base64);

            // IV
            byte[] iv = Arrays.copyOfRange(raw, 0, 16);

            // 密文
            byte[] cipherText = Arrays.copyOfRange(raw, 16, raw.length);

            // key
            byte[] keyBytes = "5V&RoR%Jf@pJPydF".getBytes(StandardCharsets.UTF_8);

            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);

            byte[] decrypted = cipher.doFinal(cipherText);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void initUrlFilterList() {
        super.initUrlFilterList();
        filter.add(new UrlFilter("manhuayu.com"));
        filter.add(new UrlFilter("manhuayu8.com"));
        filter.add(new UrlFilter("manhuayu88.com"));
        filter.add(new UrlFilter("manhuayu5.com"));
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws Exception {
        if (page == 1) {
            String url = baseUrl + "/search?q=" + keyword;
            return new Request.Builder().url(url).build();
        }
        return null;
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        Node body = new Node(html);
        List<Node> resList = body.list("div.media");
        if (resList.isEmpty()) {
            return null;
        }
        return new NodeIterator(resList) {
            @Override
            protected Comic parse(Node node) {
                String title = node.text(".media-content > a.title");
                String cover = node.attr(".media-left > a", "data-original");
                String cid = node.href(".media-content > a.title").replace("/", "");
                return new Comic(TYPE, cid, title, cover, "", "");
            }
        };
    }

    @Override
    public Request getInfoRequest(String cid) {
        return new Request.Builder().url(baseUrl + "/" + cid).build();
    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException, JSONException {
        Node body = new Node(html);
        String title = body.text(".metas-title");
        String cover = body.src(".metas-image > img");
        String author = null;
        boolean status = true;
        for (Node node : body.list(".metas-body > .author")) {
            String tmp = node.text();
            if (tmp.contains("作者")) {
                author = tmp.replace("作者：", "").strip();
            } else if (tmp.contains("连载")) {
                status = false;
            }
        }
        String update = body.text(".has-text-danger");
        String intro = body.text(".metas-desc > p");
        comic.setInfo(title, cover, update, intro, author, status);
        return comic;
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        List<Node> chapterNodes = body.list("ul.comic-chapters > li > a");

        int i = 0;

        for (Node chapterNode : chapterNodes) {
            String title = chapterNode.text();
            String path = chapterNode.href().split("/")[2].replace(".html", "");
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
        String url = StringUtils.format("%s/%s/%s.html", baseUrl, cid, path);
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException, JSONException {
        List<ImageUrl> list = new ArrayList<>();

        // 1. 从 HTML 中提取 params 密文
        String encryptedParams = extractParams(html);
        if (encryptedParams == null || encryptedParams.isEmpty()) {
            return list;
        }

        // 2. 解密 params 得到 JSON 字符串
        String decryptedJson = "";
        try {
            decryptedJson = decrypt(encryptedParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
        JSONObject params = new JSONObject(decryptedJson);

        // 3. 获取图片路径数组和图片服务器配置
        JSONArray chapterImages = params.getJSONArray("chapter_images");
        JSONArray imagesHosts = params.getJSONArray("images_hosts");
        boolean imagesBase64 = params.optBoolean("images_base64", false);

        // 取第一个图片服务器作为前缀（或按需求实现多线路切换）
        String imageHost = imagesHosts.getString(0);

        // 4. 遍历图片路径，拼接完整 URL 并构建 ImageUrl 对象
        for (int i = 0; i < chapterImages.length(); i++) {
            Long comicChapter = chapter.getId();
            int index = i + 1; // 图片序号从 1 开始
            Long id = IdCreator.createImageId(comicChapter, index);

            String path = chapterImages.getString(i);
            if (path == null || path.isEmpty()) continue;

            String fullUrl = buildImageUrl(path, imageHost, imagesBase64);
            list.add(new ImageUrl(id, comicChapter, index, fullUrl, false, getHeader()));
        }

        return list;
    }

// ==================== AES 密钥还原相关 ====================

    /**
     * 从 HTML 脚本中提取 params 变量值
     */
    private String extractParams(String html) {
        // 匹配 params = '...'  ，内部允许 \' 转义
        Pattern pattern = Pattern.compile(
                "params\\s*=\\s*'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'"
        );
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 拼接图片完整 URL
     *
     * @param path      原始路径（可能是相对路径或需 Base64 编码的字符串）
     * @param host      图片服务器域名（例如 "https://img.example.com"）
     * @param useBase64 是否需要对 path 进行 Base64 编码
     */
    private String buildImageUrl(String path, String host, boolean useBase64) {
        if (path == null || path.isEmpty()) return "";

        if (useBase64) {
            // 先对路径进行标准 Base64 编码，然后拼接
            String encoded = Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8));
            return host + "/" + encoded;
        } else if (!path.matches("^(https?:)?//.*")) {
            // 相对路径，加上域名前缀，确保以 "/" 连接
            return host + (path.startsWith("/") ? path : "/" + path);
        } else {
            // 已经是完整 URL，直接返回
            return path;
        }
    }

    @Override
    public String getUrl(String cid) {
        return baseUrl + "/" + cid;
    }

    @Override
    public Headers getHeader() {
        return new Headers.Builder()
                .add("referer", baseUrl + "/")
                .add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36 Edg/133.0.0.0")
                .build();
    }
}
