package com.xyrlsz.xcimocob.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.xyrlsz.quickjs.QuickJSEngine;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.BuildConfig;
import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.model.JsSource;
import com.xyrlsz.xcimocob.model.JsSource_;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.model.Source_;
import com.xyrlsz.xcimocob.source.js.JsScriptCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.Query;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 动态 JS 漫画源管理。负责 JsSource 表 CRUD，以及从 GitHub raw 仓库
 * 增量拉取 {@code index.json} 清单与源脚本（{@link #updateFromServer(String)}）。
 */
public class JsSourceManager {

    /**
     * 默认在线源仓库（GitHub raw，不含结尾斜杠），便于用户在源列表一键更新。
     */
    public static final String DEFAULT_REPO_URL = "https://raw.githubusercontent.com/xyrlsz/xcimoc-js-sources/main";
    private static final String PREFS = "js_source";
    private static final String KEY_REPO_URL = "repo_url";
    private static final String KEY_SEEDED = "seeded";
    /**
     * 记录上次自动检查源更新的时间戳（毫秒）。
     */
    private static final String KEY_AUTO_UPDATE_LAST = "auto_update_last";
    /**
     * 自动检查更新的最小间隔（6 小时），避免每次启动都联网。
     */
    private static final long AUTO_UPDATE_INTERVAL = 6L * 60 * 60 * 1000;
    /**
     * 记录上次播种/强制覆盖所用的 APK 版本号（versionCode），用于 APK 更新时强制覆盖打包 JS 源。
     */
    private static final String KEY_APP_VERSION = "app_version_seeded";
    /**
     * 标记是否已为存量源回填过 hasCategory 元信息（用于兼容升级）。
     */
    private static final String KEY_CATEGORY_META_BACKFILLED = "category_meta_backfilled";
    // 内置 JS 源来自 git 子模块 xcimoc-js-sources，其根目录即 assets 根
    private static final String ASSETS_INDEX = "index.json";
    private static volatile JsSourceManager mInstance;
    /**
     * 更新操作专用的 OkHttpClient：连接/读取超时更长（raw GitHub 常较慢）。
     */
    private static volatile okhttp3.OkHttpClient sUpdateClient;
    private final Box<JsSource> mBox;
    private final BoxStore mBoxStore;

    private JsSourceManager(AppGetter getter) {
        mBoxStore = getter.getAppInstance().getBoxStore();
        mBox = mBoxStore.boxFor(JsSource.class);
    }

    /* ---------------- 源仓库地址 ---------------- */

    public static JsSourceManager getInstance(AppGetter getter) {
        if (mInstance == null) {
            synchronized (JsSourceManager.class) {
                if (mInstance == null) {
                    mInstance = new JsSourceManager(getter);
                }
            }
        }
        return mInstance;
    }

    /**
     * 语义化版本比较（x.y.z，段可缺省）：a 严格高于 b 返回 true。
     */
    private static boolean isNewerVersion(String a, String b) {
        if (a == null || a.isEmpty()) return false;
        if (b == null || b.isEmpty()) return true;
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = (i < pa.length) ? parseIntSafe(pa[i]) : 0;
            int vb = (i < pb.length) ? parseIntSafe(pb[i]) : 0;
            if (va != vb) return va > vb;
        }
        return false;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /* ---------------- CRUD ---------------- */

    /**
     * 当前 APK 版本号（versionCode）；异常返回 0。用于 APK 更新时强制覆盖打包 JS 源。
     */
    private static int appVersionCode() {
        try {
            return App.getAppContext().getPackageManager()
                    .getPackageInfo(App.getAppContext().getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 把 validateScript 返回的登录/设置元信息写入 JsSource 实体。
     */
    private static void applyMeta(JsSource js, JSONObject meta) {
        js.setHasLogin(meta.optBoolean("__hasLogin", false));
        js.setSettingCount(meta.optInt("__settingCount", 0));
        js.setSettingsJson(meta.optString("__settingsJson", ""));
        js.setHasCategory(meta.optBoolean("__hasCategory", false));
        js.setMetaReady(true);
    }

    private static okhttp3.OkHttpClient updateClient() {
        okhttp3.OkHttpClient c = sUpdateClient;
        if (c == null) {
            synchronized (JsSourceManager.class) {
                if (sUpdateClient == null) {
                    sUpdateClient = new okhttp3.OkHttpClient.Builder()
                            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                            .followRedirects(true)
                            .retryOnConnectionFailure(false)
                            .build();
                }
                c = sUpdateClient;
            }
        }
        return c;
    }

    /**
     * 返回候选仓库基地址：主地址 +（若是 raw.githubusercontent 则追加）常见镜像。
     */
    private static List<String> resolveBaseUrls(String repoUrl) {
        String base = repoUrl.endsWith("/")
                ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
        List<String> list = new ArrayList<>();
        list.add(base);
        try {
            java.net.URI uri = new java.net.URI(base);
            String host = uri.getHost();
            if (host != null && host.endsWith("raw.githubusercontent.com")) {
                String[] parts = uri.getPath().split("/");
                if (parts.length >= 4 && !parts[1].isEmpty() && !parts[2].isEmpty() && !parts[3].isEmpty()) {
                    String owner = parts[1], repo = parts[2], branch = parts[3];
                    // jsdelivr 镜像：https://cdn.jsdelivr.net/gh/{owner}/{repo}@{branch}
                    list.add("https://cdn.jsdelivr.net/gh/" + owner + "/" + repo + "@" + branch);
                    // ghproxy 类代理
                    list.add("https://ghproxy.net/https://raw.githubusercontent.com/"
                            + owner + "/" + repo + "/" + branch);
                    list.add("https://ghfast.top/https://raw.githubusercontent.com/"
                            + owner + "/" + repo + "/" + branch);
                }
            }
        } catch (Exception ignore) {
        }
        return list;
    }

    public String getRepoUrl() {
        return prefs().getString(KEY_REPO_URL, DEFAULT_REPO_URL);
    }

    public void setRepoUrl(String url) {
        prefs().edit().putString(KEY_REPO_URL, url == null ? "" : url.trim()).apply();
    }

    private SharedPreferences prefs() {
        return App.getAppContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* ---------------- 在线更新 ---------------- */

    public Observable<List<JsSource>> list() {
        return Observable.fromCallable(() -> {
            try (Query<JsSource> query = mBox.query().order(JsSource_.type).build()) {
                return query.find();
            }
        }).subscribeOn(Schedulers.io());
    }

    public List<JsSource> listEnabled() {
        try (Query<JsSource> query = mBox.query().equal(JsSource_.enable, true).order(JsSource_.type).build()) {
            return query.find();
        }
    }

    public JsSource loadByType(int type) {
        try (Query<JsSource> query = mBox.query().equal(JsSource_.type, type).build()) {
            return query.findFirst();
        }
    }

    public JsSource loadEnabledByType(int type) {
        JsSource js = loadByType(type);
        if (js == null) {
            Log.w("JsSource", "loadEnabledByType(" + type + "): no JsSource row, total=" + mBox.count());
            return null;
        }
        // 启用状态以 Source 表为准（源列表切换即改 Source.enable）
        Source s;
        try (Query<Source> query = mBoxStore.boxFor(Source.class).query().equal(Source_.type, type).build()) {
            s = query.findFirst();
        }
        if (s == null || !s.getEnable()) {
            Log.w("JsSource", "loadEnabledByType(" + type + "): Source row missing/disabled (source="
                    + (s == null ? "null" : ("enable=" + s.getEnable())) + ")");
            return null;
        }
        return js;
    }

    public long put(JsSource js) {
        return mBox.put(js);
    }

    /* ---------------- 内置源播种 ---------------- */

    public void delete(int type) {
        JsSource js = loadByType(type);
        if (js != null) {
            mBox.remove(js);
        }
    }

    /**
     * App 启动时自动签到：遍历启用的 JS 源，对开启了 auto_sign 设置的源调用 onSettingsAction("sign_in")。
     */
    public void autoSignIn() {
        try {
            for (JsSource js : listEnabled()) {
                if (!"true".equals(com.xyrlsz.xcimocob.source.js.JsHost.INSTANCE.getSetting(js.getType(), "auto_sign"))) {
                    continue;
                }
                try {
                    com.xyrlsz.xcimocob.parser.MangaParser parser =
                            SourceManager.getInstance(App.getApp()).getParser(js.getType());
                    if (parser instanceof com.xyrlsz.xcimocob.source.js.JsMangaParser) {
                        org.json.JSONObject r = ((com.xyrlsz.xcimocob.source.js.JsMangaParser) parser)
                                .settingsCallback("sign_in");
                        Log.i("JsSource", "autoSignIn " + js.getTitle() + " -> " + (r != null ? r.toString() : "null"));
                    }
                } catch (Throwable t) {
                    Log.w("JsSource", "autoSignIn " + js.getTitle() + " failed", t);
                }
            }
        } catch (Throwable t) {
            Log.w("JsSource", "autoSignIn error", t);
        }
    }

    public Observable<UpdateResult> updateFromServer(String repoUrl) {
        return Observable.fromCallable(() -> doUpdate(repoUrl)).subscribeOn(Schedulers.io());
    }

    private UpdateResult doUpdate(String repoUrl) throws Exception {
        UpdateResult result = new UpdateResult();
        // 依次尝试主地址 + 镜像（raw.githubusercontent 在国内常超时，回退到 jsdelivr/ghproxy 等）
        String base = null;
        String indexJson = null;
        for (String candidate : resolveBaseUrls(repoUrl)) {
            try {
                indexJson = httpGet(candidate + "/index.json");
                base = candidate;
                break;
            } catch (Exception e) {
                String em = e.getMessage();
                android.util.Log.w("JsSource", "update: base unreachable " + candidate
                        + " (" + e.getClass().getSimpleName()
                        + (em != null ? ": " + em : "") + ")");
            }
        }
        if (base == null || indexJson == null) {
            throw new Exception("无法连接任何源仓库镜像，请检查网络或仓库地址");
        }
        JSONObject index = new JSONObject(indexJson);
        JSONArray sources = index.getJSONArray("sources");
        // 记录清单中的所有 type，用于删除仓库中已移除的本地源
        java.util.Set<Integer> remoteTypes = new java.util.HashSet<>();
        for (int i = 0; i < sources.length(); i++) {
            JSONObject entry = sources.getJSONObject(i);
            int type = entry.optInt("type", -1);
            String version = entry.optString("version");
            String url = entry.optString("url");
            String title = entry.optString("title");
            String baseUrl = entry.optString("baseUrl");
            if (type < 0 || url.isEmpty()) {
                result.failed++;
                result.errors.add("非法清单条目: " + entry.toString());
                continue;
            }
            remoteTypes.add(type);
            JsSource local = loadByType(type);
            if (local != null && version.equals(local.getVersion())) {
                continue; // 版本一致，跳过
            }
            try {
                String script = httpGet(base + "/" + url);
                JSONObject meta = validateScript(script);
                JsSource js = (local != null) ? local : new JsSource();
                js.setType(type);
                js.setTitle(meta.has("title") ? meta.optString("title") : title);
                js.setVersion(version);
                js.setBaseUrl(meta.has("baseUrl") ? meta.optString("baseUrl") : baseUrl);
                js.setHosts(meta.optString("hosts"));
                js.setCidRegex(meta.optString("cidRegex"));
                js.setCidQuery(meta.optString("cidQuery"));
                js.setSourceUrl(base + "/" + url);
                js.setScript(script);
                js.setEnable(local != null ? local.isEnable() : meta.optBoolean("defaultEnable", true));
                applyMeta(js, meta);
                mBox.put(js);
                syncSource(js);
                if (local == null) {
                    result.added++;
                } else {
                    result.updated++;
                }
            } catch (Exception e) {
                result.failed++;
                result.errors.add(title + "(type=" + type + "): " + e.getMessage());
            }
        }
        // 删除仓库清单中已不存在的本地 JS 源（含 Source 表记录）
        for (JsSource local : mBox.getAll()) {
            if (!remoteTypes.contains(local.getType())) {
                removeSourceCompletely(local.getType());
                result.removed++;
                Log.i("JsSource", "update: removed source type=" + local.getType()
                        + " title=" + local.getTitle());
            }
        }
        SourceManager.getInstance(App.getApp()).refreshParserCache();
        return result;
    }

    /**
     * 删除一个 JS 源（同时删除 Source 表对应记录，避免残留不可用的源）。
     */
    public void removeSourceCompletely(int type) {
        delete(type);
        Box<Source> sbox = mBoxStore.boxFor(Source.class);
        Source s;
        try (Query<Source> query = sbox.query().equal(Source_.type, type).build()) {
            s = query.findFirst();
        }
        if (s != null) {
            sbox.remove(s);
        }
    }

    /**
     * 打开 App 时自动检查并更新漫画源（后台线程调用）。
     * 受设置开关 {@code PREF_OTHER_JS_AUTO_UPDATE} 控制，且带频率限制
     * （默认 6 小时内不重复自动检查，避免每次启动都联网）。
     */
    public void autoUpdateIfNeeded() {
        try {
            if (!com.xyrlsz.xcimocob.App.getPreferenceManager()
                    .getBoolean(com.xyrlsz.xcimocob.manager.PreferenceManager.PREF_OTHER_JS_AUTO_UPDATE, true)) {
                return;
            }
            long now = System.currentTimeMillis();
            long last = prefs().getLong(KEY_AUTO_UPDATE_LAST, 0);
            if (now - last < AUTO_UPDATE_INTERVAL) {
                Log.d("JsSource", "autoUpdateIfNeeded: skip (last=" + last + ")");
                return;
            }
            String repo = getRepoUrl();
            if (repo == null || repo.isEmpty()) {
                return;
            }
            UpdateResult r = doUpdate(repo);
            prefs().edit().putLong(KEY_AUTO_UPDATE_LAST, now).apply();
            Log.i("JsSource", "autoUpdateIfNeeded: added=" + r.added + " updated=" + r.updated
                    + " removed=" + r.removed + " failed=" + r.failed);
        } catch (Throwable t) {
            Log.w("JsSource", "autoUpdateIfNeeded: failed", t);
        }
    }

    /**
     * 从打包进 assets 的内置 JS 源（{@code assets/js/sources/}）播种数据库。
     * 仅当 JsSource 表尚无该 type 时写入（不覆盖用户已有的源或手动更新的源）。
     * 返回本次新增的源数量。
     */
    public int seedFromAssets() {
        // 每次启动都核对内置清单：缺源补种；已存在但打包版本更新则升级（保留启用状态，
        // 且仅当打包版本严格更高时才覆盖，不覆盖用户经「更新源」拉的更新版本）。
        // APK 版本（versionCode）变化时强制用打包 JS 源覆盖，保证升级 App 后新 JS 生效。
        int added = 0;
        // debug 构建每次都强制用内置 assets 重新播种（改本地 JS 源后直接 Run 即生效）；
        // release 仍只在 APK 更新时强制覆盖。
        boolean force = BuildConfig.DEBUG
                || (getRepoUrl().contains("xyrlsz/xcimoc-js-sources")
                && appVersionCode() != prefs().getInt(KEY_APP_VERSION, -1));
        Log.d("JsSource", "seedFromAssets: start, existing count=" + mBox.count()
                + ", force=" + force + " (debug=" + BuildConfig.DEBUG + ")");
        try {
            String indexJson = new String(
                    com.xyrlsz.xcimocob.utils.BinStreamUtils.readAllBytesCompat(
                            App.getAppContext().getAssets().open(ASSETS_INDEX)),
                    java.nio.charset.StandardCharsets.UTF_8);
            JSONObject index = new JSONObject(indexJson);
            JSONArray sources = index.getJSONArray("sources");
            for (int i = 0; i < sources.length(); i++) {
                JSONObject entry = sources.optJSONObject(i);
                if (entry == null) continue;
                int type = entry.optInt("type", -1);
                String url = entry.optString("url");
                String title = entry.optString("title");
                String baseUrl = entry.optString("baseUrl");
                String version = entry.optString("version");
                if (type < 0 || url.isEmpty()) continue;
                JsSource existing = loadByType(type);
                // force（APK 更新）时强制覆盖；否则仅当打包版本更高才升级
                if (existing != null && !force && !isNewerVersion(version, existing.getVersion())) {
                    continue;
                }
                try {
                    String script = new String(
                            com.xyrlsz.xcimocob.utils.BinStreamUtils.readAllBytesCompat(
                                    App.getAppContext().getAssets().open(url)),
                            java.nio.charset.StandardCharsets.UTF_8);
                    JSONObject meta = validateScript(script);
                    JsSource js = (existing != null) ? existing : new JsSource();
                    js.setType(type);
                    js.setTitle(meta.has("title") ? meta.optString("title") : title);
                    js.setVersion(version);
                    js.setBaseUrl(meta.has("baseUrl") ? meta.optString("baseUrl") : baseUrl);
                    js.setHosts(meta.optString("hosts"));
                    js.setCidRegex(meta.optString("cidRegex"));
                    js.setCidQuery(meta.optString("cidQuery"));
                    js.setSourceUrl(url);
                    js.setScript(script);
                    js.setEnable(existing != null ? existing.isEnable() : meta.optBoolean("defaultEnable", true));
                    applyMeta(js, meta);
                    mBox.put(js);
                    syncSource(js);
                    added++;
                } catch (Exception e) {
                    // 单个源失败不影响其它源；带堆栈便于定位 JS 求值失败原因
                    Log.w("JsSource", "seedFromAssets: source '" + title + "'(type=" + type
                            + ") failed: " + e, e);
                }
            }
        } catch (Exception e) {
            // 无内置清单或解析失败时静默跳过
            Log.e("JsSource", "seedFromAssets: index read/parse failed: " + e);
        }
        // 仅当确实播种了源时才记录已播种 + 本次 APK 版本；失败（added==0）不置位，下次启动会重试
        if (added > 0) {
            prefs().edit()
                    .putBoolean(KEY_SEEDED, true)
                    .putInt(KEY_APP_VERSION, appVersionCode())
                    .apply();
        }
        Log.d("JsSource", "seedFromAssets: done, added=" + added
                + ", total=" + mBox.count());
        if (added > 0) {
            SourceManager.getInstance(App.getApp()).refreshParserCache();
        }
        return added;
    }

    /**
     * 让 Source 表与 JS 源保持同步，便于源列表展示与 getParser 分发。
     */
    private void syncSource(JsSource js) {
        Box<Source> box = mBoxStore.boxFor(Source.class);
        Source s;
        try (Query<Source> query = box.query().equal(Source_.type, js.getType()).build()) {
            s = query.findFirst();
        }
        if (s == null) {
            s = new Source();
            s.setType(js.getType());
            s.setEnable(js.isEnable());
        }
        s.setTitle(js.getTitle());
        s.setBaseUrl(js.getBaseUrl());
        s.setHasCategory(js.isHasCategory());
        box.put(s);
    }

    /**
     * 校验一段源脚本：在 QuickJS 中评估 SDK+脚本并读取 SOURCE 元数据。
     * 非法（type < 0 或读取不到）时抛 {@link IllegalArgumentException}。
     * <p>
     * 同时计算登录/设置/分类元信息并写入返回的 meta：
     * <ul>
     *   <li>{@code __hasLogin}：脚本是否声明 login 或 getLoginState</li>
     *   <li>{@code __settingCount}：getSettings 返回数组长度</li>
     *   <li>{@code __settingsJson}：getSettings 返回的数组原文（供设置页直接渲染）</li>
     *   <li>{@code __hasCategory}：脚本是否实现 getCategories（供分类页过滤支持分类的源）</li>
     * </ul>
     * 这些值在源入库时缓存到 JsSource 表，列表/分类加载时直接读取，避免逐源跑 JS。
     */
    public JSONObject validateScript(String script) throws Exception {
        QuickJSEngine engine = new QuickJSEngine();
        try {
            engine.setGlobal("__SOURCE_TYPE", "-1");
            // 与运行时同一条求值路径：顺带产出/复用字节码缓存，后续创建解析器引擎直接命中
            JsScriptCache.evaluate(engine, script);
            String src = engine.getGlobalJson("SOURCE");
            if (src == null || "null".equals(src)) {
                throw new IllegalArgumentException("脚本缺少 SOURCE 元数据（需用 var SOURCE = {...}）");
            }
            JSONObject meta = new JSONObject(src);
            int type = meta.optInt("type", -1);
            if (type < 0) {
                throw new IllegalArgumentException("SOURCE.type 非法: " + type);
            }
            // 顺便计算登录/设置元信息并缓存（列表页直接读 DB，无需再跑 JS）
            boolean hasLogin = engine.hasFunction("login") || engine.hasFunction("getLoginState");
            String settingsJson = "";
            int settingCount = 0;
            if (engine.hasFunction("getSettings")) {
                try {
                    String raw = engine.callFunction("getSettings", "[]");
                    if (raw != null && !"null".equals(raw) && !raw.isEmpty()) {
                        JSONArray arr = new JSONArray(raw);
                        settingCount = arr.length();
                        settingsJson = raw;
                    }
                } catch (Exception e) {
                    Log.w("JsSource", "validateScript: getSettings failed: " + e);
                }
            }
            meta.put("__hasLogin", hasLogin);
            meta.put("__settingCount", settingCount);
            meta.put("__settingsJson", settingsJson);
            // 分类能力：仅判断是否实现 getCategories，不执行它（执行交给分类页按需懒加载）。
            meta.put("__hasCategory", engine.hasFunction("getCategories"));
            return meta;
        } finally {
            try {
                engine.close();
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     * 为存量 JsSource 回填登录/设置/分类元信息。
     * 逐个评估脚本并把 hasLogin/settingCount/settingsJson/hasCategory 写回 DB，
     * 再同步到 Source 表。App 启动时在后台线程调用一次即可，之后列表/分类加载直接读缓存。
     * <p>
     * 为兼容已回填过登录/设置元信息（metaReady=true）但尚未计算 hasCategory 的存量源，
     * 首次运行（{@code KEY_CATEGORY_META_BACKFILLED} 未置位）会重新评估所有源一次。
     */
    public void backfillMeta() {
        boolean categoryBackfilled = prefs().getBoolean(KEY_CATEGORY_META_BACKFILLED, false);
        List<JsSource> all = mBox.getAll();
        int done = 0;
        for (JsSource js : all) {
            // metaReady 且分类元信息已回填过的源跳过；其余（含首次全量回填）重新评估
            if (js.isMetaReady() && categoryBackfilled) continue;
            try {
                JSONObject meta = validateScript(js.getScript());
                applyMeta(js, meta);
                mBox.put(js);
                syncSource(js); // 把 hasCategory 同步到 Source 表
                done++;
            } catch (Exception e) {
                Log.w("JsSource", "backfillMeta: type=" + js.getType()
                        + " title=" + js.getTitle() + " failed: " + e);
            }
        }
        if (!categoryBackfilled) {
            prefs().edit().putBoolean(KEY_CATEGORY_META_BACKFILLED, true).apply();
        }
        if (done > 0) {
            Log.i("JsSource", "backfillMeta: done " + done + " sources");
        }
    }

    /**
     * 更新专用下载：直接用 updateClient 执行，不重试、不 printStackTrace（getResponseBody 会
     * 重试一次且刷堆栈，对镜像探测不合适）。
     */
    private String httpGet(String url) throws Exception {
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
        try (okhttp3.Response resp = updateClient().newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new java.io.IOException("HTTP " + resp.code());
            }
            return resp.body().string();
        }
    }

    /**
     * 一次在线更新的统计结果。
     */
    public static class UpdateResult {
        public final List<String> errors = new ArrayList<>();
        public int added = 0;
        public int updated = 0;
        public int removed = 0;
        public int failed = 0;
    }
}
