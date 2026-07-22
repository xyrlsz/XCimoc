package com.xyrlsz.xcimocob.source;

import com.google.common.collect.Lists;
import com.xyrlsz.xcimocob.core.Manga;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.JsonIterator;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.parser.SearchIterator;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import kotlin.collections.ArraysKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.RangesKt;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;
import okhttp3.Headers;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

public class DuManWuApp extends MangaParser {
    public static final int TYPE = 117;
    public static final String DEFAULT_TITLE = "读漫屋app";
    private static final String MH_BASE_URL = "https://d9zfb53b.lstool.xyz";
    private static final String quanse = "ok37hy";
    private static final String g8bh4z = "g8bh4z";
    private static final Long openAddTime = System.currentTimeMillis();
    private static final String ref = "8";
    private static final String version = "3.1.05";
    private static final int lnum = 0;

    public DuManWuApp(Source source) {
        init(source);
        setParseImagesUseWebParser(true);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws Exception {

        if (page != 1) {
            return null;
        }
        int index = Math.min(keyword.length(), 12);
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("key", keyword.substring(0, index))
                .build();
        long time = System.currentTimeMillis();

        return new Request.Builder()
                .url(MH_BASE_URL + "/search")
                .method("POST", body)
                .addHeader("time", String.valueOf(time))
                .addHeader("sgin", getMd5Encrypt(time))
                .addHeader("ref", ref)
                .addHeader("version", version)
                .build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) throws JSONException {
        JSONObject data = new JSONObject(html);
        String responseData = data.getString("responseData");
        String aes128Decrypt = Aes128Decrypt(g8bh4z, responseData);
        if (aes128Decrypt == null) {
            return null;
        }
        JSONObject searchData = new JSONObject(aes128Decrypt);
        return new JsonIterator(searchData.getJSONArray("updata")) {
            @Override
            protected Comic parse(JSONObject object) throws JSONException {
                String cid = object.getString("acId");
                String cover = object.getString("acPic");
                String author = object.getString("authorName");
                String title = object.getString("bookName");
                String update = object.getString("latestChapterName");
                return new Comic(TYPE, cid, title, cover, update, author);
            }
        };
    }

    @Override
    public Request getInfoRequest(String cid) {
        long time = System.currentTimeMillis();
        return new Request.Builder()
                .url(MH_BASE_URL + "/comic3/" + cid)
                .addHeader("time", String.valueOf(time))
                .addHeader("sgin", getMd5Encrypt(time, cid))
                .addHeader("ref", ref)
                .addHeader("version", version)
                .build();

    }

    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException, JSONException {
        JSONObject data = new JSONObject(html);
        String responseData = data.getString("responseData");
        String aes128Decrypt = Aes128Decrypt(g8bh4z, responseData);
        assert aes128Decrypt != null;
        JSONObject bookDetail = new JSONObject(aes128Decrypt).getJSONObject("bookdetailed");
        String title = bookDetail.getString("bookName");
        String cover = bookDetail.getString("coverPic");

        String timestamp = bookDetail.getString("latestChapterTime");
        Instant instant = Instant.ofEpochMilli(Long.parseLong(timestamp) * 1000);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String update = dateTime.format(formatter);

        String intro = bookDetail.getString("intro");
        String author = bookDetail.getString("authorName");
        comic.setInfo(title, cover, update, intro, author, false);
        return comic;
    }

    @Override
    public Request getChapterRequest(String html, String cid) {

        long time = System.currentTimeMillis();
        return new Request.Builder()
                .url(MH_BASE_URL + "/chapterlist/" + cid)
                .addHeader("time", String.valueOf(time))
                .addHeader("sgin", getMd5Encrypt(time, cid))
                .addHeader("ref", ref)
                .addHeader("version", version)
                .build();
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) throws JSONException {
        JSONObject data = new JSONObject(html);
        String responseData = data.getString("responseData");
        String aes128Decrypt = Aes128Decrypt(g8bh4z, responseData);
        assert aes128Decrypt != null;
        JSONObject chapterData = new JSONObject(aes128Decrypt);
        JSONArray chapList = chapterData.getJSONArray("chaplist");
        List<Chapter> list = new LinkedList<>();
        for (int i = 0; i < chapList.length(); i++) {
            JSONObject item = chapList.getJSONObject(i);
            String title = item.getString("chaptername");
            String path = item.getString("chapterid");
            list.add(new Chapter(null, sourceComic, title, path));
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
        long time = System.currentTimeMillis();

        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("otime", String.valueOf(openAddTime))
                .addFormDataPart("lnum", String.valueOf(lnum))
                .build();

        return new Request.Builder()
                .url(StringUtils.format("%s/readcomic/%s/%s", MH_BASE_URL, cid, path))
                .method("POST", body)
                .addHeader("time", String.valueOf(time))
                .addHeader("sgin", getMd5Encrypt(time, cid, path))
                .addHeader("ref", ref)
                .addHeader("version", version)
                .build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws Manga.NetworkErrorException, JSONException {
        List<ImageUrl> list = new ArrayList<>();
        if (html.contains("<html>")) {
            Document document = Jsoup.parse(html);
            html = document.body().text();
            if (html.startsWith("\"")) {
                html = html.substring(1, html.length() - 1);
            }
        }
        JSONObject data = new JSONObject(html);
        String responseData = data.getString("responseData");
        String aes128Decrypt = Aes128Decrypt(g8bh4z, responseData);
        assert aes128Decrypt != null;
        JSONArray imgList = new JSONObject(aes128Decrypt).getJSONArray("piclist");
        for (int i = 1; i <= imgList.length(); i++) {
            long comicChapter = chapter.getId();
            long id = IdCreator.createImageId(comicChapter, i);
            String imgUrl = imgList.getString(i - 1);
            list.add(new ImageUrl(id, comicChapter, i, imgUrl, false));
        }

        return list;
    }

    private String Md5Encrypt(String str) {
        try {

            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            byte[] digest = messageDigest.digest(bytes);
            StringBuilder md5Encrypt = new StringBuilder();
            for (byte b : digest) {
                md5Encrypt.append(String.format("%02x", b));
            }
            return md5Encrypt.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String getMd5Encrypt(long timestamp, String id, String vid) {
        return Md5Encrypt((timestamp - 59852) + ref + quanse + id + vid);
    }

    private String getMd5Encrypt(long timestamp) {
        return Md5Encrypt((timestamp - 59852) + ref + quanse);
    }

    private String getMd5Encrypt(long timestamp, String id) {
        return Md5Encrypt((timestamp - 59852) + ref + quanse + id);
    }

    private String Aes128Decrypt(String aesKey, String encryptedData) {
        try {
            byte[] bytes = StringsKt.padEnd(aesKey, 16, (char) 0).getBytes(Charsets.UTF_8);
            SecretKeySpec secretKeySpec = new SecretKeySpec(bytes, "AES");
            byte[] decode = Base64.getDecoder().decode(encryptedData);
            Intrinsics.checkNotNull(decode);
            if (decode.length < 16) {
                return null;
            }
            byte[] sliceArray = ArraysKt.sliceArray(decode, RangesKt.until(0, 16));
            byte[] sliceArray2 = ArraysKt.sliceArray(decode, RangesKt.until(16, decode.length));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(2, secretKeySpec, new IvParameterSpec(sliceArray));
            byte[] doFinal = cipher.doFinal(sliceArray2);
            Intrinsics.checkNotNull(doFinal);
            return new String(doFinal, Charsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Headers getHeader() {
        return Headers.of("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
    }

}
