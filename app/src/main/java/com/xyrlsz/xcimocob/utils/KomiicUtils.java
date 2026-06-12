package com.xyrlsz.xcimocob.utils;

import static android.content.Context.MODE_PRIVATE;
import static com.xyrlsz.xcimocob.Constants.KOMIIC_SHARED;
import static com.xyrlsz.xcimocob.Constants.KOMIIC_SHARED_COOKIES;
import static com.xyrlsz.xcimocob.Constants.KOMIIC_SHARED_EXPIRED;
import static com.xyrlsz.xcimocob.Constants.KOMIIC_SHARED_PASSWD;
import static com.xyrlsz.xcimocob.Constants.KOMIIC_SHARED_USERNAME;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class KomiicUtils {
    /**
     * 从 Cookie 字符串中提取 JWT，解码 Payload 获取 exp 时间戳
     */
    private static long getExpFromJwt(String cookieStr) {
        if (cookieStr == null || cookieStr.isEmpty()) return -1L;
        try {
            // 匹配 JWT 模式：三个 Base64 URL-safe 段，用点分隔
            Pattern pattern = Pattern.compile("([A-Za-z0-9\\-_]+)\\.([A-Za-z0-9\\-_]+)\\.([A-Za-z0-9\\-_]+)");
            Matcher matcher = pattern.matcher(cookieStr);
            if (matcher.find()) {
                String payload = matcher.group(2);
                byte[] decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE);
                JSONObject json = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
                if (json.has("exp")) {
                    return json.getLong("exp");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1L;
    }

    public static boolean checkExpired() {
        SharedPreferences sharedPreferences = App.getAppContext().getSharedPreferences(Constants.KOMIIC_SHARED, MODE_PRIVATE);
        String cookies = sharedPreferences.getString(Constants.KOMIIC_SHARED_COOKIES, "");
        // 优先从 JWT 中解码 exp
        long expired = getExpFromJwt(cookies);
        // 如果 JWT 中取不到，fallback 到之前保存的 expired 值
        if (expired == -1L) {
            expired = sharedPreferences.getLong(Constants.KOMIIC_SHARED_EXPIRED, -1L);
        }
        // 没有 cookie 则视为已过期（需要重新登录）
        if (cookies.isEmpty()) return true;
        // 有 cookie 但无法获取过期时间，保守起见认为未过期
        if (expired == -1L) return false;
        long now = System.currentTimeMillis() / 1000;
        return now >= expired;
    }
    public static void login(Context context, String username,String password){
        login(context, username, password, new OnLoginListener() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onFail() {
                HintUtils.showToast(context,"Komiic 自动登录失败，请重新登录");
            }
        });
    }
    public static void login(Context context, String username,String password ,OnLoginListener listener){
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        String json = "{\"email\":\"" + username + "\", \"password\":\"" + password + "\"}";
        RequestBody body = RequestBody.create(mediaType, json);
        Request request = new Request.Builder()
                .url("https://komiic.com/api/login")
                .addHeader("referer", "https://komiic.com/login")
                .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
                .post(body)
                .build();

        Objects.requireNonNull(App.getHttpClient()).newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                listener.onFail();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<String> cookies = response.headers("Set-Cookie");
                if (response.isSuccessful() && !cookies.isEmpty()) {
                    Set<String> set = new HashSet<>();
                    for (String s : cookies) {
                        List<String> tmp = Arrays.asList(s.split("; "));
                        set.addAll(tmp);
                    }
                    String cookieStr = String.join("; ", set);
                    // 从 JWT 中解码 exp，不依赖 API 的 expire 字段
                    long expired = getExpFromJwt(cookieStr);
                    // fallback: 从 API 响应中读取 expire 字段（同时消费响应体避免泄漏）
                    String responseBody = response.body().string();
                    if (expired == -1L) {
                        try {
                            JSONObject data = new JSONObject(responseBody);
                            if (data.has("expire")) {
                                expired = KomiicUtils.toTimestamp(data.getString("expire"));
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    SharedPreferences sharedPreferences = context.getSharedPreferences(KOMIIC_SHARED, MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(KOMIIC_SHARED_COOKIES, cookieStr);
                    editor.putString(KOMIIC_SHARED_USERNAME, username);
                    editor.putString(KOMIIC_SHARED_PASSWD, password);
                    editor.putLong(KOMIIC_SHARED_EXPIRED, expired);
                    editor.apply();
                    listener.onSuccess();
                }else {
                    listener.onFail();
                }
            }
        });
    }
    public static void refresh(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.KOMIIC_SHARED, MODE_PRIVATE);
        String cookies = sharedPreferences.getString(KOMIIC_SHARED_COOKIES, "");
        if (cookies.isEmpty()) return; // 没有 cookie 无需刷新

        Request request = new Request.Builder()
                .url("https://komiic.com/auth/refresh")
                .post(RequestBody.create(MediaType.get("application/json"), ""))
                .addHeader("content-type", "application/json")
                .addHeader("cookie", cookies)
                .addHeader("Referer", "https://komiic.com/")
                .build();

        try {
            Objects.requireNonNull(App.getHttpClient()).newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    // 网络失败时尝试用保存的账号密码重新登录
                    SharedPreferences sp = context.getSharedPreferences(Constants.KOMIIC_SHARED, MODE_PRIVATE);
                    String username = sp.getString(KOMIIC_SHARED_USERNAME, "");
                    String password = sp.getString(KOMIIC_SHARED_PASSWD, "");
                    if (!username.isEmpty() && !password.isEmpty()) {
                        login(context, username, password);
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    SharedPreferences sp = context.getSharedPreferences(Constants.KOMIIC_SHARED, MODE_PRIVATE);
                    // 先消费响应体避免泄漏
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        List<String> setCookieHeaders = response.headers("Set-Cookie");
                        if (!setCookieHeaders.isEmpty()) {
                            // 有新的 Set-Cookie，更新存储
                            Set<String> set = new HashSet<>();
                            for (String s : setCookieHeaders) {
                                List<String> tmp = Arrays.asList(s.split("; "));
                                set.addAll(tmp);
                            }
                            String cookieStr = String.join("; ", set);
                            long expired = getExpFromJwt(cookieStr);
                            if (expired == -1L) {
                                // fallback: 从 API 响应中读取 expire 字段
                                try {
                                    JSONObject data = new JSONObject(responseBody);
                                    if (data.has("expire")) {
                                        expired = KomiicUtils.toTimestamp(data.getString("expire"));
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                            SharedPreferences.Editor editor = sp.edit();
                            editor.putString(KOMIIC_SHARED_COOKIES, cookieStr);
                            editor.putLong(KOMIIC_SHARED_EXPIRED, expired);
                            editor.apply();
                        }
                        // else: 刷新成功但没有新 cookie，保留旧 cookie 即可
                    } else {
                        // refresh 失败（如 401），用保存的账号密码重新登录
                        String username = sp.getString(KOMIIC_SHARED_USERNAME, "");
                        String password = sp.getString(KOMIIC_SHARED_PASSWD, "");
                        if (!username.isEmpty() && !password.isEmpty()) {
                            login(context, username, password);
                        }
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String FormatTime(String t) {
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        try {
            Date date = inputFormat.parse(t);
            return outputFormat.format(Objects.requireNonNull(date));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return t;
    }

    public static void getImageLimit(UpdateImageLimitCallback callback) {
        SharedPreferences sharedPreferences = App.getAppContext().getSharedPreferences(Constants.KOMIIC_SHARED, MODE_PRIVATE);
        String cookies = sharedPreferences.getString(KOMIIC_SHARED_COOKIES, "");
        getImageLimit(cookies, callback);
    }

    public static void getImageLimit(String cookies, UpdateImageLimitCallback callback) {
        String json = "{\"operationName\":\"getImageLimit\",\"variables\":{},\"query\":\"query getImageLimit {\\n  getImageLimit {\\n    limit\\n    usage\\n    resetInSeconds\\n    __typename\\n  }\\n}\"}";

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"), json);

        Request request = new Request.Builder()
                .url("https://komiic.com/api/query")
                .addHeader("accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
                .addHeader("referer", "https://komiic.com/login")
                .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
                .addHeader("cookie", cookies)
                .post(body)
                .build();
        try {
            App.getHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        JSONObject data;
                        try {
                            String json = response.body().string();
                            data = new JSONObject(json).getJSONObject("data");
                            int limit = data.getJSONObject("getImageLimit").getInt("limit");
                            int usage = data.getJSONObject("getImageLimit").getInt("usage");
                            usage = Math.max(usage - 1, 0);
                            int res = limit - usage;
                            callback.onSuccess(Math.max(res, 0));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean checkImgLimit(String cookies) {
        String json = "{\"operationName\":\"getImageLimit\",\"variables\":{},\"query\":\"query getImageLimit {\\n  getImageLimit {\\n    limit\\n    usage\\n    resetInSeconds\\n    __typename\\n  }\\n}\"}";

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"), json);

        Request request = new Request.Builder()
                .url("https://komiic.com/api/query")
                .addHeader("referer", "https://komiic.com/login")
                .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
                .addHeader("cookie", cookies)
                .post(body)
                .build();
        try {
            Response response = Objects.requireNonNull(App.getHttpClient()).newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject data;
                try {
                    String respJson = response.body().string();
                    data = new JSONObject(respJson).getJSONObject("data");
                    int limit = data.getJSONObject("getImageLimit").getInt("limit");
                    int usage = data.getJSONObject("getImageLimit").getInt("usage");
                    return limit - usage <= 0;
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean checkIsOverImgLimit() {
        SharedPreferences sharedPreferences = App.getAppContext().getSharedPreferences(Constants.KOMIIC_SHARED, MODE_PRIVATE);
        String cookies = sharedPreferences.getString(KOMIIC_SHARED_COOKIES, "");
        return checkImgLimit(cookies);
    }

    public static boolean checkEmptyAccountIsOverImgLimit() {
        return checkImgLimit("");
    }

    public static Long toTimestamp(String t) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        long timestamp = 0;
        try {
            Date date = dateFormat.parse(t);
            timestamp = Objects.requireNonNull(date).getTime() / 1000;

        } catch (ParseException e) {
            e.printStackTrace();
        }
        return timestamp;
    }

    public interface UpdateImageLimitCallback {
        void onSuccess(int result);
    }

    public interface OnLoginListener {
        void onSuccess();
        void onFail();
    }
}
