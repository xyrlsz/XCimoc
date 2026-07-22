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
    // ========== 参数（可调） ==========
    private static final int MAX_SCROLL = 512; // 最多滚动次数
    private static final int SAME_LIMIT = 5; // 高度连续不变次数
    private static final int SCROLL_DELAY = 30; // 滚动间隔（ms）
    /** 总超时时间：120 秒后强制完成，防止永久阻塞 */
    private static final long TOTAL_TIMEOUT_MS = 120_000;
    // ========== 内存缓存 ==========
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5分钟有效，下拉刷新时主动清除
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

    /** 滚动停止后，等待动态内容加载的稳定期（ms）后重新检查高度 */
    private static final long STABILIZE_WAIT_MS = 500;

    private final String url;
    private final Headers headers;
    private final PublishSubject<String> htmlSubject = PublishSubject.create();
    private WebView webView;
    private String UA = "";
    /** 构造函数中查到缓存时，直接存下来，跳过 WebView 创建 */
    private String cachedResult = null;
    /** 防止重复调用 onPageFinished 导致多个 autoScroll 并发运行 */
    private volatile boolean scrollGuard = false;

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
        // WebView 缓存：关闭 WebView 自身缓存，完全由我们的 sHtmlCache 控制内存缓存
        // 避免断网加载到错误页面后，WebView 内部缓存了错误页面，重连后仍返回错误结果
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        // JS 桥接：注入 Android 接口，使 JS 可直接回调 Java，避免轮询
        webView.addJavascriptInterface(new JsBridge(), "XCimoc");

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
     * 只等 DOM ready 一次，并防止重复启动 autoScroll
     */
    private void waitForDomReady() {
        webView.evaluateJavascript("(function(){return document.readyState})()", value -> {
            if (value != null && value.contains("complete")) {
                // 用 scrollGuard 防止 onPageFinished 多次触发导致并发滚动
                if (scrollGuard) {
                    return;
                }
                scrollGuard = true;

                // 给 JS 一点时间
                if (url.contains(CopyMHWeb.website) && url.contains("/comic/") && !url.contains("/chapter/")) {
                    String jsCode = "javascript:(function() { " +
                            "var btns = document.getElementsByClassName('next-all'); " +
                            "for(var i = 0; i < btns.length; i++) { " +
                            "   btns[i].click(); " +
                            "} " +
                            "})()";

                    webView.evaluateJavascript(jsCode, s -> {
                        new Handler(Looper.getMainLooper()).postDelayed(this::startJsAutoScroll, 500);
                    });
                } else {
                    new Handler(Looper.getMainLooper()).postDelayed(this::startJsAutoScroll, 300);
                }
            } else if (!scrollGuard) {
                // DOM 未完成加载，继续轮询（如果还没开始滚动）
                new Handler(Looper.getMainLooper()).postDelayed(this::waitForDomReady, 50);
            }
        });
    }

    /**
     * 在 JS 内完成滚动，规避 Java↔JS 桥接的每步开销，大幅提升速度
     */
    private void startJsAutoScroll() {
        if (emitted) {
            return;
        }
        String js = "(function(){"
                + "var MAX=" + MAX_SCROLL + ",SAME=" + SAME_LIMIT + ",STEP=" + 1000 + ",DELAY=" + 30 + ",STABILIZE=" + STABILIZE_WAIT_MS + ";"
                + "var lastH=0,same=0,count=0,observer=null;"
                + "function scroll(){"
                + "if(count>=MAX){done();return}"
                + "var h=document.body.scrollHeight;"
                + "var rem=h-(window.innerHeight+(window.pageYOffset||document.documentElement.scrollTop));"
                + "if(rem<=100){stabilize();return}"
                + "if(h===lastH){same++}else{same=0;lastH=h}"
                + "if(same>=SAME){stabilize();return}"
                + "count++;window.scrollBy(0,STEP);setTimeout(scroll,DELAY)"
                + "}"
                + "function stabilize(){"
                + "setTimeout(function(){"
                + "var h2=document.body.scrollHeight;"
                + "if(h2>lastH+50){lastH=h2;same=0;count=Math.max(0,count-5);scroll()}"
                + "else{waitForIdle()}"
                + "},STABILIZE)"
                + "}"
                + "function waitForIdle(){"
                + "// 注册 MutationObserver 监听 DOM 变化"
                + "observer=new MutationObserver(function(){"
                + "// DOM 有变化，重置空闲计时器"
                + "clearTimeout(window._idleTimer);"
                + "window._idleTimer=setTimeout(function(){"
                + "observer.disconnect();"
                + "observer=null;"
                + "done()"
                + "},2000)"
                + "});"
                + "observer.observe(document.body,{childList:true,subtree:true,attributes:false,characterData:false});"
                + "// 同时检查网络是否空闲"
                + "checkNetworkIdle()"
                + "}"
                + "function checkNetworkIdle(){"
                + "if(window.performance&&window.performance.getEntriesByType){"
                + "var entries=window.performance.getEntriesByType('resource');"
                + "var pending=0;"
                + "for(var i=0;i<entries.length;i++){"
                + "if(entries[i].responseEnd===0||entries[i].responseEnd===undefined){pending++}"
                + "}"
                + "if(pending>0){"
                + "setTimeout(checkNetworkIdle,500);"
                + "return"
                + "}"
                + "}"
                + "// 网络空闲，启动空闲计时器"
                + "if(!window._idleTimer){"
                + "window._idleTimer=setTimeout(function(){"
                + "if(observer){observer.disconnect();observer=null}"
                + "done()"
                + "},2000)"
                + "}"
                + "}"
                + "function done(){"
                + "if(observer){observer.disconnect();observer=null}"
                + "clearTimeout(window._idleTimer);"
                + "try{XCimoc.onScrollDone()}catch(e){}"
                + "}"
                + "scroll()"
                + "})()";
        webView.evaluateJavascript(js, null);
    }

    /**
     * 获取 HTML
     */
    private void getPageHtml() {
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

    /**
     * JS 桥接接口 — 滚动完成后由 JS 直接回调，避免轮询
     */
    private class JsBridge {
        @android.webkit.JavascriptInterface
        @SuppressWarnings("unused")
        public void onScrollDone() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!emitted) {
                    getPageHtml();
                }
            });
        }
    }

    /**
     * 清除指定 URL 的内存缓存，用于下拉刷新时强制重新获取
     */
    public static void clearCache(String url) {
        sHtmlCache.remove(url);
    }
}