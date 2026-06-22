package com.xyrlsz.xcimocob.source;

import com.google.common.collect.Lists;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.soup.Node;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 拷贝漫画
 * <a href="https://github.com/ccbkv/venera-configs/blob/main/copy_manga.js">...</a>
 */

public class CopyMH extends CopyMHBase {
    public static final int TYPE = 26;
    public static final String DEFAULT_TITLE = "拷贝漫画";

    public CopyMH(Source source) {
        super(source, new Category(), TYPE, false, false);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, website);
    }


    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic)
            throws JSONException {
        List<Chapter> list = new LinkedList<>();
        String ccz;
        Pattern[] cczPatterns = {
                Pattern.compile("(?:var|let|const)\\s+ccz\\s*=\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("window\\.ccz\\s*=\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("ccz\\s*=\\s*['\"]([^'\"]+)['\"]")
        };
        ccz = Arrays.stream(cczPatterns).map(pattern -> pattern.matcher(html)).filter(matcher -> matcher.find() && matcher.group(1) != null).findFirst().map(matcher -> matcher.group(1)).orElse("");
        Node body = new Node(html);
        // 2. 提取 dnt
        String dnt = "";
        Node dntEl = body.getChild("#dnt");
        if (dntEl.get() != null) {
            dnt = dntEl.attr("value");
        }

        if (!ccz.isEmpty() && !dnt.isEmpty()) {
            String chapterUrl = website + "/comicdetail/" + comic.getCid() + "/chapters?format=json";
            Request request = new Request.Builder()
                    .url(chapterUrl)
                    .headers(getHeader())
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .addHeader("Referer", website + "/comic/" + comic.getCid())
                    .addHeader("dnts", dnt)
                    .build();

            try {
                Response response = App.getHttpClient().newCall(request).execute();
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    JSONObject rootObject = new JSONObject(json);
                    if (rootObject.getInt("code") == 200 && rootObject.has("results")) {
                        String encrypted = rootObject.getString("results").trim();

                        if (encrypted.length() > 16) {
                            // 5. AES解密
                            String ivStr = encrypted.substring(0, 16);
                            String cipherStr = encrypted.substring(16);


                            String plainText = aesDecrypt(cipherStr, ccz, ivStr);

                            // 6. 解析解密后的JSON
                            JSONObject parsedNode = new JSONObject(plainText);
                            JSONObject groupsNode = parsedNode.getJSONObject("groups");
                            if (groupsNode.length() > 0) {
                                Iterator<String> keys = groupsNode.keys();

                                while (keys.hasNext()) {
                                    String gKey = keys.next();

                                    JSONObject group = groupsNode.getJSONObject(gKey);

                                    String groupName = group.has("name")
                                            ? group.getString("name")
                                            : gKey;

                                    JSONArray chaptersArray = group.optJSONArray("chapters");

                                    if (chaptersArray != null && chaptersArray.length() > 0) {
                                        // 遍历章节
                                        for (int i = 0; i < chaptersArray.length(); i++) {
                                            JSONObject chapter = chaptersArray.getJSONObject(i);
                                            String title = chapter.getString("name");
                                            String uuid = chapter.getString("id");
                                            list.add(new Chapter(null, sourceComic, title, uuid, groupName));
                                        }
                                    }
                                }
                            }

                        }
                    }
                }
            } catch (Exception ignored) {

            }


        }
        list = Lists.reverse(list);
        for (int j = 0; j < list.size(); j++) {
            long id = IdCreator.createChapterId(sourceComic, j);
            list.get(j).setId(id);
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String chapterUrl = String.format("%s/comic/%s/chapter/%s", website, cid, path);
        return new Request.Builder()
                .headers(getHeader())
                .url(chapterUrl)
                .build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws JSONException {
        List<ImageUrl> list = new LinkedList<>();

        // 3. 提取 contentKey
        String contentKey = "";
        Pattern[] contentKeyPatterns = {
                Pattern.compile("(?:var|let|const)\\s+contentKey\\s*=\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("window\\.contentKey\\s*=\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("contentKey\\s*=\\s*['\"]([^'\"]+)['\"]")
        };
        for (Pattern pattern : contentKeyPatterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find() && matcher.group(1) != null) {
                contentKey = matcher.group(1);
                break;
            }
        }

        // 4. 提取 cct
        String cct = "";
        Pattern[] cctPatterns = {
                Pattern.compile("(?:var|let|const)\\s+cct\\s*=\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("window\\.cct\\s*=\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("cct\\s*=\\s*['\"]([^'\"]+)['\"]")
        };
        for (Pattern pattern : cctPatterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find() && matcher.group(1) != null) {
                cct = matcher.group(1);
                break;
            }
        }

        // 5. AES解密
        if (!StringUtils.isEmpty(contentKey) && !StringUtils.isEmpty(cct)) {
            assert contentKey != null;
            if (contentKey.length() > 16) {
                try {

                    String ivStr = contentKey.substring(0, 16);
                    String cipherStr = contentKey.substring(16);

                    assert cct != null;
                    byte[] keyBytes = cct.getBytes(StandardCharsets.UTF_8);
                    byte[] ivBytes = ivStr.getBytes(StandardCharsets.UTF_8);

                    byte[] cipherBytes;
                    if (cipherStr.matches("^[0-9a-fA-F]+$") && cipherStr.length() % 2 == 0) {
                        int len = cipherStr.length();
                        cipherBytes = new byte[len / 2];
                        for (int i = 0; i < len; i += 2) {
                            cipherBytes[i / 2] = (byte) Integer.parseInt(cipherStr.substring(i, i + 2), 16);
                        }
                    } else {
                        cipherBytes = Base64.getDecoder().decode(cipherStr);
                    }

                    // 执行AES/CBC/PKCS5Padding解密
                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
                    IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
                    byte[] plainBytes = cipher.doFinal(cipherBytes);
                    String plainText = new String(plainBytes, StandardCharsets.UTF_8);

                    int paddingLength = plainBytes[plainBytes.length - 1];
                    if (paddingLength > 0 && paddingLength <= 16) {
                        boolean validPadding = true;
                        for (int i = 0; i < paddingLength; i++) {
                            if (plainBytes[plainBytes.length - 1 - i] != paddingLength) {
                                validPadding = false;
                                break;
                            }
                        }
                        if (validPadding) {
                            plainBytes = Arrays.copyOfRange(plainBytes, 0, plainBytes.length - paddingLength);
                            plainText = new String(plainBytes, StandardCharsets.UTF_8);
                        }
                    }

                    Pattern jsonPattern = Pattern.compile("\\[.*]", Pattern.DOTALL);
                    Matcher jsonMatcher = jsonPattern.matcher(plainText);
                    if (jsonMatcher.find()) {
                        plainText = jsonMatcher.group(0);
                    }

                    assert plainText != null;
                    JSONArray urls = new JSONArray(plainText);
                    for (int i = 0; i < urls.length(); i++) {
                        JSONObject jsonObject = urls.getJSONObject(i);
                        String imgUrl = jsonObject.getString("url");
                        long comicChapter = chapter.getId();
                        long id = IdCreator.createImageId(comicChapter, i);
                        imgUrl = imgUrl.replaceAll("c\\d+x\\.[a-zA-Z]+$", "c" + 1500 + "x.webp");
                        list.add(new ImageUrl(id, comicChapter, i, imgUrl, false));
                    }

                } catch (Exception ignored) {
                }
            }
        }

        return list;
    }


    @Override
    public Headers getHeader() {
        Headers.Builder builder = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .add("platform", "2");
        return builder.build();
    }


    /**
     * 通用 AES/CBC/PKCS5Padding 解密方法
     *
     * @param cipherText 密文 (支持 Hex 或 Base64 编码)
     * @param key        密钥
     * @param ivStr      初始化向量
     * @return 解密后的明文字符串
     * @throws Exception 解密过程中可能出现的异常
     */
    private String aesDecrypt(String cipherText, String key, String ivStr) throws Exception {
        // 1. 处理密文：判断是 Hex 还是 Base64
        byte[] cipherBytes;
        if (cipherText.matches("^[0-9a-fA-F]+$") && cipherText.length() % 2 == 0) {
            // Hex 解码
            int len = cipherText.length();
            cipherBytes = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                cipherBytes[i / 2] = (byte) Integer.parseInt(cipherText.substring(i, i + 2), 16);
            }
        } else {
            // Base64 解码
            cipherBytes = Base64.getDecoder().decode(cipherText);
        }

        // 2. 执行 AES 解密
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivStr.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

}
