package com.xyrlsz.xcimocob.source.js;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebSettings;

import androidx.appcompat.app.AlertDialog;

import com.xyrlsz.quickjs.QuickJSEngine;
import com.xyrlsz.quickjs.SourceCodec;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.utils.HintUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class JsHost implements QuickJSEngine.HostBridge {

    public static final JsHost INSTANCE = new JsHost();
    private static final String PREFS = "js_source";
    private static final String TAG = "JsHost";
    private static final long NODE_LIFETIME_MS = 30_000L;
    private static final int MAX_HTML_LENGTH = 5_000_000;
    private static final int MAX_NODE_COUNT = 10_000;
    private static final int NODE_TRIM_BATCH = 1_000;
    private static final int REQUEST_TIMEOUT_SECONDS = 10;
    private static final long MAX_RESPONSE_BYTES = 10L * 1024L * 1024L;
    private static volatile String DEFAULT_USER_AGENT;
    private final ConcurrentHashMap<Integer, NodeEntry> mNodeMap = new ConcurrentHashMap<>();
    private final AtomicInteger mNodeSeq = new AtomicInteger(1);
    private final ConcurrentHashMap<String, String> mState = new ConcurrentHashMap<>();

    private JsHost() {
    }

    @Override
    public String onHostCall(String name, String argsJson) {
        try {
            return switch (name) {
                case "dom" -> handleDom(argsJson);
                case "md5" -> quoteStr(SourceCodec.md5(arg(argsJson, "data")));
                case "lz64" -> quoteStr(SourceCodec.lz64Decrypt(arg(argsJson, "data")));
                case "base64" -> handleBase64(argsJson);
                case "aes_cbc" -> handleAesCbc(argsJson);
                case "state" -> handleState(argsJson);
                case "setting" -> handleSetting(argsJson);
                case "login" -> handleLogin(argsJson);
                case "fetch" -> handleFetch(argsJson);
                case "log" -> {
                    Log.d("JsSource", "[" + arg(argsJson, "type") + "] " + arg(argsJson, "data"));
                    yield "null";
                }
                case "toast" -> {
                    HintUtils.showToast(arg(argsJson, "data"));
                    yield "null";
                }
                case "dialog" -> {
                    handleDialog(argsJson);
                    yield "null";
                }
                case "urlencode" -> quoteStr(SourceCodec.urlEncode(arg(argsJson, "data")));
                case "urldecode" -> quoteStr(SourceCodec.urlDecode(arg(argsJson, "data")));
                default -> "null";
            };
        } catch (Exception e) {
            Log.e(TAG, "onHostCall(" + name + ") error", e);
            return "null";
        }
    }

    private void handleDialog(String argsJson) throws JSONException {
        Activity activity = App.getCurrentActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        String message = arg(argsJson, "data");
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) {
                return;
            }
            new AlertDialog.Builder(activity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
    }

    private String handleDom(String argsJson) throws JSONException {
        JSONObject o = new JSONObject(argsJson);
        String op = o.optString("op");
        if ("create".equals(op)) {
            String html = o.optString("html");
            if (html.length() > MAX_HTML_LENGTH) {
                Log.w(TAG, "HTML too large, length=" + html.length());
                return "null";
            }
            Element root = Jsoup.parse(html).body();
            int id = nextNodeId();
            mNodeMap.put(id, new NodeEntry(root));
            trimNodeMap();
            return new JSONObject().put("id", id).toString();
        }
        int id = o.optInt("id", -1);
        NodeEntry entry = mNodeMap.get(id);
        if (entry == null) {
            return "null";
        }
        entry.lastAccess = System.currentTimeMillis();
        Element base = entry.element;
        String sel = o.isNull("sel") ? null : o.optString("sel");
        switch (op) {
            case "select": {
                if (sel == null || sel.isEmpty()) {
                    return "null";
                }
                List<Element> elements = base.select(sel);
                if (elements.isEmpty()) {
                    return "[]";
                }
                JSONArray out = new JSONArray();
                for (Element child : elements) {
                    int cid = nextNodeId();
                    mNodeMap.put(cid, new NodeEntry(child));
                    out.put(cid);
                }
                trimNodeMap(); // 修复：及时清理
                return out.toString();
            }
            case "text": {
                Element target = resolveTarget(base, sel);
                return quoteStr(target == null ? null : target.text().trim());
            }
            case "attr": {
                Element target = resolveTarget(base, sel);
                String attr = o.optString("attr");
                return quoteStr(target == null ? null : emptyToNull(target.attr(attr)));
            }
            case "href":
                return quoteStr(attrOf(base, sel, "href"));
            case "src":
                return quoteStr(attrOf(base, sel, "src"));
            case "data-src":
                return quoteStr(attrOf(base, sel, "data-src"));
            default:
                return "null";
        }
    }

    private int nextNodeId() {
        int id = mNodeSeq.getAndIncrement();
        if (id <= 0) {
            synchronized (mNodeSeq) {
                if (mNodeSeq.get() <= 0) {
                    mNodeSeq.set(1);
                }
                id = mNodeSeq.getAndIncrement();
            }
        }
        return id;
    }

    private Element resolveTarget(Element el, String sel) {
        if (el == null) {
            return null;
        }
        if (sel == null || sel.isEmpty()) {
            return el;
        }
        return el.select(sel).first();
    }

    private String attrOf(Element el, String sel, String attr) {
        Element target = resolveTarget(el, sel);
        if (target == null) {
            return null;
        }
        return emptyToNull(target.attr(attr));
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private void trimNodeMap() {
        long now = System.currentTimeMillis();
        mNodeMap.entrySet().removeIf(e -> now - e.getValue().lastAccess > NODE_LIFETIME_MS);
        int size = mNodeMap.size();
        if (size <= MAX_NODE_COUNT) {
            return;
        }
        int removeCount = Math.min(NODE_TRIM_BATCH, size - MAX_NODE_COUNT);
        List<Map.Entry<Integer, NodeEntry>> entries = new ArrayList<>(mNodeMap.entrySet());
        entries.sort(Comparator.comparingLong(e -> e.getValue().lastAccess));
        for (int i = 0; i < removeCount && i < entries.size(); i++) {
            Map.Entry<Integer, NodeEntry> entry = entries.get(i);
            mNodeMap.remove(entry.getKey(), entry.getValue());
        }
    }

    private String handleBase64(String argsJson) throws JSONException {
        JSONObject o = new JSONObject(argsJson);
        String op = o.optString("op");
        String data = o.optString("data");
        return switch (op) {
            case "encode" -> quoteStr(SourceCodec.base64Encode(data));
            case "decode" -> quoteStr(SourceCodec.base64Decode(data));
            case "url" -> quoteStr(SourceCodec.base64UrlDecode(data));
            default -> "null";
        };
    }

    private String handleAesCbc(String argsJson) throws JSONException {
        JSONObject o = new JSONObject(argsJson);
        String value = o.optString("value");
        String key = o.optString("key");
        boolean ivPrefix = o.optBoolean("ivPrefix", false);
        String result;
        if (ivPrefix) {
            result = SourceCodec.aesCbcDecryptWithIvPrefix(value, key);
        } else {
            result = SourceCodec.aesCbcDecrypt(value, key, o.optString("iv"));
        }
        return quoteStr(result);
    }

    private String handleState(String argsJson) throws JSONException {
        JSONObject o = new JSONObject(argsJson);
        String op = o.optString("op");
        int type = o.optInt("type", -1);
        String key = o.optString("key");
        String fullKey = "state_" + type + "_" + key;
        if ("set".equals(op)) {
            Object value = o.opt("value");
            mState.put(fullKey, toJsonValue(value));
            return "null";
        }
        String value = mState.get(fullKey);
        return value == null ? "null" : value;
    }

    private SharedPreferences prefs() {
        return App.getAppContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private String handleSetting(String argsJson) throws JSONException {
        JSONObject o = new JSONObject(argsJson);
        String op = o.optString("op");
        int type = o.optInt("type", -1);
        String key = o.optString("key");
        String fullKey = "setting_" + type + "_" + key;
        SharedPreferences sp = prefs();
        if ("set".equals(op)) {
            Object value = o.opt("value");
            sp.edit().putString(fullKey, toJsonValue(value)).apply();
            return "null";
        }
        String value = sp.getString(fullKey, null);
        return value == null ? "null" : value;
    }

    private String handleLogin(String argsJson) throws JSONException {
        JSONObject o = new JSONObject(argsJson);
        String op = o.optString("op");
        int type = o.optInt("type", -1);
        String fullKey = "login_" + type;
        SharedPreferences sp = prefs();
        if ("set".equals(op)) {
            Object value = o.opt("value");
            sp.edit().putString(fullKey, toJsonValue(value)).apply();
            return "null";
        }
        if ("clear".equals(op)) {
            sp.edit().remove(fullKey).apply();
            return new JSONObject().put("success", true).toString();
        }
        String value = sp.getString(fullKey, null);
        return value == null ? "null" : value;
    }

    private String handleFetch(String argsJson) throws Exception {
        JSONObject o = new JSONObject(argsJson);
        String url = o.optString("url").trim();
        if (url.isEmpty()) {
            return "null";
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e(TAG, "fetch() called on main thread");
            HintUtils.showToast(App.getAppContext(), "fetch() called on main thread");
            return new JSONObject().put("error", "Network request not allowed on main thread").toString();
        }
        String method = o.optString("method", "GET").trim().toUpperCase(Locale.ROOT);

        Request.Builder builder = new Request.Builder().url(url);

        // 解析用户自定义 headers，使用小写键去重（保留最后出现的原始名称和值）
        Map<String, Map.Entry<String, String>> userHeaders = new LinkedHashMap<>();
        JSONObject headers = o.optJSONObject("headers");
        if (headers != null) {
            Iterator<String> iter = headers.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                if (key == null || key.isEmpty()) continue;
                String value = headers.optString(key);
                userHeaders.put(key.toLowerCase(Locale.ROOT),
                        new AbstractMap.SimpleEntry<>(key, value));
            }
        }

        // 检查用户是否提供了 User-Agent（忽略大小写）
        boolean hasUserAgent = userHeaders.containsKey("user-agent");
        if (!hasUserAgent) {
            builder.header("User-Agent", getDefaultUserAgent());
        }

        // 添加用户自定义头（已去重，且保留原始大小写）
        for (Map.Entry<String, Map.Entry<String, String>> entry : userHeaders.entrySet()) {
            String originalKey = entry.getValue().getKey();
            String value = entry.getValue().getValue();
            try {
                builder.header(originalKey, value);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid HTTP header: " + originalKey, e);
            }
        }

        String body = o.isNull("body") ? null : o.optString("body");
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            String contentType = o.optString("contentType", "application/x-www-form-urlencoded");
            if (body == null) {
                body = "";
            }
            MediaType mediaType = MediaType.parse(contentType);
            RequestBody requestBody = RequestBody.create(body, mediaType);
            builder.method(method, requestBody);
        } else if ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method)) {
            builder.method(method, null);
        } else {
            return new JSONObject().put("error", "Unsupported HTTP method: " + method).toString();
        }

        OkHttpClient client = App.getHttpClient().newBuilder()
                .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        Request request = builder.build();
        try (Response response = client.newCall(request).execute()) {
            JSONObject out = new JSONObject();
            out.put("status", response.code());
            JSONObject responseHeaders = new JSONObject();
            for (String key : response.headers().names()) {
                responseHeaders.put(key, response.header(key));
            }
            out.put("headers", responseHeaders);
            JSONArray setCookie = new JSONArray();
            for (String cookie : response.headers("Set-Cookie")) {
                setCookie.put(cookie);
            }
            out.put("setCookie", setCookie);
            ResponseBody responseBody = response.body();
            long contentLength = responseBody.contentLength();
            if (contentLength > MAX_RESPONSE_BYTES) {
                return new JSONObject().put("status", response.code()).put("error", "Response too large").toString();
            }
            String responseText = responseBody.string();
            if (responseText.length() > MAX_HTML_LENGTH) {
                return new JSONObject().put("status", response.code()).put("error", "Response too large").toString();
            }
            out.put("body", responseText);
            return out.toString();
        }
    }

    private String getDefaultUserAgent() {
        String ua = DEFAULT_USER_AGENT;
        if (ua != null) {
            return ua;
        }
        synchronized (JsHost.class) {
            ua = DEFAULT_USER_AGENT;
            if (ua == null) {
                ua = WebSettings.getDefaultUserAgent(App.getAppContext());
                if (ua == null || ua.isEmpty()) {
                    ua = "Mozilla/5.0";
                }
                DEFAULT_USER_AGENT = ua;
            }
        }
        return ua;
    }

    public String getLogin(int type) {
        return prefs().getString("login_" + type, null);
    }

    public void setLogin(int type, String json) {
        prefs().edit().putString("login_" + type, json).apply();
    }

    public void clearLogin(int type) {
        prefs().edit().remove("login_" + type).apply();
    }

    public String getSetting(int type, String key) {
        return prefs().getString("setting_" + type + "_" + key, null);
    }

    public void setSetting(int type, String key, String value) {
        prefs().edit().putString("setting_" + type + "_" + key, value).apply();
    }

    private String arg(String argsJson, String name) {
        try {
            if (argsJson == null || argsJson.isEmpty()) {
                return null;
            }
            return new JSONObject(argsJson).optString(name, null);
        } catch (JSONException e) {
            return null;
        }
    }

    private String toJsonValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "null";
        }
        // 修复：字符串需要加双引号，以便 JSON.parse 可正确解析
        if (value instanceof String) {
            return JSONObject.quote((String) value);
        }
        try {
            Object wrapped = JSONObject.wrap(value);
            return wrapped == null ? "null" : wrapped.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert value to JSON", e);
            return JSONObject.quote(String.valueOf(value));
        }
    }

    private String quoteStr(String value) {
        return value == null ? "null" : JSONObject.quote(value);
    }

    private static class NodeEntry {
        final Element element;
        volatile long lastAccess;

        NodeEntry(Element element) {
            this.element = element;
            this.lastAccess = System.currentTimeMillis();
        }
    }
}