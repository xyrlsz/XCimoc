package com.xyrlsz.xcimocob.network.sync;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.manager.PreferenceManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 与 Cimoc Data Sync Server (Go) 通信的 API 客户端
 *
 * 使用 OkHttp + Gson，无需 Retrofit 依赖。
 * 所有方法均同步执行，调用方应在后台线程执行。
 */
public class DataSyncClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new GsonBuilder().create();

    private final OkHttpClient mHttpClient;
    private final String mBaseUrl;

    public DataSyncClient() {
        this(App.getPreferenceManager().getString(PreferenceManager.PREF_DATA_SERVER_URL, ""));
    }

    public DataSyncClient(String baseUrl) {
        // 去除末尾斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        this.mBaseUrl = baseUrl;

        mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(mBaseUrl);
    }

    // ==================== Auth ====================

    /**
     * 登录，返回 token
     */
    public DataSyncModels.LoginResponse login(String username, String password)
            throws IOException, DataSyncException {
        DataSyncModels.LoginRequest req = new DataSyncModels.LoginRequest(username, password);
        String json = GSON.toJson(req);
        String body = post("/api/auth/login", json);
        return GSON.fromJson(body, DataSyncModels.LoginResponse.class);
    }

    /**
     * 刷新 token（延长有效期）
     */
    public String refreshToken(String oldToken) throws IOException, DataSyncException {
        String body = post("/api/auth/refresh", "{}", oldToken);
        // 解析响应 {"token": "..."}
        RefreshTokenResponse resp = GSON.fromJson(body, RefreshTokenResponse.class);
        return resp != null ? resp.token : null;
    }

    private static class RefreshTokenResponse {
        public String token;
    }

    // ==================== Token 工具 ====================

    /**
     * 检查 token 是否需要刷新（即将在 7 天内过期时返回 true）
     */
    public static boolean isTokenExpiringSoon(String token) {
        if (TextUtils.isEmpty(token)) return false;
        try {
            // JWT 格式: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length < 2) return false;

            byte[] payload = Base64.decode(parts[1], Base64.URL_SAFE);
            String json = new String(payload, "UTF-8");

            // 快速解析 exp 字段
            int expIdx = json.indexOf("\"exp\"");
            if (expIdx < 0) return false;
            int colonIdx = json.indexOf(":", expIdx + 4);
            if (colonIdx < 0) return false;

            // 提取数字
            StringBuilder numStr = new StringBuilder();
            for (int i = colonIdx + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c >= '0' && c <= '9') {
                    numStr.append(c);
                } else if (numStr.length() > 0) {
                    break;
                }
            }
            if (numStr.length() == 0) return false;

            long exp = Long.parseLong(numStr.toString()) * 1000; // 转毫秒
            long now = System.currentTimeMillis();
            long sevenDays = 7L * 24 * 60 * 60 * 1000;

            // 如果剩余有效期少于 7 天，需要刷新
            return (exp - now) < sevenDays;
        } catch (Exception e) {
            Log.w("DataSyncClient", "Failed to parse token expiry", e);
            return false;
        }
    }

    /**
     * 确保 token 有效：如果需要刷新则自动刷新并保存
     * @return 有效的 token，如果刷新失败则返回 null
     */
    public static String ensureValidToken() {
        PreferenceManager pm = App.getPreferenceManager();
        String token = pm.getString(PreferenceManager.PREFERENCES_USER_TOCKEN, "");
        if (TextUtils.isEmpty(token)) return null;

        if (!isTokenExpiringSoon(token)) {
            return token; // token 仍然有效
        }

        // 需要刷新
        try {
            String serverUrl = pm.getString(PreferenceManager.PREF_DATA_SERVER_URL, "");
            if (TextUtils.isEmpty(serverUrl)) return token;

            DataSyncClient client = new DataSyncClient(serverUrl);
            String newToken = client.refreshToken(token);
            if (newToken != null) {
                pm.putString(PreferenceManager.PREFERENCES_USER_TOCKEN, newToken);
                Log.d("DataSyncClient", "Token refreshed successfully");
                return newToken;
            }
        } catch (Exception e) {
            Log.w("DataSyncClient", "Token refresh failed, using old token", e);
        }
        return token; // 刷新失败，继续使用旧 token
    }

    // ==================== Comics ====================

    /**
     * 获取服务端该用户的所有漫画
     */
    public List<DataSyncModels.ComicServerItem> listComics(String token)
            throws IOException, DataSyncException {
        String body = get("/api/comics", token);
        DataSyncModels.ComicListResponse resp = GSON.fromJson(body, DataSyncModels.ComicListResponse.class);
        return resp != null ? resp.comics : null;
    }

    /**
     * 同步漫画到服务端
     */
    public DataSyncModels.ComicSyncResponse syncComics(String token, List<DataSyncModels.ComicSyncItem> comics)
            throws IOException, DataSyncException {
        DataSyncModels.ComicSyncRequest req = new DataSyncModels.ComicSyncRequest(comics);
        String json = GSON.toJson(req);
        String body = post("/api/comics/sync", json, token);
        return GSON.fromJson(body, DataSyncModels.ComicSyncResponse.class);
    }

    /**
     * 删除服务端某条漫画记录
     */
    public void deleteComic(String token, long comicId)
            throws IOException, DataSyncException {
        delete("/api/comics/" + comicId, token);
    }

    // ==================== Settings ====================

    /**
     * 获取服务端该用户的所有设置
     */
    public List<DataSyncModels.SettingServerItem> listSettings(String token)
            throws IOException, DataSyncException {
        String body = get("/api/settings", token);
        DataSyncModels.SettingListResponse resp = GSON.fromJson(body, DataSyncModels.SettingListResponse.class);
        return resp != null ? resp.settings : null;
    }

    /**
     * 同步设置到服务端
     */
    public DataSyncModels.SettingSyncResponse syncSettings(String token, List<DataSyncModels.SettingItem> settings)
            throws IOException, DataSyncException {
        DataSyncModels.SettingSyncRequest req = new DataSyncModels.SettingSyncRequest(settings);
        String json = GSON.toJson(req);
        String body = post("/api/settings/sync", json, token);
        return GSON.fromJson(body, DataSyncModels.SettingSyncResponse.class);
    }

    // ==================== Tags ====================

    /**
     * 获取服务端该用户的所有标签（含关联漫画）
     */
    public List<DataSyncModels.TagServerItem> listTags(String token)
            throws IOException, DataSyncException {
        String body = get("/api/tags", token);
        DataSyncModels.TagListResponse resp = GSON.fromJson(body, DataSyncModels.TagListResponse.class);
        return resp != null ? resp.tags : null;
    }

    /**
     * 同步标签到服务端（全量替换）
     */
    public DataSyncModels.TagSyncResponse syncTags(String token, List<DataSyncModels.TagSyncItem> tags)
            throws IOException, DataSyncException {
        DataSyncModels.TagSyncRequest req = new DataSyncModels.TagSyncRequest(tags);
        String json = GSON.toJson(req);
        String body = post("/api/tags/sync", json, token);
        return GSON.fromJson(body, DataSyncModels.TagSyncResponse.class);
    }

    // ==================== HTTP helpers ====================

    private String get(String path, String token) throws IOException, DataSyncException {
        Request.Builder builder = new Request.Builder()
                .url(mBaseUrl + path)
                .get();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return execute(builder.build());
    }

    private String post(String path, String jsonBody) throws IOException, DataSyncException {
        return post(path, jsonBody, null);
    }

    private String post(String path, String jsonBody, String token) throws IOException, DataSyncException {
        Request.Builder builder = new Request.Builder()
                .url(mBaseUrl + path)
                .post(RequestBody.create(jsonBody, JSON));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return execute(builder.build());
    }

    private String delete(String path, String token) throws IOException, DataSyncException {
        Request.Builder builder = new Request.Builder()
                .url(mBaseUrl + path)
                .delete();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return execute(builder.build());
    }

    private String execute(Request request) throws IOException, DataSyncException {
        Response response = mHttpClient.newCall(request).execute();
        String body = response.body() != null ? response.body().string() : "";

        if (!response.isSuccessful()) {
            String errorMsg = "请求失败: HTTP " + response.code();
            // 尝试解析服务端返回的错误信息
            if (!TextUtils.isEmpty(body)) {
                try {
                    DataSyncModels.ErrorResponse err = GSON.fromJson(body, DataSyncModels.ErrorResponse.class);
                    if (err != null && err.error != null) {
                        errorMsg = err.error;
                    }
                } catch (Exception ignored) {
                }
            }
            throw new DataSyncException(response.code(), errorMsg);
        }

        return body;
    }

    /**
     * 自定义异常，包含 HTTP 状态码
     */
    public static class DataSyncException extends Exception {
        public final int httpCode;

        public DataSyncException(int httpCode, String message) {
            super(message);
            this.httpCode = httpCode;
        }
    }
}
