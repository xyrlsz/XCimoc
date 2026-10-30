package com.xyrlsz.xcimocob.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 让 OkHttp 请求复用 WebView 的 Cookie。
 *
 * <p>Cloudflare 的 {@code cf_clearance} 是 WebView 完成挑战后写入 {@link CookieManager} 的，
 * 如果后续 OkHttp 请求（页面解析、JS 源的 fetch、图片下载）不带这个 Cookie，
 * Cloudflare 就会再次下发挑战，表现为「老是需要重新验证」。
 *
 * <p>这里只做「读」同步（WebView → OkHttp），不回写响应里的 Set-Cookie：
 * JS 源（如 komiic、vomicmh）自己维护 Cookie 头，回写会污染其登录态。
 * 请求自身显式设置的 Cookie 优先于 WebView 中的同名 Cookie。
 */
public class WebViewCookieInterceptor implements Interceptor {

    private static final String TAG = "WebViewCookieInterceptor";

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String merged = mergeCookies(request.url(), request.header("Cookie"));
        if (merged != null && !merged.isEmpty()) {
            request = request.newBuilder().header("Cookie", merged).build();
        }
        return chain.proceed(request);
    }

    /**
     * 把 WebView 中该 URL 的 Cookie 与请求自带的 Cookie 合并，请求自带的同名 Cookie 优先。
     */
    @Nullable
    private static String mergeCookies(HttpUrl url, @Nullable String explicitCookie) {
        String webViewCookie;
        try {
            webViewCookie = CookieManager.getInstance().getCookie(url.toString());
        } catch (Exception e) {
            Log.w(TAG, "getCookie failed: " + url, e);
            return explicitCookie;
        }
        if (webViewCookie == null || webViewCookie.isEmpty()) {
            return explicitCookie;
        }

        Map<String, String> cookies = new LinkedHashMap<>();
        putCookies(cookies, webViewCookie);
        // 显式设置的 Cookie 覆盖 WebView 中的同名项
        putCookies(cookies, explicitCookie);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static void putCookies(Map<String, String> out, @Nullable String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) return;
        for (String pair : cookieHeader.split(";")) {
            String item = pair.trim();
            int eq = item.indexOf('=');
            if (eq <= 0) continue;
            out.put(item.substring(0, eq).trim(), item.substring(eq + 1).trim());
        }
    }

    /**
     * 强制把 CookieManager 内存中的 Cookie 落盘。进程被系统杀掉时，
     * 未落盘的 cf_clearance 会丢失，导致下次启动又要重新验证。
     * {@link CookieManager#flush()} 只允许在主线程调用，这里统一投递到主线程。
     */
    public static void flushAsync() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                CookieManager.getInstance().flush();
            } catch (Exception e) {
                Log.w(TAG, "flush failed", e);
            }
        });
    }
}
