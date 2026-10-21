package com.xyrlsz.xcimocob.parser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.StringUtils;

import org.json.JSONObject;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import okhttp3.Headers;


public class WebParser {
    // 参数（可调）
    private static final int MAX_SCROLL = 512; // 最多滚动次数
    private static final int SAME_LIMIT = 3; // 高度连续不变次数
    private static final int SCROLL_DELAY = 50; // 出错重试间隔
    private static final int BOTTOM_WAIT = 200; // 滚动到底后等待内容渲染的时间
    /**
     * 总超时时间：120 秒后强制完成，防止永久阻塞
     */
    private static final long TOTAL_TIMEOUT_MS = 120_000;

    /**
     * Cloudflare 挑战轮询间隔（毫秒）
     */
    private static final long CLOUDFLARE_POLL_MS = 500;

    /**
     * 检测当前页面是否处于 Cloudflare 挑战页。
     * 返回：'0' 非挑战页；'1' 纯 JS 挑战（可自动通过）；'2' 交互式验证 / 被拦截（需人工）。
     */
    private static final String CF_DETECT_JS = "(function(){"
            + "try{"
            + "var t=(document.title||'').toLowerCase();"
            + "var h=document.documentElement.outerHTML||'';"
            + "var u=location.href||'';"
            + "var isCf=(t.indexOf('just a moment')!==-1)"
            + "||(t.indexOf('attention required')!==-1)"
            + "||(typeof window._cf_chl_opt!=='undefined')"
            + "||(h.indexOf('challenge-platform')!==-1)"
            + "||(h.indexOf('cf-chl')!==-1)"
            + "||(h.indexOf('cf_chl_opt')!==-1)"
            + "||(u.indexOf('/cdn-cgi/challenge-platform/')!==-1);"
            + "if(!isCf){return '0';}"
            + "var interactive=(t.indexOf('attention required')!==-1)"
            + "||(h.indexOf('turnstile')!==-1)"
            + "||(h.indexOf('hcaptcha')!==-1)"
            + "||(h.indexOf('cf_captcha')!==-1)"
            + "||(typeof window.turnstile!=='undefined');"
            + "return interactive?'2':'1';"
            + "}catch(e){return '0';}"
            + "})()";

    private final String url;
    private final Headers headers;
    private final PublishSubject<String> htmlSubject = PublishSubject.create();
    private final Context mContext;
    private String UA = "";
    /**
     * 是否自动向下滑动页面以触发懒加载，默认开启
     */
    private final boolean autoScroll;
    /**
     * DOM 就绪后注入执行的自定义 JS，可为 null
     */
    private final String injectJs;
    private final String waitForSelector;
    private final long waitForSelectorTimeoutMs;
    /**
     * 是否处理 Cloudflare 认证（默认开启，仅当检测到挑战页时生效）
     */
    private final boolean handleCloudflare;
    /**
     * Cloudflare JS 挑战等待通过的超时（毫秒）
     */
    private final long cloudflareTimeoutMs;
    /**
     * 是否允许交互式 Cloudflare 验证（把 WebView 挂到前台供用户手动完成）
     */
    private final boolean interactiveChallenge;
    /**
     * 所属漫画源名称，用于 Cloudflare 认证触发时的提示消息
     */
    private final String sourceTitle;
    /**
     * 每个实例独立的 WebView，不再共享静态实例
     */
    private WebView mWebView;
    // 滚动控制
    private int lastHeight = 0;
    private int sameCount = 0;
    private int scrollCount = 0;

    private int errTimes = 0;
    private volatile boolean emitted = false;
    private volatile boolean destroyed = false;

    // DOM / 流程控制
    private boolean domWaiting = false;
    private boolean cloudflareWaiting = false;
    private long cloudflareStartTime = 0;
    private ViewGroup mChallengeContainer;
    private boolean cfNotified = false;
    private boolean disable = false;

    public WebParser(Context context, String url, Headers headers) {
        this(context, url, headers, "", new WebParserConfig());
    }

    public WebParser(Context context, String url, Headers headers, String UA) {
        this(context, url, headers, UA, new WebParserConfig());
    }

