package com.xyrlsz.xcimocob.network;

import android.webkit.WebSettings;

import androidx.annotation.NonNull;

import com.xyrlsz.xcimocob.App;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 为缺少 User-Agent 的请求补上与 WebView 一致的默认 UA。
 *
 * <p>Cloudflare 的 {@code cf_clearance} 与申请挑战时的 User-Agent 绑定：WebView 用系统默认 UA
 * 通过挑战后，如果 OkHttp 请求不带 UA（OkHttp 默认不发送 User-Agent），Cookie 会被判为无效而重新挑战。
 */
public class DefaultUserAgentInterceptor implements Interceptor {

    private static volatile String sDefaultUserAgent;

    /**
     * 系统 WebView 的默认 User-Agent（与未设置 {@code setUserAgentString} 的 WebView 保持一致）。
     */
    public static String getDefaultUserAgent() {
        String ua = sDefaultUserAgent;
        if (ua == null) {
            synchronized (DefaultUserAgentInterceptor.class) {
                ua = sDefaultUserAgent;
                if (ua == null) {
                    try {
                        ua = WebSettings.getDefaultUserAgent(App.getAppContext());
                    } catch (Exception ignored) {
                    }
                    if (ua == null || ua.isEmpty()) {
                        ua = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
                    }
                    sDefaultUserAgent = ua;
                }
            }
        }
        return ua;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (request.header("User-Agent") != null) {
            return chain.proceed(request);
        }
        return chain.proceed(request.newBuilder()
                .header("User-Agent", getDefaultUserAgent())
                .build());
    }
}
