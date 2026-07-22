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
    private static final int MAX_SCROLL = 768; // 最多滚动次数
    private static final int SAME_LIMIT = 10; // 高度连续不变次数（保守，避免误判）
    private static final int SCROLL_STEP = 600; // 每次滚动像素（适度放大，平衡速度与图片加载时间）
    private static final int SCROLL_DELAY_NEAR = 100; // 接近底部时的滚动间隔（ms）
    private static final int SCROLL_DELAY_FAR = 50; // 远离底部时的滚动间隔（ms）
    private static final int SCROLL_DELAY_ERR = 50; // 出错重试间隔（ms）
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
    private static final long STABILIZE_WAIT_MS = 800;
    /** 稳定期检测最多重试次数（已确认高度不变后，再等一轮复查，防止图片尚在下载中） */
    private static final int STABILIZE_RETRY_MAX = 2;
    /** 距离底部多少 px 视为"接近底部"，改用慢速滚动 */
    private static final int NEAR_BOTTOM_THRESHOLD = 800;
    /** 距离底部多少 px 视为"到达底部"，触发稳定期检测 */
    private static final int AT_BOTTOM_THRESHOLD = 80;

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

    /** 防止重复调用 onPageFinished 导致多个 autoScroll 并发运行 */
    private volatile boolean scrollGuard = false;

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
        // WebView 缓存：关闭 WebView 自身缓存，完全由我们的 sHtmlCache 控制内存缓存
        // 避免断网加载到错误页面后，WebView 内部缓存了错误页面，重连后仍返回错误结果
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);

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
                        new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, 300);
                    });
                } else {
                    new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, 200);
                }
            } else if (!scrollGuard) {
                // DOM 未完成加载，继续轮询（如果还没开始滚动）
                new Handler(Looper.getMainLooper()).postDelayed(this::waitForDomReady, 50);
            }
        });
    }

    /**
     * 核心：智能滚动，滚动停止后增加稳定期检测，确保动态内容加载完成。
     * 优化：增大步长到 SCROLL_STEP、缩短延迟，大幅减少 Java↔JS 桥接次数。
     */
    private void autoScroll() {
        // 如果已经发射过结果，不再继续滚动
        if (emitted) {
            return;
        }

        String js = "(function(){"
                + "var h=document.body.scrollHeight;"
                + "var ch=document.body.clientHeight;"
                + "var st=document.body.scrollTop||document.documentElement.scrollTop;"
                + "window.scrollBy(0," + SCROLL_STEP + ");"  // 步长放大，减少桥接调用
                + "return h+','+ch+','+st;"
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

                // 1. 到底检测
                if (distanceToBottom <= AT_BOTTOM_THRESHOLD) {
                    stabilizeAndFinish();
                    return;
                }

                // 2. 高度不变检测（防死循环）
                if (currentScrollHeight == lastHeight) {
                    sameCount++;
                } else {
                    sameCount = 0;
                    lastHeight = (int) currentScrollHeight;
                }

                scrollCount++;

                if (sameCount >= SAME_LIMIT || scrollCount >= MAX_SCROLL) {
                    stabilizeAndFinish();
                    return;
                }

                // 接近底部用慢速让内容有时间加载，远处快速通过
                int nextDelay = (distanceToBottom < NEAR_BOTTOM_THRESHOLD) ? SCROLL_DELAY_NEAR : SCROLL_DELAY_FAR;

                new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, nextDelay);

            } catch (Exception ignored) {
                if (errTimes <= 5) {
                    new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, SCROLL_DELAY_ERR);
                    errTimes++;
                } else {
                    emitError(new Exception("WebParser: autoScroll failed after retries"));
                }
            }
        });
    }

    /**
     * 稳定期检测：等待 STABILIZE_WAIT_MS 后重新检查页面高度。
     * 最多重试 STABILIZE_RETRY_MAX 次，每次等足稳定期再复查，
     * 防止因图片尚在下载中而误判为"加载完成"。
     */
    private void stabilizeAndFinish() {
        stabilizeAndFinish(0);
    }

    private void stabilizeAndFinish(int retryCount) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (emitted) {
                return;
            }
            String js = "(function(){"
                    + "var h = document.body.scrollHeight;"
                    + "var st = document.body.scrollTop || document.documentElement.scrollTop;"
                    + "return h + ',' + st;"
                    + "})()";
            webView.evaluateJavascript(js, value -> {
                try {
                    if (value == null || emitted) {
                        return;
                    }
                    String[] parts = value.replace("\"", "").split(",");
                    double newHeight = Double.parseDouble(parts[0]);

                    // 如果高度比停止时增加了，说明有动态内容刚加载出来，继续滚动
                    if (newHeight > lastHeight + 50) {
                        lastHeight = (int) newHeight;
                        sameCount = 0;
                        scrollCount = Math.max(0, scrollCount - 5);
                        new Handler(Looper.getMainLooper()).post(this::autoScroll);
                    } else if (retryCount < STABILIZE_RETRY_MAX) {
                        // 本轮稳定，但再等一轮复查（图片可能尚在下载，高度还未变化）
                        stabilizeAndFinish(retryCount + 1);
                    } else {
                        // 多轮复查均稳定，真正结束
                        getPageHtml();
                    }
                } catch (Exception e) {
                    // 稳定期检测失败，直接结束
                    getPageHtml();
                }
            });
        }, STABILIZE_WAIT_MS);
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
        }, 150); // 最后缓冲
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
     * 清除指定 URL 的内存缓存，用于下拉刷新时强制重新获取
     */
    public static void clearCache(String url) {
        sHtmlCache.remove(url);
    }
}