    public WebParser(Context context, String url, Headers headers, String UA, WebParserConfig config) {
        this.url = url;
        this.headers = headers;
        this.UA = UA;
        this.autoScroll = config.isAutoScroll();
        this.injectJs = config.getInjectJs();
        this.waitForSelector = config.getWaitForSelector();
        this.waitForSelectorTimeoutMs = config.getWaitForSelectorTimeoutMs();
        this.handleCloudflare = config.isHandleCloudflare();
        this.cloudflareTimeoutMs = config.getCloudflareTimeoutMs();
        this.interactiveChallenge = config.isInteractiveChallenge();
        this.sourceTitle = config.getSourceTitle();
        this.mContext = context.getApplicationContext();
        boolean onlyWifi =
                App.getPreferenceManager().getBoolean(PreferenceManager.PREF_OTHER_CONNECT_ONLY_WIFI, false);
        WifiManager manager_wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        disable = !manager_wifi.isWifiEnabled() && onlyWifi;
        // 在 UI 线程创建 WebView 并开始加载
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (disable) {
                    emitError(new Exception("仅允许 Wi-Fi 网络，但当前 Wi-Fi 未开启"));
                    return;
                }
                mWebView = new WebView(mContext);
                initWebViewSettings(mWebView);
                initWebViewForRequest();
            } catch (Exception e) {
                Log.e("WebParser", "WebView init error", e);
                emitError(e);
            }
        });
    }

    /**
     * 安全发射结果，防止重复发射
     */
    private void emitResult(String html) {
        if (!emitted) {
            emitted = true;
            htmlSubject.onNext(html);
            htmlSubject.onComplete();
            destroyWebView();
        }
    }

    private void emitError(Throwable e) {
        if (!emitted) {
            emitted = true;
            htmlSubject.onError(e);
            destroyWebView();
        }
    }

    /**
     * 销毁 WebView（必须在 UI 线程执行）
     */
    private void destroyWebView() {
        if (destroyed) return;
        destroyed = true;
        hideChallengeOverlay();
        if (mWebView != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    mWebView.stopLoading();
                    mWebView.removeAllViews();
                    mWebView.destroy();
                } catch (Exception e) {
                    Log.w("WebParser", "WebView destroy error", e);
                }
                mWebView = null;
            });
        }
        disable = false;
    }

    /**
     * WebView 初始化设置
     */
    @SuppressLint("SetJavaScriptEnabled")
    private static void initWebViewSettings(WebView wv) {
        wv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wv.setWebChromeClient(new WebChromeClient());
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setDomStorageEnabled(true);
        wv.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
    }

    /**
     * 加载 URL 前的配置
     */
    private void initWebViewForRequest() {
        if (destroyed) return;
        if (!StringUtils.isEmpty(UA)) {
            mWebView.getSettings().setUserAgentString(UA);
        }

        // 使用 TreeMap 并指定忽略大小写的比较器
        Map<String, String> headersMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            for (String key : headers.names()) {
                headersMap.put(key, headers.get(key));
            }
            if (StringUtils.isEmpty(UA) && headersMap.containsKey("User-Agent")) {
                mWebView.getSettings().setUserAgentString(headersMap.get("User-Agent"));
            }
        }

        mWebView.setWebViewClient(new WebViewClient() {
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

        mWebView.loadUrl(url, headersMap);
    }

    /**
     * 等待 DOM ready（同时只允许一个等待循环）
     */
    private void waitForDomReady() {
        if (destroyed || mWebView == null || domWaiting) return;
        domWaiting = true;
        waitForDomReadyLoop();
    }

    private void waitForDomReadyLoop() {
        if (destroyed || mWebView == null) {
            domWaiting = false;
            return;
        }

        mWebView.evaluateJavascript("(function(){return document.readyState})()", value -> {
            if (destroyed || mWebView == null) {
                domWaiting = false;
                return;
            }

            if (value != null && value.contains("complete")) {
                domWaiting = false;
                onDomReady();
            } else {
                new Handler(Looper.getMainLooper()).postDelayed(this::waitForDomReadyLoop, 100);
            }
        });
    }

    /**
     * DOM 就绪后的入口。若正在等待 Cloudflare 挑战通过，则跳过渲染。
     */
    private void onDomReady() {
        if (destroyed || mWebView == null) return;
        if (cloudflareWaiting) return;
        startWithCloudflareCheck();
    }

    /**
     * 先检测是否命中 Cloudflare 挑战页，再决定是否开始渲染。
     */
    private void startWithCloudflareCheck() {
        if (!handleCloudflare) {
            startRender();
            return;
        }

        mWebView.evaluateJavascript(CF_DETECT_JS, value -> {
            if (destroyed || mWebView == null) return;

            int cf = parseCfResult(value);
            if (cf == 0) {
                startRender();
            } else if (cf == 2) {
                if (interactiveChallenge) {
                    // 交互式验证：把 WebView 挂到前台由用户手动点击完成
                    notifyCloudflare("「" + sourceDisplay() + "」需要完成 Cloudflare 真人验证");
                    cloudflareWaiting = true;
                    cloudflareStartTime = System.currentTimeMillis();
                    showChallengeOverlay();
                    waitAndRecheckCloudflare();
                } else {
                    // 交互式验证（勾选验证码）或 Attention Required 拦截无法在后台 WebView 自动完成
                    emitError(new Exception("「" + sourceDisplay() + "」启用了 Cloudflare 交互式验证，无法自动解析"));
                }
            } else {
                // 纯 JS 挑战：页面 JS 会自动计算并通过，轮询等待
                notifyCloudflare("「" + sourceDisplay() + "」正在通过 Cloudflare 验证，请稍候");
                cloudflareWaiting = true;
                cloudflareStartTime = System.currentTimeMillis();
                waitAndRecheckCloudflare();
            }
        });
    }

    /**
     * 轮询等待 Cloudflare JS 挑战通过；挑战通过后（可能已重定向）重新等待 DOM 再渲染。
     */
    private void waitAndRecheckCloudflare() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (destroyed || mWebView == null) return;

            long elapsed = System.currentTimeMillis() - cloudflareStartTime;
            if (elapsed >= cloudflareTimeoutMs) {
                cloudflareWaiting = false;
                hideChallengeOverlay();
                emitError(new Exception("「" + sourceDisplay() + "」Cloudflare 验证超时"));
                return;
            }

            mWebView.evaluateJavascript(CF_DETECT_JS, value -> {
                if (destroyed || mWebView == null) return;

                int cf = parseCfResult(value);
                if (cf == 0) {
                    // 挑战通过：可能已重定向到目标页，重新等待 DOM 就绪后开始渲染
                    cloudflareWaiting = false;
                    hideChallengeOverlay();
                    waitForDomReady();
                } else if (cf == 2) {
                    if (interactiveChallenge) {
                        // 交互式验证：继续等待用户操作
                        waitAndRecheckCloudflare();
                    } else {
                        cloudflareWaiting = false;
                        hideChallengeOverlay();
                        emitError(new Exception("「" + sourceDisplay() + "」启用了 Cloudflare 交互式验证，无法自动解析"));
                    }
                } else {
                    waitAndRecheckCloudflare();
                }
            });
        }, CLOUDFLARE_POLL_MS);
    }

    /**
     * Cloudflare 认证触发时，用 Toast 提示是哪个漫画源（每个实例只提示一次）。
     */
    private void notifyCloudflare(String message) {
        if (cfNotified) return;
        cfNotified = true;
        try {
            HintUtils.showToast(mContext, message  );
        } catch (Exception ignored) {
        }
    }

    /**
     * 源名称显示，未配置时使用通用文案。
     */
    private String sourceDisplay() {
        return StringUtils.isEmpty(sourceTitle) ? "该漫画源" : sourceTitle;
    }

    /**
     * 将 WebView 挂载到当前 Activity 上，供用户完成交互式 Cloudflare 验证。
     * 无可见 Activity 时跳过（仍会轮询，若实际是 JS 挑战也能自动通过）。
     */
    private void showChallengeOverlay() {
        if (mChallengeContainer != null) return;
        Activity activity = App.getCurrentActivity();
        if (activity == null || mWebView == null) return;

        FrameLayout container = new FrameLayout(mContext);
        container.setBackgroundColor(0xFFFFFFFF);

        LinearLayout topBar = new LinearLayout(mContext);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(6), dp(8), dp(6));
        topBar.setBackgroundColor(0xFFF0F0F0);

        TextView hint = new TextView(mContext);
        hint.setText("请完成 Cloudflare 真人验证，完成后将自动继续");
        hint.setTextSize(13);
        hint.setMaxLines(2);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(hint, hintLp);

        Button refresh = new Button(mContext);
        refresh.setText("刷新");
        refresh.setOnClickListener(v -> {
            // 离屏加载可能未渲染出 Turnstile 组件，刷新挑战页并重置超时计时
            cloudflareStartTime = System.currentTimeMillis();
            if (mWebView != null) {
                mWebView.reload();
            }
        });
        topBar.addView(refresh);

        Button cancel = new Button(mContext);
        cancel.setText("取消");
        cancel.setOnClickListener(v -> {
            hideChallengeOverlay();
            emitError(new Exception("已取消「" + sourceDisplay() + "」的 Cloudflare 验证"));
        });
        topBar.addView(cancel);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;
        container.addView(topBar, topLp);

        FrameLayout.LayoutParams webLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        webLp.topMargin = dp(44);
        container.addView(mWebView, webLp);

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(container, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        mChallengeContainer = container;
    }

    private void hideChallengeOverlay() {
        if (mChallengeContainer != null) {
            ViewGroup parent = (ViewGroup) mChallengeContainer.getParent();
            if (parent != null) {
                parent.removeView(mChallengeContainer);
            }
            mChallengeContainer = null;
        }
    }

    private int dp(float value) {
        return (int) (value * mContext.getResources().getDisplayMetrics().density + 0.5f);
    }

    private int parseCfResult(String value) {
        if (value == null) return 0;
        try {
            String v = value.replace("\"", "").trim();
            if ("1".equals(v)) return 1;
            if ("2".equals(v)) return 2;
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * 正式开始渲染：执行注入 JS / 自动滑动 / 抓取 HTML
     */
    private void startRender() {
        if (destroyed || mWebView == null) return;

        if (injectJs != null && !injectJs.isEmpty()) {
            // 源配置了 JS 注入：先执行注入脚本，再根据 autoScroll 决定是否继续滑动
            mWebView.evaluateJavascript(injectJs, s -> {
                if (destroyed || mWebView == null) return;
                if (autoScroll) {
                    new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, 500);
                } else {
                    getPageHtml();
                }
            });
        } else if (autoScroll) {
            new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, 300);
        } else {
            // 关闭自动滑动：DOM 就绪后直接抓取当前 HTML
            getPageHtml();
        }
    }

    /**
     * 核心：智能滚动——逐步向下滚动触发懒加载（保证每段内容都经过视口），
     * 滚动到底后等待内容渲染，若高度继续增长则接着滚，直到高度连续不变或达到上限
     */
    private void autoScroll() {
        if (destroyed || mWebView == null) return;

        String js = "(function(){"
                + "var h = document.body.scrollHeight;"
                + "var ch = document.body.clientHeight;"
                + "var st = document.body.scrollTop || document.documentElement.scrollTop;"
                + "window.scrollBy(0, 500);"
                + "return h + ',' + ch + ',' + st;"
                + "})()";

        mWebView.evaluateJavascript(js, value -> {
            if (destroyed || mWebView == null) return;

            try {
                if (value == null)
                    return;

                String[] parts = value.replace("\"", "").split(",");
                double currentScrollHeight = Double.parseDouble(parts[0]);
                double clientHeight = Double.parseDouble(parts[1]);
                double currentScrollTop = Double.parseDouble(parts[2]);

                double distanceToBottom = currentScrollHeight - (currentScrollTop + clientHeight);

                // 记录内容高度是否仍在变化
                if ((int) currentScrollHeight == lastHeight) {
                    sameCount++;
                } else {
                    sameCount = 0;
                    lastHeight = (int) currentScrollHeight;
                }

                scrollCount++;

                // 高度连续不变或达到上限：加载完成
                if (sameCount >= SAME_LIMIT || scrollCount >= MAX_SCROLL) {
                    getPageHtml();
                    return;
                }

                if (distanceToBottom <= 100) {
                    // 已到当前底部：等待内容渲染，若高度继续增长则接着滚，否则多次确认后完成
                    new Handler(Looper.getMainLooper()).postDelayed(this::autoScroll, BOTTOM_WAIT);
                    return;
                }

                // 继续滚动，接近底部时放慢速度给懒加载留出时间
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
        if (destroyed || mWebView == null) return;

        if (waitForSelector == null || waitForSelector.trim().isEmpty()) {
            capturePageHtml(300);
        } else {
            waitForSelector(System.currentTimeMillis());
        }
    }

    private void waitForSelector(long startedAt) {
        if (destroyed || mWebView == null) return;

        String selector = JSONObject.quote(waitForSelector);
        String script = "(function(){try{return document.querySelectorAll("
                + selector + ").length}catch(e){return -1}})()";
        mWebView.evaluateJavascript(script, value -> {
            if (destroyed || mWebView == null) return;

            int count;
            try {
                count = Integer.parseInt(value == null ? "" : value.replace("\"", ""));
            } catch (NumberFormatException e) {
                emitError(new Exception("WebParser: failed to check selector: " + waitForSelector, e));
                return;
            }

            if (count > 0) {
                capturePageHtml(0);
            } else if (count < 0) {
                emitError(new Exception("WebParser: invalid selector: " + waitForSelector));
            } else if (System.currentTimeMillis() - startedAt >= waitForSelectorTimeoutMs) {
                emitError(new Exception("WebParser: timed out waiting for selector: " + waitForSelector));
            } else {
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> waitForSelector(startedAt), 250);
            }
        });
    }

    private void capturePageHtml(long delayMs) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (destroyed || mWebView == null) return;
            mWebView.evaluateJavascript(
                    "(function(){return document.documentElement.outerHTML})()", value -> {
                        if (destroyed || mWebView == null) return;

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
        }, delayMs);
    }

    /**
     * 获取 HTML 的 Observable
     */
    public Observable<String> getHtmlObservable() {
        return htmlSubject
                .timeout(TOTAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .firstElement()
                .toObservable();
    }
}