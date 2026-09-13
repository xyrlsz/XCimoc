package com.xyrlsz.xcimocob.source.js;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.xyrlsz.quickjs.QuickJSEngine;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.JsSource;
import com.xyrlsz.xcimocob.parser.Category;
import com.xyrlsz.xcimocob.parser.MangaCategory;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.parser.SearchIterator;
import com.xyrlsz.xcimocob.parser.UrlFilter;
import com.xyrlsz.xcimocob.parser.UrlFilterWithCidQueryKey;
import com.xyrlsz.xcimocob.parser.WebParserConfig;
import com.xyrlsz.xcimocob.utils.BinStreamUtils;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 动态 JS 漫画源解析器。把 {@link MangaParser} 的全部接口桥接到一段 JS 源脚本
 * （存储于 {@link JsSource#getScript()}）。每次调用创建独立 {@link QuickJSEngine}，
 * 评估「SDK + 源脚本」后调用对应的全局 JS 函数，再把 JSON 结果转回 Java 模型。
 * <p>
 * 因引擎按调用创建，解析器自身无 JS 跨调用状态；脚本需要跨调用保存的数据
 * 应使用宿主提供的 {@code setState/getState}、{@code setSetting/getSetting}、
 * {@code setLogin/getLogin}（按源 type 隔离，见 {@link JsHost}）。
 */
public class JsMangaParser extends MangaParser {

    private static final String SDK;
    private static final int HEADER_CACHE_CAP = 64;

    static {
        QuickJSEngine.setHostBridge(JsHost.INSTANCE);
        String sdk;
        try {
            sdk = new String(
                    BinStreamUtils.readAllBytesCompat(
                            App.getAppContext().getAssets().open("source_sdk.js")),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            sdk = "";
        }
        SDK = sdk;
    }

    private final JsSource mSource;
    /**
     * 当前线程的会话引擎（单次详情流程内复用）。非会话调用时 {@code withEngine} 仍每次新建引擎，
     * 保持源脚本无跨调用状态、解析器无状态、可多线程共享的既有语义。
     */
    private final ThreadLocal<QuickJSEngine> mSessionEngine = new ThreadLocal<>();
    /**
     * 最近一次图片请求后缓存的请求头（用于"referer 随图片页变化"的源）。
     */
    private volatile Headers mCachedHeader;
    /**
     * JS 源 init() 后台调度去重标记：保证每个解析器实例只调度一次。
     * 用于拷贝漫画等需要在脚本加载时探测域名/分类的源——init() 内部调用 fetch()，
     * 必须在后台线程执行（主线程会被 JsHost.handleFetch 拒绝）。
     */
    private final java.util.concurrent.atomic.AtomicBoolean mInited =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /**
     * 分类（懒加载缓存）。
     */
    private volatile Category mCategory;
    private volatile boolean mCategoryLoaded = false;
    /**
     * WebParser 配置是否已从 SOURCE.webConfig 应用。
     */
    private volatile boolean mConfigApplied = false;

    public JsMangaParser(JsSource source) {
        mSource = source;
        mTitle = source.getTitle();
        applyConfigSourceTitle(mTitle);
        buildFilters();
        initInBackground();
    }

    /**
     * 把 JS 返回的 JSON 字符串还原为普通字符串（null/undefined → null）。
     */
    private static String unquote(String json) {
        if (json == null || json.isEmpty() || "null".equals(json)) return null;
        try {
            Object v = new org.json.JSONTokener(json).nextValue();
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * 判断 JS 返回的 JSON 字符串是否为 null/undefined/空。
     * 用于在 new JSONObject(callJs(...)) 前拦截，避免
     * "Value null of type org.json.JSONObject$1 cannot be converted to JSONObject"。
     */
    private static boolean isNullJson(String json) {
        return json == null || json.isEmpty() || "null".equals(json);
    }

    /* ---------------- 引擎 ---------------- */

    /**
     * 读取 JSON 字段：JSON null 或键缺失时返回 null。
     * 不能用 optString(key, null)——当值为 JSON null 时它返回字符串 "null" 而非默认值，
     * 会导致搜索/分类列表更新时间显示 "null"。
     */
    private static String jstr(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key);
    }

    public JsSource getJsSource() {
        return mSource;
    }

    public int getType() {
        return mSource.getType();
    }

    private QuickJSEngine createEngine() {
        QuickJSEngine engine = new QuickJSEngine();
        try {
            engine.setGlobal("__SOURCE_TYPE", String.valueOf(mSource.getType()));
            String result = engine.evaluate(SDK + "\n" + mSource.getScript());
            if (result == null || result.isEmpty()) {
                android.util.Log.e("JsParser", "eval returned empty for type "
                        + mSource.getType() + " '" + mTitle + "', sdkLen=" + SDK.length()
                        + ", scriptLen=" + (mSource.getScript() == null ? -1 : mSource.getScript().length()));
            }
        } catch (Throwable t) {
            // 脚本/引擎异常：后续 callFunction 均返回 "null"，各方法自然回退
            android.util.Log.e("JsParser", "createEngine failed for type "
                    + mSource.getType() + " '" + mTitle + "': " + t);
        }
        return engine;
    }

    /**
     * 开启一次引擎会话：在会话期间（同一线程串行）复用同一引擎，使详情加载的多次解析调用
     * （getInfoRequest → parseInfo → getChapterRequest → parseChapter）只需编译一次体积较大的
     * SDK+脚本，显著降低详情加载耗时。会话结束（close）即销毁引擎，跨漫画/任务互不污染。
     * <p>
     * 必须配合 {@code try-with-resources} 使用，且只能在单个线程内串行调用解析方法
     * （不得跨线程、不得并发复用）。这是唯一安全的引擎复用方式：复用范围严格限定在单次
     * 流程内，不引入跨任务的状态污染。
     */
    public java.io.Closeable openSession() {
        QuickJSEngine engine = createEngine();
        final QuickJSEngine prev = mSessionEngine.get();
        mSessionEngine.set(engine);
        return () -> {
            try {
                engine.close();
            } catch (Throwable ignore) {
            }
            mSessionEngine.set(prev);
        };
    }

    private <T> T withEngine(EngineAction<T> action) {
        QuickJSEngine session = mSessionEngine.get();
        if (session != null && !session.isClosed()) {
            // 会话中：复用会话引擎（不 close，由会话统一释放）
            try {
                return action.run(session);
            } catch (Throwable t) {
                Log.w("JsParser", "withEngine(session) failed for type "
                        + mSource.getType() + ": " + t);
                return null;
            }
        }

        if (session != null && session.isClosed()) {
            mSessionEngine.remove();
            Log.w("JsParser", "cleared stale closed session for type "
                    + mSource.getType());
        }

        QuickJSEngine engine = null;
        try {
            engine = createEngine();
            return action.run(engine);
        } catch (Throwable t) {
            Log.w("JsParser", "withEngine failed for type "
                    + mSource.getType() + ": " + t);
            return null;
        } finally {
            if (engine != null) {
                try {
                    engine.close();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private String callJs(QuickJSEngine engine, String name, String argsJson) {
        return engine.callFunction(name, argsJson);
    }

    /**
     * 后台调度 JS 源的 {@code init()}（如拷贝漫画的域名/分类探测）。
     * <p>
     * init() 内部可能调用 fetch()，主线程会被 {@link JsHost#handleFetch(String)} 拒绝并报
     * "Network request not allowed on main thread"。这里用独立线程执行，并通过
     * {@link #mInited} 保证每个解析器实例只调度一次。
     * <p>
     * 失败不影响后续解析：{@code withEngine} 会捕获 Throwable，且 init 仅用于更新持久化设置，
     * 不改变解析器自身状态；探测完成后的设置会在下次引擎创建时被读取。
     */
    public void initInBackground() {
        if (!mInited.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                withEngine(e -> {
                    if (e.hasFunction("init")) {
                        callJs(e, "init", "[]");
                    }
                    return null;
                });
            } catch (Throwable ignore) {
            }
        }, "JsSource-init-" + mSource.getType()).start();
    }

    /* ---------------- 过滤器 / 元数据 ---------------- */

    private JSONObject requestToJson(Request req) {
        if (req == null) return null;
        JSONObject o = new JSONObject();
        try {
            o.put("url", req.url().toString());
            JSONObject h = new JSONObject();
            for (String k : req.headers().names()) {
                h.put(k, req.headers().get(k));
            }
            o.put("headers", h);
        } catch (JSONException ignore) {
        }
        return o;
    }

    private void buildFilters() {
        filter.clear();
        // cid 正则：对齐 Java 源默认 (\\d+)，可用 SOURCE.cidRegex 覆盖
        String regex = mSource.getCidRegex();
        if (regex == null || regex.isEmpty()) {
            regex = "(\\d+)";
        }
        // cid 若在 query 参数里（如 zaimanhua 的 ?id=），用 UrlFilterWithCidQueryKey
        String query = mSource.getCidQuery();
        for (String h : parseHosts()) {
            if (query != null && !query.isEmpty()) {
                filter.add(new UrlFilterWithCidQueryKey(h, query));
            } else {
                filter.add(new UrlFilter(h, regex));
            }
        }
    }

    /**
     * 解析源 host 列表：hosts 可能存为 JSON 数组字符串（["a.com","b.com"]）或逗号分隔；
     * 为空时回退到 baseUrl 的 host。
     */
    private List<String> parseHosts() {
        List<String> hostList = new ArrayList<>();
        String hosts = mSource.getHosts();
        if (hosts != null && !hosts.isEmpty()) {
            if (hosts.trim().startsWith("[")) {
                try {
                    JSONArray arr = new JSONArray(hosts);
                    for (int i = 0; i < arr.length(); i++) {
                        String t = arr.optString(i).trim();
                        if (!t.isEmpty()) hostList.add(t);
                    }
                } catch (Exception ignore) {
                }
            }
            if (hostList.isEmpty()) {
                for (String h : hosts.split(",")) {
                    String t = h.trim().replaceAll("[\\[\\]\"]", "");
                    if (!t.isEmpty()) hostList.add(t);
                }
            }
        }
        if (hostList.isEmpty() && mSource.getBaseUrl() != null && !mSource.getBaseUrl().isEmpty()) {
            try {
                hostList.add(new URL(mSource.getBaseUrl()).getHost());
            } catch (Exception ignore) {
            }
        }
        return hostList;
    }

    @Override
    public String getUrl(String cid) {
        return withEngine(e -> {
            if (e.hasFunction("getUrl")) {
                JSONArray args = new JSONArray().put(cid);
                String s = unquote(callJs(e, "getUrl", args.toString()));
                return s != null ? s : super.getUrl(cid);
            }
            return super.getUrl(cid);
        });
    }

    /* ---------------- 请求头 ---------------- */

    private Headers headersFromJson(JSONObject o) {
        if (o == null) return null;
        Headers.Builder b = new Headers.Builder();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            String v = o.optString(k);
            if (k != null && !StringUtils.isEmpty(v)) {
                try {
                    b.add(k, v);
                } catch (Exception ignore) {
                }
            }
        }
        return b.build();
    }

    private Request buildRequest(JSONObject desc) {
        if (desc == null) return null;
        String url = desc.optString("url");
        if (StringUtils.isEmpty(url)) return null;
        Request.Builder b = new Request.Builder().url(url);
        JSONObject headers = desc.optJSONObject("headers");
        if (headers != null) {
            Iterator<String> it = headers.keys();
            while (it.hasNext()) {
                String k = it.next();
                String v = headers.optString(k);
                if (k != null && !StringUtils.isEmpty(v)) {
                    try {
                        b.header(k, v);
                    } catch (Exception ignore) {
                    }
                }
            }
        }
        String method = desc.optString("method", "GET").toUpperCase(Locale.ROOT);
        if ("POST".equals(method)) {
            String body = desc.optString("body");
            String ctype = desc.optString("contentType", "application/x-www-form-urlencoded");
            try {
                b.post(RequestBody.create(body, MediaType.parse(ctype)));
            } catch (Exception ignore) {
            }
        }
        return b.build();
    }

    @Override
    public Headers getHeader() {
        Headers cached = mCachedHeader;
        if (cached != null) return cached;
        return withEngine(e -> {
            if (!e.hasFunction("getHeader")) return null;
            // getHeader() 返回 null 表示「无特殊请求头」，是合法契约；直接返回 null，
            // 避免 new JSONObject("null") 抛 JSONException（JSONObject.NULL）。
            String s = callJs(e, "getHeader", "[]");
            if (s == null || "null".equals(s)) return null;
            JSONObject o = new JSONObject(s);
            Headers h = headersFromJson(o);
            mCachedHeader = h;
            return h;
        });
    }

    /* ---------------- 搜索 ---------------- */

    @Override
    public Request getSearchRequest(String keyword, int page) {
        return withEngine(e -> {
            if (!e.hasFunction("getSearchRequest")) return null;
            JSONArray args = new JSONArray().put(keyword).put(page);
            String s = callJs(e, "getSearchRequest", args.toString());
            if (isNullJson(s)) return null;
            return buildRequest(new JSONObject(s));
        });
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        return withEngine(e -> {
            if (!e.hasFunction("parseSearch")) return null;
            JSONArray args = new JSONArray().put(html).put(page);
            String s = callJs(e, "parseSearch", args.toString());
            if (isNullJson(s)) return null;
            JSONArray arr = new JSONArray(s);
            return new JsSearchIterator(arr, page, mSource.getType());
        });
    }

    @Override
    public Request getInfoRequest(String cid) {
        return withEngine(e -> {
            if (!e.hasFunction("getInfoRequest")) return null;
            JSONArray args = new JSONArray().put(cid);
            String s = callJs(e, "getInfoRequest", args.toString());
            if (isNullJson(s)) return null;
            return buildRequest(new JSONObject(s));
        });
    }

    @Override
    public Comic parseInfo(String html, Comic comic) {
        return withEngine(e -> {
            if (!e.hasFunction("parseInfo")) return comic;
            JSONArray args = new JSONArray().put(html).put(comic.getCid());
            String s = callJs(e, "parseInfo", args.toString());
            if (isNullJson(s)) return comic;
            JSONObject o = new JSONObject(s);
            String title = o.optString("title");
            String cover = o.optString("cover");
            String update = o.optString("update");
            String intro = o.optString("intro");
            String author = o.optString("author");
            boolean finish = o.optBoolean("finish", false);
            if (StringUtils.isEmpty(title) || "null".equals(title)) title = null;
            if (StringUtils.isEmpty(cover) || "null".equals(cover)) cover = null;
            if (StringUtils.isEmpty(update) || "null".equals(update)) update = null;
            if (StringUtils.isEmpty(intro) || "null".equals(intro)) intro = null;
            if (StringUtils.isEmpty(author) || "null".equals(author)) author = null;
            comic.setInfo(title, cover, update, intro, author, finish);
            // 若 parseInfo 一并返回了章节列表，缓存到 comic.note 供 parseChapter 消费
            if (o.has("chapters") && !o.isNull("chapters")) {
                comic.note = o.optJSONArray("chapters");
            }
            return comic;
        });
    }

    /* ---------------- 详情 ---------------- */

    @Override
    public Request getChapterRequest(String html, String cid) {
        return withEngine(e -> {
            if (!e.hasFunction("getChapterRequest")) return null;
            JSONArray args = new JSONArray().put(html).put(cid);
            String s = callJs(e, "getChapterRequest", args.toString());
            if (isNullJson(s)) return null;
            return buildRequest(new JSONObject(s));
        });
    }

    @Override
    public List<Chapter> parseChapter(String html) {
        return parseChapter(html, null, null);
    }

    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) {
        // 优先消费 parseInfo 缓存的章节
        if (comic != null && comic.note instanceof JSONArray cached) {
            comic.note = null;
            return toChapters(cached, sourceComic);
        }
        return withEngine(e -> {
            if (!e.hasFunction("parseChapter")) return new LinkedList<>();
            JSONArray args;
            if (comic != null) {
                JSONObject comicJson = new JSONObject();
                comicJson.put("cid", comic.getCid());
                comicJson.put("title", comic.getTitle());
                args = new JSONArray().put(html).put(comicJson);
            } else {
                args = new JSONArray().put(html);
            }
            String fn = "parseChapter";
            String s = callJs(e, fn, args.toString());
            if (isNullJson(s)) return new LinkedList<>();
            JSONArray arr = new JSONArray(s);
            return toChapters(arr, sourceComic);
        });
    }

    private List<Chapter> toChapters(JSONArray arr, Long sourceComic) {
        List<Chapter> list = new LinkedList<>();
        if (arr == null) return list;
        long sc = sourceComic == null ? 0L : sourceComic;
        int i = 0;
        HashSet<Pair<String, String>> chapterHashSet = new HashSet<>();
        for (int n = 0; n < arr.length(); n++) {
            JSONObject o = arr.optJSONObject(n);
            if (o == null) continue;
            String title = o.optString("title");
            String path = o.optString("path");
            if (StringUtils.isEmpty(path)) continue;
            if (StringUtils.isEmpty(title) || "null".equals(title)) title = null;
            // JS 源可通过 group 字段返回章节分组（详情页按 sourceGroup 分组显示）
            String group = o.optString("group");
            Chapter ch = new Chapter(IdCreator.createChapterId(sc, i++), sc, title, path);
            ch.setSourceGroup(StringUtils.isEmpty(group) ? "" : group);
            Pair<String, String> tmp = new Pair<>(title, path);
            if (!chapterHashSet.contains(tmp)) {
                chapterHashSet.add(tmp);
                list.add(ch);
            }
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        return withEngine(e -> {
            if (!e.hasFunction("getImagesRequest")) return null;
            JSONArray args = new JSONArray().put(cid).put(path);
            String s = callJs(e, "getImagesRequest", args.toString());
            if (isNullJson(s)) return null;
            Request req = buildRequest(new JSONObject(s));
            // 同一引擎内捕获 getImagesRequest 之后的 getHeader（referer 已更新）
            if (e.hasFunction("getHeader")) {
                try {
                    String hs = callJs(e, "getHeader", "[]");
                    if (isNullJson(hs)) return req;
                    JSONObject ho = new JSONObject(hs);
                    Headers h = headersFromJson(ho);
                    if (h != null) mCachedHeader = h;
                } catch (Exception ignore) {
                }
            }
            return req;
        });
    }

    @Override
    public List<ImageUrl> parseImages(String html) {
        return parseImages(html, null);
    }

    /* ---------------- 图片 ---------------- */

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) {
        return withEngine(e -> {
            if (!e.hasFunction("parseImages")) return new ArrayList<>();
            JSONArray args;
            if (chapter != null) {
                JSONObject ch = new JSONObject();
                ch.put("cid", chapter.getPath());
                ch.put("path", chapter.getPath());
                ch.put("id", chapter.getId());
                args = new JSONArray().put(html).put(ch);
            } else {
                args = new JSONArray().put(html);
            }
            String s = callJs(e, "parseImages", args.toString());
            if (isNullJson(s)) return new ArrayList<>();
            JSONArray arr = new JSONArray(s);
            List<ImageUrl> list = new ArrayList<>();
            Headers defaultHeader = getHeader();
            long cc = chapter != null ? chapter.getId() : 0L;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                long id = IdCreator.createImageId(cc, i + 1);
                boolean lazy = o.optBoolean("lazy", false);
                String url = o.optString("url");
                if (StringUtils.isEmpty(url) || "null".equals(url)) url = null;
                // 支持每个图片项自带 headers（如图片防盗链 Referer），否则用默认 getHeader()
                Headers h = defaultHeader;
                JSONObject itemHeaders = o.optJSONObject("headers");
                if (itemHeaders != null) {
                    Headers nh = headersFromJson(itemHeaders);
                    if (nh != null) h = nh;
                }
                JSONArray urls = o.optJSONArray("urls");
                if (urls != null && urls.length() > 0) {
                    String[] ua = new String[urls.length()];
                    for (int k = 0; k < urls.length(); k++) {
                        ua[k] = urls.optString(k);
                    }
                    list.add(new ImageUrl(id, cc, i + 1, ua, null, ImageUrl.STATE_NULL, lazy, h));
                } else if (url != null) {
                    list.add(new ImageUrl(id, cc, i + 1, url, lazy, h));
                }
            }
            return list;
        });
    }

    @Override
    public Request getLazyRequest(String url) {
        return withEngine(e -> {
            if (!e.hasFunction("getLazyRequest")) return null;
            JSONArray args = new JSONArray().put(url);
            String s = callJs(e, "getLazyRequest", args.toString());
            if (isNullJson(s)) return null;
            return buildRequest(new JSONObject(s));
        });
    }

    @Override
    public String parseLazy(String html, String url) {
        return withEngine(e -> {
            if (!e.hasFunction("parseLazy")) return null;
            JSONArray args = new JSONArray().put(html).put(url);
            return unquote(callJs(e, "parseLazy", args.toString()));
        });
    }

    @Override
    public Request getCheckRequest(String cid) {
        return withEngine(e -> {
            if (!e.hasFunction("getCheckRequest")) return getInfoRequest(cid);
            JSONArray args = new JSONArray().put(cid);
            String s = callJs(e, "getCheckRequest", args.toString());
            if (isNullJson(s)) return getInfoRequest(cid);
            return buildRequest(new JSONObject(s));
        });
    }

    @Override
    public String parseCheck(String html) {
        return withEngine(e -> {
            if (!e.hasFunction("parseCheck")) return null;
            JSONArray args = new JSONArray().put(html);
            return unquote(callJs(e, "parseCheck", args.toString()));
        });
    }

    /* ---------------- 更新检查 ---------------- */

    @Override
    public Category getCategory() {
        if (!mCategoryLoaded) {
            synchronized (this) {
                if (!mCategoryLoaded) {
                    mCategory = buildCategory();
                    mCategoryLoaded = true;
                }
            }
        }
        return mCategory;
    }

    private Category buildCategory() {
        return withEngine(e -> {
            // 未实现 getCategories 的源返回 null，表示不支持分类；
            // CategoryFragment#updateSourceList 据此只列出「真正实现分类且启用」的源，
            // 不再把无分类能力的源也塞进分类页。
            if (!e.hasFunction("getCategories")) return null;
            try {
                JSONObject o = new JSONObject(callJs(e, "getCategories", "[]"));
                return new JsCategory(o);
            } catch (Exception ex) {
                return null;
            }
        });
    }

    /* ---------------- 分类 ---------------- */

    @Override
    public Request getCategoryRequest(String format, int page) {
        // 用当前页填充分页占位符 {page}/{offset}/{limit}；分类占位符已在 getFormat 填充。
        // pageSize 来自 getCategories().pageSize（默认 20）。
        int pageSize = 20;
        if (mCategory instanceof JsCategory) {
            pageSize = ((JsCategory) mCategory).getPageSize();
        }
        String fmt = format;
        if (fmt != null) {
            fmt = fmt.replace("{page}", String.valueOf(page))
                    .replace("{offset}", String.valueOf((page - 1) * pageSize))
                    .replace("{limit}", String.valueOf(pageSize));
        }
        final String rendered = fmt;
        Request r = withEngine(e -> {
            if (!e.hasFunction("getCategoryRequest")) return null;
            JSONArray args = new JSONArray().put(rendered).put(page);
            String s = callJs(e, "getCategoryRequest", args.toString());
            if (isNullJson(s)) return null;
            return buildRequest(new JSONObject(s));
        });
        if (r != null) return r;
        // 未实现 getCategoryRequest 的模板型源：直接用渲染后的 URL 发 GET。
        // 不能用 super.getCategoryRequest（String.format），它会按 % 解释 URL，破坏编码。
        if (rendered == null || rendered.isEmpty()) return null;
        return new Request.Builder().url(rendered).build();
    }

    @Override
    public List<Comic> parseCategory(String html, int page) {
        return withEngine(e -> {
            if (!e.hasFunction("parseCategory")) return null;
            JSONArray args = new JSONArray().put(html).put(page);
            String s = callJs(e, "parseCategory", args.toString());
            if (isNullJson(s)) return null;
            JSONArray arr = new JSONArray(s);
            List<Comic> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                list.add(new Comic(mSource.getType(), jstr(o, "cid"),
                        jstr(o, "title"), jstr(o, "cover"),
                        jstr(o, "update"), jstr(o, "author")));
            }
            return list;
        });
    }

    private void applyConfigSourceTitle(String title) {
        getSearchConfig().setSourceTitle(title);
        getInfoConfig().setSourceTitle(title);
        getChapterConfig().setSourceTitle(title);
        getImagesConfig().setSourceTitle(title);
        getImagesLazyConfig().setSourceTitle(title);
    }

    private synchronized void ensureConfig() {
        if (mConfigApplied) return;
        mConfigApplied = true;
        withEngine(e -> {
            String src = e.getGlobalJson("SOURCE");
            if (src == null || "null".equals(src)) return null;
            JSONObject source = new JSONObject(src);
            JSONObject wc = source.optJSONObject("webConfig");
            if (wc != null) {
                apply(wc.optJSONObject("search"), getSearchConfig());
                apply(wc.optJSONObject("info"), getInfoConfig());
                apply(wc.optJSONObject("chapter"), getChapterConfig());
                apply(wc.optJSONObject("images"), getImagesConfig());
                apply(wc.optJSONObject("imagesLazy"), getImagesLazyConfig());
            }
            return null;
        });
    }

    private void apply(JSONObject o, WebParserConfig cfg) {
        if (o == null) return;
        cfg.setUseWebParser(o.optBoolean("useWebParser", cfg.isUseWebParser()));
        if (o.has("autoScroll")) cfg.setAutoScroll(o.optBoolean("autoScroll"));
        if (o.has("injectJs")) cfg.setInjectJs(o.optString("injectJs"));
        if (o.has("handleCloudflare")) cfg.setHandleCloudflare(o.optBoolean("handleCloudflare"));
        if (o.has("cloudflareTimeoutMs"))
            cfg.setCloudflareTimeoutMs(o.optLong("cloudflareTimeoutMs"));
        if (o.has("interactiveChallenge"))
            cfg.setInteractiveChallenge(o.optBoolean("interactiveChallenge"));
    }

    /* ---------------- WebParser 配置（SOURCE.webConfig） ---------------- */

    @Override
    public WebParserConfig getSearchConfig() {
        ensureConfig();
        return super.getSearchConfig();
    }

    @Override
    public WebParserConfig getInfoConfig() {
        ensureConfig();
        return super.getInfoConfig();
    }

    @Override
    public WebParserConfig getChapterConfig() {
        ensureConfig();
        return super.getChapterConfig();
    }

    @Override
    public WebParserConfig getImagesConfig() {
        ensureConfig();
        return super.getImagesConfig();
    }

    @Override
    public WebParserConfig getImagesLazyConfig() {
        ensureConfig();
        return super.getImagesLazyConfig();
    }

    /**
     * 脚本是否声明了登录能力。
     * 优先使用 JsSource 表缓存的 hasLogin（validateScript 时计算），避免每次都跑 JS；
     * 仅当缓存未就绪（metaReady=false）时回退到实时评估。
     */
    public boolean hasLogin() {
        if (mSource.isMetaReady()) {
            return mSource.isHasLogin();
        }
        return Boolean.TRUE.equals(withEngine(e -> e.hasFunction("login") || e.hasFunction("getLoginState")));
    }

    /**
     * 是否已登录（优先脚本 getLoginState，其次宿主保存的登录态）。
     */
    public JSONObject getLoginState() {
        JSONObject fromJs = withEngine(e -> {
            if (e.hasFunction("getLoginState")) {
                return new JSONObject(callJs(e, "getLoginState", "[]"));
            }
            return null;
        });
        if (fromJs != null) return fromJs;
        String stored = JsHost.INSTANCE.getLogin(mSource.getType());
        if (stored == null) return new JSONObject();
        try {
            return new JSONObject(stored);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /**
     * 调用脚本 login(params) 执行登录。
     * 注意：脚本内部已通过 SDK 的 setLogin() 持久化登录态（cookie/token），
     * 这里不能再把成功结果写回宿主登录态，否则会用 {"success":true,...} 覆盖已存的
     * cookie/token，导致 getLoginState 永远显示未登录、请求也不带登录头。
     */
    public JSONObject login(JSONObject params) {
        return withEngine(e -> {
            if (!e.hasFunction("login")) return null;
            JSONArray args = new JSONArray().put(params);
            try {
                return new JSONObject(callJs(e, "login", args.toString()));
            } catch (Exception ex) {
                return null;
            }
        });
    }

    /* ---------------- 登录 / 设置（供 UI 调用） ---------------- */

    /**
     * 脚本声明的注册链接（JS getRegisterUrl() 返回的 URL 字符串），未声明或返回空则返回 null。
     */
    public String getRegisterUrl() {
        return withEngine(e -> {
            if (!e.hasFunction("getRegisterUrl")) return null;
            String raw = callJs(e, "getRegisterUrl", "[]");
            if (raw == null || "null".equals(raw) || raw.isEmpty()) return null;
            try {
                return new JSONObject("{\"url\":" + raw + "}").optString("url", null);
            } catch (JSONException ex) {
                return null;
            }
        });
    }

    /**
     * 调用脚本 logout() 并清除宿主登录态。
     */
    public void logout() {
        withEngine(e -> {
            if (e.hasFunction("logout")) {
                callJs(e, "logout", "[]");
            }
            return null;
        });
        JsHost.INSTANCE.clearLogin(mSource.getType());
    }

    /**
     * 脚本声明的设置项（JS getSettings() 返回的字段描述数组），无则返回空。
     * 优先使用 JsSource 表缓存的 settingsJson（validateScript 时计算），避免每次都跑 JS；
     * 仅当缓存未就绪或为空时回退到实时评估。
     */
    public JSONArray getSettings() {
        if (mSource.isMetaReady()) {
            String cached = mSource.getSettingsJson();
            if (cached != null && !cached.isEmpty() && !"null".equals(cached)) {
                try {
                    return new JSONArray(cached);
                } catch (JSONException e) {
                    // 缓存损坏，回退到实时评估
                }
            }
            return new JSONArray();
        }
        return withEngine(e -> {
            if (!e.hasFunction("getSettings")) return new JSONArray();
            return new JSONArray(callJs(e, "getSettings", "[]"));
        });
    }

    /**
     * 读取某设置值（宿主持久化）。
     */
    public String getSetting(String key) {
        return JsHost.INSTANCE.getSetting(mSource.getType(), key);
    }

    /**
     * 写入某设置值（宿主持久化）。
     */
    public void setSetting(String key, String value) {
        JsHost.INSTANCE.setSetting(mSource.getType(), key, value);
    }

    /**
     * 调用脚本 onSettingsAction(key) 执行设置按钮动作（如签到），返回 {success, message}。
     */
    public JSONObject settingsCallback(String key) {
        return withEngine(e -> {
            if (!e.hasFunction("onSettingsAction")) return null;
            JSONArray args = new JSONArray().put(key);
            try {
                return new JSONObject(callJs(e, "onSettingsAction", args.toString()));
            } catch (Exception ex) {
                return null;
            }
        });
    }

    @Override
    public boolean isHere(Uri uri) {
        return super.isHere(uri);
    }

    @Override
    public String getComicId(Uri uri) {
        return super.getComicId(uri);
    }

    private interface EngineAction<T> {
        T run(QuickJSEngine engine) throws Exception;
    }

    /* ---------------- 其它 ---------------- */

    private static class JsSearchIterator implements SearchIterator {
        private final JSONArray arr;
        private final int type;
        private int index = 0;

        JsSearchIterator(JSONArray arr, int page, int type) {
            this.arr = arr;
            this.type = type;
        }

        @Override
        public boolean empty() {
            return arr == null || arr.length() == 0;
        }

        @Override
        public boolean hasNext() {
            return arr != null && index < arr.length();
        }

        @Override
        public Comic next() {
            JSONObject o = arr.optJSONObject(index++);
            if (o == null) return null;
            return new Comic(type, jstr(o, "cid"), jstr(o, "title"),
                    jstr(o, "cover"), jstr(o, "update"),
                    jstr(o, "author"));
        }
    }

    /**
     * 由 JS getCategories() 构建的分类。
     */
    private static class JsCategory extends MangaCategory {
        private static final String[] ATTR_KEYS = {"subject", "area", "reader", "year", "progress", "order"};
        private final boolean composite;
        /**
         * getCategories() 返回的 format 模板（含 {subject}/{area}/{reader}/{year}/{progress}/{order}/{page}/{offset}/{limit} 占位符）。
         */
        private final String formatTemplate;
        /**
         * getCategories().pageSize，用于填充 {offset}/{limit}（默认 20）。
         */
        private final int pageSize;
        /**
         * 「全部」哨兵：值为空串时用于替换成后端接受的“全部”值。
         * 可为单个字符串（作用于所有维度）或按维度 map（{subject:..,area:..,...}）。
         * 为 null 表示后端接受空串=全部，保持原样。
         */
        private final String allValue;
        private final Map<String, String> allValueMap;
        private final List<Pair<String, String>> subject = new ArrayList<>();
        private final List<Pair<String, String>> area = new ArrayList<>();
        private final List<Pair<String, String>> reader = new ArrayList<>();
        private final List<Pair<String, String>> progress = new ArrayList<>();
        private final List<Pair<String, String>> year = new ArrayList<>();
        private final List<Pair<String, String>> order = new ArrayList<>();
        private final boolean[] has = new boolean[6];

        JsCategory(JSONObject o) {
            composite = o.optBoolean("composite", false);
            String fmt = o.optString("format");
            formatTemplate = (StringUtils.isEmpty(fmt) || "null".equals(fmt)) ? null : fmt;
            int ps = o.optInt("pageSize", 20);
            pageSize = (ps <= 0) ? 20 : ps;
            subject.addAll(parsePairs(o.optJSONArray("subject")));
            area.addAll(parsePairs(o.optJSONArray("area")));
            reader.addAll(parsePairs(o.optJSONArray("reader")));
            progress.addAll(parsePairs(o.optJSONArray("progress")));
            year.addAll(parsePairs(o.optJSONArray("year")));
            order.addAll(parsePairs(o.optJSONArray("order")));
            // 解析「全部」哨兵：字符串（作用于所有维度）或对象（按维度）
            String av = null;
            Map<String, String> avMap = null;
            if (o.has("allValue") && !o.isNull("allValue")) {
                Object raw = o.opt("allValue");
                if (raw instanceof JSONObject avObj) {
                    avMap = new HashMap<>();
                    Iterator<String> it = avObj.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        String v = avObj.optString(k);
                        if (!v.isEmpty() && !"null".equals(v)) {
                            avMap.put(k, v);
                        }
                    }
                } else {
                    assert raw != null;
                    String s = raw.toString();
                    av = (s.isEmpty() || "null".equals(s)) ? null : s;
                }
            }
            allValue = av;
            allValueMap = avMap;
            has[Category.CATEGORY_SUBJECT] = !subject.isEmpty();
            has[Category.CATEGORY_AREA] = !area.isEmpty();
            has[Category.CATEGORY_READER] = !reader.isEmpty();
            has[Category.CATEGORY_PROGRESS] = !progress.isEmpty();
            has[Category.CATEGORY_YEAR] = !year.isEmpty();
            has[Category.CATEGORY_ORDER] = !order.isEmpty();
        }

        private static List<Pair<String, String>> parsePairs(JSONArray arr) {
            List<Pair<String, String>> list = new ArrayList<>();
            if (arr == null) return list;
            for (int i = 0; i < arr.length(); i++) {
                Object el = arr.opt(i);
                if (el instanceof JSONObject o) {
                    // 对象格式：{ title: "全部", value: "" }（源脚本统一用此格式）
                    String title = catStr(o, "title");
                    if (title == null) title = catStr(o, "name");
                    // value 允许空串 ''（表示「全部」），仅 JSON null/缺失才视为无效，
                    // 不能用 catStr（会把空串折叠成 null，导致「全部」选项被整条丢弃）
                    String value = catVal(o, "value");
                    if (value == null) value = catVal(o, "id");
                    if (title != null && !title.isEmpty() && value != null) {
                        list.add(Pair.create(title, value));
                    }
                } else {
                    // 数组格式：[title, value]
                    JSONArray pair = arr.optJSONArray(i);
                    if (pair != null && pair.length() >= 2) {
                        list.add(Pair.create(pair.optString(0), pair.optString(1)));
                    }
                }
            }
            return list;
        }

        /**
         * 读取对象字段，JSON null/缺失返回 null。
         */
        private static String catStr(JSONObject o, String key) {
            if (o.has(key) && !o.isNull(key)) {
                String s = o.optString(key);
                return (StringUtils.isEmpty(s) || "null".equals(s)) ? null : s;
            }
            return null;
        }

        /**
         * 读取分类项 value：JSON null/缺失返回 null，空串 ''（「全部」标记）原样保留。
         */
        private static String catVal(JSONObject o, String key) {
            if (o.has(key) && !o.isNull(key)) {
                String s = o.optString(key);
                return "null".equals(s) ? null : s;
            }
            return null;
        }

        int getPageSize() {
            return pageSize;
        }

        /**
         * 用所选值填充 format 模板中的分类占位符；{page}/{offset}/{limit} 留给 getCategoryRequest 按页填充。
         * 选中「全部」（值空/未选）时，若声明了 allValue 哨兵则替换为哨兵值，否则保持空串。
         */
        @Override
        public String getFormat(String... args) {
            String[] r = resolveArgs(args);
            if (formatTemplate != null) {
                String s = formatTemplate;
                s = s.replace("{subject}", r[0]);
                s = s.replace("{area}", r[1]);
                s = s.replace("{reader}", r[2]);
                s = s.replace("{year}", r[3]);
                s = s.replace("{progress}", r[4]);
                s = s.replace("{order}", r[5]);
                return s;
            }
            return super.getFormat(r);
        }

        /**
         * 解析单个维度实际值：非空选中原样返回；空/未选（「全部」）按 allValue 哨兵替换，
         * 未声明哨兵则保持空串。
         */
        private String resolveValue(int idx, String selected) {
            if (selected != null && !selected.isEmpty()) return selected;
            if (allValue != null) return allValue;
            if (allValueMap != null) {
                String v = allValueMap.get(ATTR_KEYS[idx]);
                return v != null ? v : "";
            }
            return "";
        }

        private String[] resolveArgs(String... args) {
            String[] r = new String[6];
            for (int i = 0; i < 6; i++) {
                String sel = (args != null && i < args.length) ? args[i] : null;
                r[i] = resolveValue(i, sel);
            }
            return r;
        }

        @Override
        public boolean isComposite() {
            return composite;
        }

        @Override
        protected List<Pair<String, String>> getSubject() {
            return subject;
        }

        @Override
        public boolean hasAttribute(int attr) {
            return has[attr];
        }

        @Override
        public List<Pair<String, String>> getAttrList(int attr) {
            return switch (attr) {
                case CATEGORY_SUBJECT -> subject;
                case CATEGORY_AREA -> area;
                case CATEGORY_READER -> reader;
                case CATEGORY_PROGRESS -> progress;
                case CATEGORY_YEAR -> year;
                case CATEGORY_ORDER -> order;
                default -> null;
            };
        }
    }
}
