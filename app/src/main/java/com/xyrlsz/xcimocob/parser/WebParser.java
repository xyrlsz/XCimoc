package com.xyrlsz.xcimocob.parser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.xyrlsz.xcimocob.source.CopyMHWeb;
import com.xyrlsz.xcimocob.utils.StringUtils;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import okhttp3.Headers;


public class WebParser {
    // 参数（可调）
    private static final int MAX_SCROLL = 512; // 最多滚动次数
    private static final int SAME_LIMIT = 3; // 高度连续不变次数
    private static final int SCROLL_DELAY = 50;
    /** 总超时时间：120 秒后强制完成，防止永久阻塞 */
    private static final long TOTAL_TIMEOUT_MS = 120_000;
    // ========== 内存缓存 ==========
    private static final long CACHE_TTL_MS = 30 * 1000; // 30秒有效，与 OkHttp stale-while-revalidate 对齐
    private static final Map<String, CacheEntry> sHtmlCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final String html;
        final long timestamp;
        CacheEntry(String html) {
            this.html = html;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isValid() {
            return System.currentTimeMillis() - timestamp < CACHE_TTL_MS;
        }
    }
    // =============================

    private final String url;
    private final Headers headers;
    private final PublishSubject<String> htmlSubject = PublishSubject.create();
    private WebView webView;
    private String UA = "";
    /** 构造函数中查到缓存时，直接存下来，跳过 WebView 创建 */
    private String cachedResult = null;
    // 滚动控制
    private int lastHeight = 0;
    private int sameCount = 0;
    private int scrollCount = 0;

    private int errTimes = 0;
    private volatile boolean emitted = false;

    public WebParser(Context context, String url, Headers headers) {
        this(context, url, headers, "");
    }

    public WebParser(Context context, String url, Headers headers, String UA) {
        this.url = url;
        this.headers = headers;
        this.UA = UA;

        // 检查内存缓存，命中则直接返回，避免创建 WebView
        CacheEntry entry = sHtmlCache.get(url);
        if (entry != null && entry.isValid()) {
            cachedResult = entry.html;
            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                webView = new WebView(context);
                initWebView();
            } catch (Exception e) {
                Log.e("WebParser", "WebView init error", e);
                emitError(e);
            }
        });
    }

    /**
     * 安全发射结果，防止重复发射，并写入内存缓存
     */
    private void emitResult(String html) {
        if (!emitted) {
            emitted = true;
            // 写入缓存（主动清理过期条目）
            sHtmlCache.put(url, new CacheEntry(html));
            if (sHtmlCache.size() > 64) {
                sHtmlCache.entrySet().removeIf(e -> !e.getValue().isValid());
            }
            htmlSubject.onNext(html);
            htmlSubject.onComplete();
        }
    }

    private void emitError(Throwable e) {
        if (!emitted) {
            emitted = true;
            htmlSubject.onError(e);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                waitForDomReady();
            }
        });

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        // WebView 缓存：遵循 HTTP 标准缓存头，有缓存头才缓存，无缓存头走网络
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);

        if (!StringUtils.isEmpty(UA)) {
            webView.getSettings().setUserAgentString(UA);
        }
        // 使用 TreeMap 并指定忽略大小写的比较器 这样写就可以直接匹配到 "user-agent" 或 "User-Agent"
        Map<String, String> headersMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            for (String key : headers.names()) {
                headersMap.put(key, headers.get(key));
            }
            if (StringUtils.isEmpty(UA) && headersMap.containsKey("User-Agent")) {
                webView.getSettings().setUserAgentString(headersMap.get("User-Agent"));
            }
        }

        webView.loadUrl(url, headersMap);
    }

    /**
     * 只等 DOM ready 一次
     */
    private void waitForDomReady() {
        webView.evaluateJavascript("(function(){return document.readyState})()", value -> {
            if (value != null && value.contains("complete")) {
                // 给 JS 一点时间
                if (url.contains(CopyMHWeb.website) && url.contains("/comic/") && !url.contains("/chapter/")) {
                    // 修改重点在这里：使用 for 循环遍历所有找到的按钮
                    String jsCode = "javascript:(function() { " +
                            "var btns = document.getElementsByClassName('next-all'); " +
                            "for(var i = 0; i < btns.length; i++) { " +
                            "   btns[i].click(); " +
                            "} " +
                            "})()";

                    webView.evaluateJavascript(jsCode, s -> {
                        // 点击操作执行完毕后，延迟执行自动滚动
                        new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, 500);
                    });
                } else {
                    // 非目标页面也保持原有的自动滚动逻辑
                    new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, 300);
                }
            } else {
                // DOM 未完成加载，继续轮询
                new Handler(Looper.getMainLooper()).postDelayed(this::waitForDomReady, 100);
            }
        });
    }

    /**
     * 核心：智能滚动
     */
    private void autoScroll() {
        String js = "(function(){"
                + "var h = document.body.scrollHeight;"
                + "var ch = document.body.clientHeight;"
                + "var st = document.body.scrollTop || document.documentElement.scrollTop;"
                + "window.scrollBy(0, 500);" + // 执行滚动
                // 返回格式： "总高度,可视高度,当前位置"
                "return h + ',' + ch + ',' + st;"
                + "})()";

        webView.evaluateJavascript(js, value -> {
            try {
                if (value == null)
                    return;

                String[] parts = value.replace("\"", "").split(",");
                double currentScrollHeight = Double.parseDouble(parts[0]);
                double clientHeight = Double.parseDouble(parts[1]);
                double currentScrollTop = Double.parseDouble(parts[2]);

                double distanceToBottom = currentScrollHeight - (currentScrollTop + clientHeight);

                // 1. 如果剩余距离已经很小（例如小于 100px），说明到底了，直接结束
                if (distanceToBottom <= 100) {
                    getPageHtml();
                    return;
                }

                // 2. 原有的防死循环逻辑（高度不变检测）
                if (currentScrollHeight == lastHeight) {
                    sameCount++;
                } else {
                    sameCount = 0;
                    lastHeight = (int) currentScrollHeight;
                }

                scrollCount++;

                if (sameCount >= SAME_LIMIT || scrollCount >= MAX_SCROLL) {
                    getPageHtml();
                    return;
                }

                int nextDelay = (distanceToBottom < 1000) ? 200 : 50;

                new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, nextDelay);

            } catch (Exception ignored) {
                if (errTimes <= 5) {
                    new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, SCROLL_DELAY);
                    errTimes++;
                } else {
                    emitError(new Exception("WebParser: autoScroll failed after retries"));
                }
            }
        });
    }

    /**
     * 获取 HTML
     */
    private void getPageHtml() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            webView.evaluateJavascript(
                    "(function(){return document.documentElement.outerHTML})()", value -> {
                        if (value != null) {
                            String result = value.replace("\\u003C", "<")
                                    .replace("\\u003E", ">")
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"")
                                    .replace("\\'", "'")
                                    .replace("\\t", "    ")
                                    .replace("\\\\/", "\\/");

                            emitResult(result);
                        } else {
                            emitError(new Exception("WebParser: failed to get HTML"));
                        }
                    });
        }, 300); // 最后缓冲
    }

    /**
     * 获取 HTML 的 Observable（优先从内存缓存返回）
     */
    public Observable<String> getHtmlObservable() {
        // 构造函数中已命中缓存，直接返回
        if (cachedResult != null) {
            return Observable.just(cachedResult);
        }
        return htmlSubject
                .timeout(TOTAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .firstElement()
                .toObservable();
    }
}