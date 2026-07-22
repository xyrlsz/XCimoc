package com.xyrlsz.xcimocob.test;

import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.Category;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.parser.SearchIterator;
import com.xyrlsz.xcimocob.source.*;

import okhttp3.*;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * XCimoc 漫画源 Java 测试工具
 *
 * 直接复用漫画源代码，对每个源运行完整的解析流程测试。
 * 输出 JSON 格式的测试报告，供 GitHub Pages 展示。
 *
 * 用法:
 *   java -jar sourcetester-1.0.jar
 *   java -jar sourcetester-1.0.jar --source 0,5,26
 *   java -jar sourcetester-1.0.jar --verbose
 */
public class Main {

    // ═══════════════════════════════════════════════════════════════
    //  源注册表 — 与 SourceManager 一致
    // ═══════════════════════════════════════════════════════════════

    static class SourceEntry {
        final int type;
        final String title;
        final String defaultBaseUrl;

        SourceEntry(int type, String title, String defaultBaseUrl) {
            this.type = type;
            this.title = title;
            this.defaultBaseUrl = defaultBaseUrl;
        }
    }

    static final SourceEntry[] ALL_SOURCES = {
        new SourceEntry(0,   "漫画柜",      "https://www.manhuagui.com"),
        new SourceEntry(5,   "动漫屋",      "https://m.dm5.com"),
        new SourceEntry(11,  "咚漫漫画",    "https://www.dongmanmanhua.cn"),
        new SourceEntry(12,  "再漫画",      "https://m.zaimanhua.com"),
        new SourceEntry(26,  "拷贝漫画",    "https://www.copy3000.com"),
        new SourceEntry(27,  "拷贝漫画Web", "https://www.copy3000.com"),
        new SourceEntry(49,  "漫画台",      "https://www.kanman.com"),
        new SourceEntry(51,  "腾讯动漫",    "https://m.ac.qq.com"),
        new SourceEntry(52,  "布卡漫画",    "https://www.bukamh.com"),
        new SourceEntry(82,  "MangaBZ",     "http://www.mangabz.com"),
        new SourceEntry(91,  "优酷漫画",    "https://m.ykmh.net"),
        new SourceEntry(101, "包子漫画",    "https://www.baozimh.com"),
        new SourceEntry(102, "热辣漫画",    "https://www.manga2026.com"),
        new SourceEntry(103, "MYCOMIC",     "https://mycomic.com"),
        new SourceEntry(104, "读漫屋",      "http://dumanwu1.com"),
        new SourceEntry(106, "Komiic",      "https://komiic.com"),
        new SourceEntry(107, "漫画鱼",      "https://www.manhuayu88.com"),
        new SourceEntry(108, "G社漫畫",     "https://m.g-mh.org"),
        new SourceEntry(110, "vomic漫",     "https://www.vomicmh.com"),
        new SourceEntry(111, "YY漫画",      "https://www.yymanhua.com"),
        new SourceEntry(113, "漫本",        "https://www.manben.com"),
        new SourceEntry(114, "古风漫画",    "https://www.gfmh.app"),
        new SourceEntry(115, "漫蛙",        "https://manwawang.com"),
        new SourceEntry(116, "漫画屋",      "https://mh5.app"),
        new SourceEntry(117, "读漫屋app",   "https://d9zfb53b.lstool.xyz"),
        new SourceEntry(118, "动漫嗨",      "https://www.dongmanhi.com"),
    };

    // ═══════════════════════════════════════════════════════════════
    //  自定义覆盖配置 — 可为每个源指定搜索关键词和漫画 ID
    //  - 若配置了 comicId，则跳过搜索步骤，直接测试该漫画
    //  - 若配置了 keyword 但未配置 comicId，则用该关键词搜索
    //  - 若均未配置，使用默认 a-z 轮换关键词 + 自动提取 CID
    // ═══════════════════════════════════════════════════════════════

    static class SourceOverride {
        final String keyword;     // 自定义搜索关键词（null 表示用默认轮换）
        final String comicId;     // 自定义漫画 ID（null 表示从搜索结果提取）
        final String connectUrl;  // 自定义连通性测试 URL（null 表示用 baseUrl）

        SourceOverride(String keyword, String comicId) {
            this(keyword, comicId, null);
        }

        SourceOverride(String keyword, String comicId, String connectUrl) {
            this.keyword = keyword;
            this.comicId = comicId;
            this.connectUrl = connectUrl;
        }
    }

    static final Map<Integer, SourceOverride> SOURCE_OVERRIDES = new HashMap<>();
    static {
        // ── 内置自定义配置 ─────────────────────────────────────
        // 格式: SOURCE_OVERRIDES.put(TYPE, new SourceOverride("关键词", "漫画ID"));
        // 关键词为 null 则用默认 a-z 轮换；漫画 ID 为 null 则自动从搜索结果提取
        //
        // 示例：为漫画柜指定固定漫画，跳过搜索步骤
        // SOURCE_OVERRIDES.put(0, new SourceOverride(null, "30449"));
        // 示例：为动漫屋指定搜索关键词
        // SOURCE_OVERRIDES.put(5, new SourceOverride("火影忍者", null));
        // ────────────────────────────────────────────────────────

        // 加载外部配置文件（若存在）
        loadOverridesFromJson();
    }

    /** 从 JSON 配置文件加载自定义覆盖项 */
    static void loadOverridesFromJson() {
        // 搜索路径：当前目录 → sourcetester/ 目录 → 父目录
        File configFile = new File("source-overrides.json");
        if (!configFile.isFile()) configFile = new File("sourcetester/source-overrides.json");
        if (!configFile.isFile()) configFile = new File("../source-overrides.json");
        if (!configFile.isFile()) return;

        try {
            String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONArray arr = new org.json.JSONArray(content);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                int type = obj.getInt("type");
                String kw = obj.has("keyword") && !obj.isNull("keyword") ? obj.getString("keyword") : null;
                String cid = obj.has("comicId") && !obj.isNull("comicId") ? obj.getString("comicId") : null;
                String url = obj.has("connectUrl") && !obj.isNull("connectUrl") ? obj.getString("connectUrl") : null;
                SOURCE_OVERRIDES.put(type, new SourceOverride(kw, cid, url));
            }
            System.out.println("  📋 已加载 " + arr.length() + " 条自定义配置: " + configFile);
        } catch (Exception e) {
            System.err.println("  警告: 加载配置文件失败 (" + configFile + "): " + e.getMessage());
        }
    }

    // 搜索关键词轮换
    private static int keywordIndex = 0;
    private static final String[] KEYWORDS = {
        "a","b","c","d","e","f","g","h","i","j","k","l","m",
        "n","o","p","q","r","s","t","u","v","w","x","y","z"
    };

    private static synchronized String nextKeyword() {
        String kw = KEYWORDS[keywordIndex % KEYWORDS.length];
        keywordIndex++;
        return kw;
    }

    /** 获取源的搜索关键词：优先取自定义配置，否则轮换 */
    private static String getKeywordForSource(int type) {
        SourceOverride ov = SOURCE_OVERRIDES.get(type);
        if (ov != null && ov.keyword != null && !ov.keyword.isEmpty()) {
            return ov.keyword;
        }
        return nextKeyword();
    }

    /** 获取源的自定义漫画 ID（没有则返回 null） */
    private static String getCustomCid(int type) {
        SourceOverride ov = SOURCE_OVERRIDES.get(type);
        if (ov != null && ov.comicId != null && !ov.comicId.isEmpty()) {
            return ov.comicId;
        }
        return null;
    }

    /** 获取源的连通性测试 URL：优先取自定义配置，否则用 baseUrl */
    private static String getConnectUrl(SourceEntry entry) {
        SourceOverride ov = SOURCE_OVERRIDES.get(entry.type);
        if (ov != null && ov.connectUrl != null && !ov.connectUrl.isEmpty()) {
            return ov.connectUrl;
        }
        return entry.defaultBaseUrl;
    }

    // ═══════════════════════════════════════════════════════════════
    //  HTTP 客户端
    // ═══════════════════════════════════════════════════════════════

    static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectionPool(new ConnectionPool(5, 30, TimeUnit.SECONDS))
        .build();

    static {
        // 设置全局 HTTP 客户端，让源代码中的 App.getHttpClient() 返回它
        App.setHttpClient(client);
    }

    // ═══════════════════════════════════════════════════════════════
    //  数据模型
    // ═══════════════════════════════════════════════════════════════

    static class TestResult {
        String name;
        String status;  // "pass" | "fail" | "skip" | "error"
        long durationMs;
        String detail;
        String error;

        TestResult(String name, String status, long durationMs, String detail, String error) {
            this.name = name;
            this.status = status;
            this.durationMs = durationMs;
            this.detail = detail;
            this.error = error;
        }
    }

    static class SourceReport {
        int typeId;
        String title;
        String baseUrl;
        List<TestResult> results = new ArrayList<>();
        String overall = "skip";

        int passed() { return (int) results.stream().filter(r -> "pass".equals(r.status)).count(); }
        int failed() { return (int) results.stream().filter(r -> "fail".equals(r.status) || "error".equals(r.status)).count(); }
        int total()  { return results.size(); }
    }

    // ═══════════════════════════════════════════════════════════════
    //  测试引擎
    // ═══════════════════════════════════════════════════════════════

    static MangaParser createParser(SourceEntry entry) {
        Source source = new Source(null, entry.title, entry.type, true,
            entry.defaultBaseUrl.isEmpty() ? null : entry.defaultBaseUrl);

        switch (entry.type) {
            case 0:   return new ManHuaGui(source);
            case 5:   return new DM5(source);
            case 11:  return new DongManManHua(source);
            case 12:  return new ZaiManhua(source);
            case 26:  return new CopyMH(source);
            case 27:  return new CopyMHWeb(source);
            case 49:  return new Manhuatai(source);
            case 51:  return new Tencent(source);
            case 52:  return new BuKa(source);
            case 82:  return new MangaBZ(source);
            case 91:  return new YKMH(source);
            case 101: return new Baozi(source);
            case 102: return new HotManga(source);
            case 103: return new MYCOMIC(source);
            case 104: return new DuManWu(source);
            case 106: return new Komiic(source);
            case 107: return new Manhuayu(source);
            case 108: return new GoDaManHua(source);
            case 110: return new Vomicmh(source);
            case 111: return new YYManHua(source);
            case 113: return new ManBen(source);
            case 114: return new GFMH(source);
            case 115: return new ManWa(source);
            case 116: return new MH5(source);
            case 117: return new DuManWuApp(source);
            case 118: return new DongManHi(source);
            default:  return null;
        }
    }

    static String executeRequest(Request request) {
        if (request == null) return null;
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) return null;
            if (resp.body() == null) return null;
            return resp.body().string();
        } catch (Exception e) {
            return null;
        }
    }

    static TestResult measure(String name, TestTask task) {
        long start = System.nanoTime();
        try {
            Object result = task.run();
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            if (result instanceof Boolean && (Boolean) result) {
                return new TestResult(name, "pass", elapsed, "OK", "");
            }
            if (result instanceof Object[]) {
                Object[] arr = (Object[]) result;
                if (arr.length == 2) {
                    // (true, detail) → pass with detail
                    if (arr[0] instanceof Boolean && (Boolean) arr[0]) {
                        return new TestResult(name, "pass", elapsed,
                            arr[1] == null ? "OK" : arr[1].toString(), "");
                    }
                    // ("skip", reason) → skip
                    if ("skip".equals(arr[0])) {
                        return new TestResult(name, "skip", elapsed,
                            arr[1] == null ? "" : arr[1].toString(), "");
                    }
                }
            }
            if (result instanceof String) {
                return new TestResult(name, "fail", elapsed, "", (String) result);
            }
            return new TestResult(name, "fail", elapsed, "", String.valueOf(result));
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            return new TestResult(name, "error", elapsed, "", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    interface TestTask {
        Object run() throws Exception;
    }

    // ═══════════════════════════════════════════════════════════════
    //  测试单个源
    // ═══════════════════════════════════════════════════════════════

    static SourceReport testSource(SourceEntry entry, boolean verbose) {
        SourceReport report = new SourceReport();
        report.typeId = entry.type;
        report.title = entry.title;
        report.baseUrl = entry.defaultBaseUrl;

        MangaParser parser = createParser(entry);
        if (parser == null) {
            report.overall = "skip";
            return report;
        }

        // 1. 基础连通性
        report.results.add(measure("基础连通性", () -> {
            String url = getConnectUrl(entry);
            if (url.isEmpty()) return new Object[]{"skip", "无 baseUrl"};
            Request req = new Request.Builder().url(url).build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.code() < 500 && resp.body() != null && resp.body().bytes().length > 0) {
                    return new Object[]{true, "HTTP " + resp.code()};
                }
                return "HTTP " + resp.code();
            }
        }));

        // 2. 确定 CID 来源：优先用自定义配置，否则搜索提取
        final String[] foundCid = {getCustomCid(entry.type)};
        final boolean hasCustomCid = (foundCid[0] != null);

        if (hasCustomCid) {
            // 自定义 CID：跳过搜索，直接记录
            String kwDesc = SOURCE_OVERRIDES.containsKey(entry.type) &&
                SOURCE_OVERRIDES.get(entry.type).keyword != null ?
                ", 自定义关键词=" + SOURCE_OVERRIDES.get(entry.type).keyword : "";
            report.results.add(new TestResult("搜索+提取CID", "skip", 0,
                "使用自定义 CID=" + foundCid[0] + kwDesc, ""));
        } else {
            // 自动搜索 + 提取 CID
            final String[] usedKeyword = {getKeywordForSource(entry.type)};
            final String kwSource = SOURCE_OVERRIDES.containsKey(entry.type) &&
                SOURCE_OVERRIDES.get(entry.type).keyword != null ? "自定义" : "默认轮换";
            report.results.add(measure("搜索+提取CID", () -> {
                Request req = parser.getSearchRequest(usedKeyword[0], 1);
                if (req == null) return "搜索请求返回 null";
                String html = executeRequest(req);
                if (html == null || html.isEmpty()) {
                    return String.format("搜索返回空 (关键词=%s, 来源=%s)", usedKeyword[0], kwSource);
                }

                SearchIterator iter = parser.getSearchIterator(html, 1);
                if (iter == null) return "SearchIterator 为 null";

                int count = 0;
                while (iter.hasNext()) {
                    Comic comic = iter.next();
                    count++;
                    if (comic != null && comic.getCid() != null && !comic.getCid().isEmpty()) {
                        foundCid[0] = comic.getCid();
                        return new Object[]{true, String.format("关键词=%s(%s), CID=%s, 共%d结果",
                            usedKeyword[0], kwSource, foundCid[0], count)};
                    }
                }
                return String.format("关键词=%s, 共%d结果, 未能提取CID", usedKeyword[0], count);
            }));
        }

        // 3. 详情页
        if (foundCid[0] != null) {
            final String cid = foundCid[0];
            report.results.add(measure("详情页", () -> {
                Request req = parser.getInfoRequest(cid);
                if (req == null) return "详情请求返回 null";
                String html = executeRequest(req);
                if (html == null || html.isEmpty()) return "详情页返回空";

                Comic comic = new Comic(entry.type, cid, "", "", "", "");
                parser.parseInfo(html, comic);

                String title = comic.getTitle();
                String cover = comic.getCover();
                String author = comic.getAuthor();
                String intro = comic.getIntro();

                StringBuilder detail = new StringBuilder();
                detail.append(html.length()).append(" 字节");
                if (title != null && !title.isEmpty()) detail.append(", 标题=").append(title);
                if (author != null && !author.isEmpty()) detail.append(", 作者=").append(author);
                if (cover != null && !cover.isEmpty()) detail.append(", 有封面");

                return new Object[]{true, detail.toString()};
            }));

            // 4. 章节列表 + 图片列表
            report.results.add(measure("章节列表", () -> {
                // 获取详情页 HTML
                Request infoReq = parser.getInfoRequest(cid);
                String infoHtml = executeRequest(infoReq);
                if (infoHtml == null || infoHtml.isEmpty()) {
                    return "详情页请求失败";
                }

                Comic comicObj = new Comic(entry.type, cid);
                List<Chapter> chapters = parser.parseChapter(infoHtml, comicObj, 0L);
                if (chapters == null || chapters.isEmpty()) {
                    return "未解析到章节";
                }

                StringBuilder detail = new StringBuilder();
                detail.append(chapters.size()).append(" 章");
                Chapter first = chapters.get(0);
                if (first.getTitle() != null) detail.append(", 首章=").append(first.getTitle());
                if (first.getPath() != null) detail.append(", 路径=").append(first.getPath());

                // 用第一章测试图片列表（作为章节列表的补充）
                String path = first.getPath();
                if (path != null && !path.isEmpty()) {
                    try {
                        Request imgReq = parser.getImagesRequest(cid, path);
                        if (imgReq != null) {
                            String imgHtml = executeRequest(imgReq);
                            if (imgHtml != null && !imgHtml.isEmpty()) {
                                List<ImageUrl> images = parser.parseImages(imgHtml, first);
                                if (images != null && !images.isEmpty()) {
                                    detail.append(", 首章=").append(images.size()).append(" 图");
                                }
                            }
                        }
                    } catch (Exception e) {
                        detail.append(", 图片解析异常: ").append(e.getMessage());
                    }
                }

                return new Object[]{true, detail.toString()};
            }));

        } else {
            report.results.add(new TestResult("详情页", "skip", 0, "未提取到漫画 ID", ""));
            report.results.add(new TestResult("章节列表", "skip", 0, "未提取到漫画 ID", ""));
            report.results.add(new TestResult("图片列表", "skip", 0, "未提取到漫画 ID", ""));
        }

        // 5. 分类浏览 — 真实测试分类页面的请求和解析
        report.results.add(measure("分类浏览", () -> {
            Category category = parser.getCategory();
            if (category == null) return new Object[]{"skip", "无分类"};

            // 使用默认参数获取分类格式字符串
            String format = category.getFormat();
            if (format == null || format.isEmpty()) return new Object[]{"skip", "分类格式为空"};

            Request catReq = parser.getCategoryRequest(format, 1);
            if (catReq == null) return new Object[]{"skip", "分类请求为 null"};

            String catHtml = executeRequest(catReq);
            if (catHtml == null || catHtml.isEmpty()) {
                return "分类页面请求失败 (HTTP 错误或空响应)";
            }

            List<Comic> catComics = parser.parseCategory(catHtml, 1);
            if (catComics == null || catComics.isEmpty()) {
                return "分类页面解析失败 (未解析到漫画)";
            }

            StringBuilder detail = new StringBuilder();
            detail.append(catHtml.length()).append(" 字节, ").append(catComics.size()).append(" 部漫画");
            Comic first = catComics.get(0);
            if (first.getCid() != null) detail.append(", 首个CID=").append(first.getCid());
            if (first.getTitle() != null) detail.append(", 标题=\"").append(first.getTitle()).append("\"");
            return new Object[]{true, detail.toString()};
        }));

        // 计算总体状态
        int failed = report.failed();
        if (failed > 0) report.overall = "fail";
        else if (report.passed() > 0) report.overall = "pass";
        else report.overall = "skip";

        return report;
    }

    // ═══════════════════════════════════════════════════════════════
    //  JSON 输出
    // ═══════════════════════════════════════════════════════════════

    static String toJson(List<SourceReport> reports, long durationMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"timestamp\": \"").append(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .format(new Date())).append("\",\n");
        sb.append("  \"total_sources\": ").append(reports.size()).append(",\n");
        sb.append("  \"passed_sources\": ").append(reports.stream().filter(r -> "pass".equals(r.overall)).count()).append(",\n");
        sb.append("  \"failed_sources\": ").append(reports.stream().filter(r -> "fail".equals(r.overall)).count()).append(",\n");
        sb.append("  \"skipped_sources\": ").append(reports.stream().filter(r -> "skip".equals(r.overall)).count()).append(",\n");
        sb.append("  \"total_tests\": ").append(reports.stream().mapToInt(r -> r.total()).sum()).append(",\n");
        sb.append("  \"passed_tests\": ").append(reports.stream().mapToInt(r -> r.passed()).sum()).append(",\n");
        sb.append("  \"failed_tests\": ").append(reports.stream().mapToInt(r -> r.failed()).sum()).append(",\n");
        sb.append("  \"skipped_tests\": ").append(reports.stream().mapToInt(r -> r.total() - r.passed() - r.failed()).sum()).append(",\n");
        sb.append("  \"duration_seconds\": ").append(String.format("%.1f", durationMs / 1000.0)).append(",\n");
        sb.append("  \"sources\": [\n");

        for (int i = 0; i < reports.size(); i++) {
            SourceReport sr = reports.get(i);
            sb.append("    {\n");
            sb.append("      \"type_id\": ").append(sr.typeId).append(",\n");
            sb.append("      \"title\": \"").append(escapeJson(sr.title)).append("\",\n");
            sb.append("      \"base_url\": \"").append(escapeJson(sr.baseUrl)).append("\",\n");
            sb.append("      \"overall\": \"").append(sr.overall).append("\",\n");
            sb.append("      \"results\": [\n");

            for (int j = 0; j < sr.results.size(); j++) {
                TestResult tr = sr.results.get(j);
                sb.append("        {\n");
                sb.append("          \"name\": \"").append(escapeJson(tr.name)).append("\",\n");
                sb.append("          \"status\": \"").append(tr.status).append("\",\n");
                sb.append("          \"duration_ms\": ").append(tr.durationMs).append(",\n");
                sb.append("          \"detail\": \"").append(escapeJson(tr.detail != null ? tr.detail : "")).append("\",\n");
                sb.append("          \"error\": \"").append(escapeJson(tr.error != null ? tr.error : "")).append("\"\n");
                sb.append("        }");
                if (j < sr.results.size() - 1) sb.append(",");
                sb.append("\n");
            }

            sb.append("      ]\n");
            sb.append("    }");
            if (i < reports.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // ═══════════════════════════════════════════════════════════════
    //  HTML 报告生成
    // ═══════════════════════════════════════════════════════════════

    static String toHtml(List<SourceReport> reports, long durationMs) {
        long totalSources = reports.size();
        long passedSources = reports.stream().filter(r -> "pass".equals(r.overall)).count();
        long failedSources = reports.stream().filter(r -> "fail".equals(r.overall)).count();
        long skippedSources = reports.stream().filter(r -> "skip".equals(r.overall)).count();
        long totalTests = reports.stream().mapToInt(r -> r.total()).sum();
        long passedTests = reports.stream().mapToInt(r -> r.passed()).sum();
        long failedTests = reports.stream().mapToInt(r -> r.failed()).sum();
        long skippedTests = reports.stream().mapToInt(r -> r.total() - r.passed() - r.failed()).sum();

        double passRate = totalSources > 0 ? (passedSources * 100.0 / totalSources) : 0;
        double testPassRate = totalTests > 0 ? (passedTests * 100.0 / totalTests) : 0;

        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .format(new Date());

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"zh-CN\">\n");
        sb.append("<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>XCimoc 漫画源测试报告</title>\n");
        sb.append("<style>\n");
        sb.append("*{margin:0;padding:0;box-sizing:border-box}\n");
        sb.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;");
        sb.append("background:#0d1117;color:#c9d1d9;padding:20px;line-height:1.6}\n");
        sb.append(".container{max-width:1100px;margin:0 auto}\n");
        sb.append("h1{color:#58a6ff;margin-bottom:10px;font-size:24px}\n");
        sb.append("h2{color:#f0f6fc;margin:20px 0 10px;font-size:18px}\n");
        sb.append(".meta{color:#8b949e;font-size:13px;margin-bottom:20px}\n");
        sb.append(".summary-cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));");
        sb.append("gap:12px;margin-bottom:24px}\n");
        sb.append(".card{background:#161b22;border:1px solid #30363d;border-radius:8px;");
        sb.append("padding:16px;text-align:center}\n");
        sb.append(".card .num{font-size:28px;font-weight:700}\n");
        sb.append(".card .label{font-size:12px;color:#8b949e;margin-top:4px}\n");
        sb.append(".card.pass{border-color:#3fb950}.card.pass .num{color:#3fb950}\n");
        sb.append(".card.fail{border-color:#f85149}.card.fail .num{color:#f85149}\n");
        sb.append(".card.skip{border-color:#d29922}.card.skip .num{color:#d29922}\n");
        sb.append(".card.total{border-color:#58a6ff}.card.total .num{color:#58a6ff}\n");
        sb.append("table{width:100%;border-collapse:collapse;margin-bottom:16px}\n");
        sb.append("th,td{padding:10px 12px;text-align:left;border-bottom:1px solid #21262d}\n");
        sb.append("th{background:#161b22;color:#8b949e;font-weight:600;font-size:12px;");
        sb.append("text-transform:uppercase;position:sticky;top:0}\n");
        sb.append(".source-row{cursor:pointer;transition:background .15s}\n");
        sb.append(".source-row:hover{background:#1c2128}\n");
        sb.append(".status-pass td:first-child{border-left:3px solid #3fb950}\n");
        sb.append(".status-fail td:first-child{border-left:3px solid #f85149}\n");
        sb.append(".status-skip td:first-child{border-left:3px solid #d29922}\n");
        sb.append(".status-cell{font-weight:600;font-size:13px}\n");
        sb.append(".badge{display:inline-block;padding:2px 8px;border-radius:12px;");
        sb.append("font-size:11px;font-weight:600}\n");
        sb.append(".badge-pass{background:rgba(63,185,80,0.15);color:#3fb950}\n");
        sb.append(".badge-fail{background:rgba(248,81,73,0.15);color:#f85149}\n");
        sb.append(".badge-skip{background:rgba(210,153,34,0.15);color:#d29922}\n");
        sb.append(".badge-error{background:rgba(248,81,73,0.15);color:#f85149}\n");
        sb.append(".duration{font-family:'SF Mono',monospace;font-size:12px;color:#8b949e}\n");
        sb.append(".test-detail{color:#8b949e;font-size:12px;word-break:break-all}\n");
        sb.append(".test-error{color:#f85149;font-size:12px;word-break:break-all}\n");
        sb.append(".details-row td{padding:0;background:#0d1117}\n");
        sb.append(".details-row .test-table{margin:0}\n");
        sb.append(".details-row .test-table th{background:#161b22}\n");
        sb.append("code{font-family:'SF Mono',monospace;font-size:12px;color:#8b949e}\n");
        sb.append(".footer{text-align:center;color:#484f58;font-size:12px;margin-top:30px;");
        sb.append("padding-top:16px;border-top:1px solid #21262d}\n");
        sb.append(".progress-bar{background:#21262d;border-radius:8px;height:8px;");
        sb.append("margin:8px 0 16px;overflow:hidden}\n");
        sb.append(".progress-fill{height:100%;border-radius:8px;");
        sb.append("background:linear-gradient(90deg,#3fb950,#58a6ff);transition:width .5s}\n");
        sb.append(".legend{display:flex;gap:16px;margin-bottom:16px;font-size:12px;color:#8b949e}\n");
        sb.append("@media(max-width:600px){.summary-cards{grid-template-columns:repeat(2,1fr)}");
        sb.append("td,th{padding:8px;font-size:13px}}\n");
        sb.append("</style>\n");
        sb.append("</head>\n<body>\n<div class=\"container\">\n");

        // Title
        sb.append("<h1>📊 XCimoc 漫画源测试报告</h1>\n");
        sb.append("<div class=\"meta\">🕐 ").append(escapeHtml(timestamp));
        sb.append(" &nbsp;|&nbsp; ⏱ 耗时 ").append(String.format("%.1f", durationMs / 1000.0));
        sb.append(" 秒</div>\n");

        // Summary cards
        sb.append("<div class=\"summary-cards\">\n");
        sb.append("<div class=\"card total\"><div class=\"num\">").append(totalSources).append("</div>");
        sb.append("<div class=\"label\">漫画源总数</div></div>\n");
        sb.append("<div class=\"card pass\"><div class=\"num\">").append(passedSources).append("</div>");
        sb.append("<div class=\"label\">通过 ✓</div></div>\n");
        sb.append("<div class=\"card fail\"><div class=\"num\">").append(failedSources).append("</div>");
        sb.append("<div class=\"label\">失败 ✗</div></div>\n");
        sb.append("<div class=\"card skip\"><div class=\"num\">").append(skippedSources).append("</div>");
        sb.append("<div class=\"label\">跳过 ⏭</div></div>\n");
        sb.append("</div>\n");

        // Test details
        sb.append("<h2>📋 测试详情</h2>\n");
        sb.append("<div class=\"legend\">");
        sb.append("<span>🟢 通过 (").append(passedTests).append("/").append(totalTests).append(")</span>");
        sb.append("<span>🔴 失败 (").append(failedTests).append(")</span>");
        sb.append("<span>🟡 跳过 (").append(skippedTests).append(")</span>");
        sb.append("<span>📈 通过率 ").append(String.format("%.1f", testPassRate)).append("%</span>");
        sb.append("</div>\n");
        sb.append("<div class=\"progress-bar\"><div class=\"progress-fill\" style=\"width:");
        sb.append(String.format("%.1f", passRate)).append("%\"></div></div>\n");

        // Table header
        sb.append("<table>\n<thead>\n<tr>");
        sb.append("<th>TYPE</th><th>源名称</th><th>Base URL</th><th>状态</th><th>通过/总数</th>");
        sb.append("</tr>\n</thead>\n<tbody>\n");

        // Table rows
        for (SourceReport sr : reports) {
            String statusIcon = "pass".equals(sr.overall) ? "✅" :
                                "fail".equals(sr.overall) ? "❌" : "⏭️";

            sb.append("<tr class=\"source-row status-").append(sr.overall);
            sb.append("\" onclick=\"toggleDetails('d").append(sr.typeId).append("')\">\n");
            sb.append("<td>").append(sr.typeId).append("</td>\n");
            sb.append("<td><strong>").append(escapeHtml(sr.title)).append("</strong></td>\n");
            sb.append("<td><code>").append(escapeHtml(sr.baseUrl)).append("</code></td>\n");
            sb.append("<td class=\"status-cell\">").append(statusIcon).append(" ");
            sb.append(sr.overall.toUpperCase()).append("</td>\n");
            sb.append("<td>").append(sr.passed()).append("/").append(sr.total()).append("</td>\n");
            sb.append("</tr>\n");

            // Detail row (hidden)
            sb.append("<tr id=\"d").append(sr.typeId).append("\" class=\"details-row\" style=\"display:none\">\n");
            sb.append("<td colspan=\"5\">\n");
            sb.append("<table class=\"test-table\">\n<thead>\n<tr>");
            sb.append("<th>测试项</th><th>结果</th><th>耗时</th><th>详情</th>");
            sb.append("</tr>\n</thead>\n<tbody>\n");

            for (TestResult tr : sr.results) {
                String badge = "pass".equals(tr.status) ? "badge-pass" :
                               "fail".equals(tr.status) ? "badge-fail" :
                               "skip".equals(tr.status) ? "badge-skip" : "badge-error";
                String badgeText = "pass".equals(tr.status) ? "通过" :
                                   "fail".equals(tr.status) ? "失败" :
                                   "skip".equals(tr.status) ? "跳过" : "错误";

                sb.append("<tr>\n");
                sb.append("<td>").append(escapeHtml(tr.name)).append("</td>\n");
                sb.append("<td><span class=\"badge ").append(badge).append("\">");
                sb.append(badgeText).append("</span></td>\n");
                sb.append("<td class=\"duration\">").append(tr.durationMs).append("ms</td>\n");
                sb.append("<td>");
                if (tr.detail != null && !tr.detail.isEmpty()) {
                    sb.append("<div class=\"test-detail\">").append(escapeHtml(tr.detail)).append("</div>");
                }
                if (tr.error != null && !tr.error.isEmpty()) {
                    sb.append("<div class=\"test-error\">").append(escapeHtml(tr.error)).append("</div>");
                }
                sb.append("</td>\n</tr>\n");
            }

            sb.append("</tbody>\n</table>\n</td>\n</tr>\n");
        }

        sb.append("</tbody>\n</table>\n");

        // Footer
        sb.append("<div class=\"footer\">");
        sb.append("XCimoc Source Tester — Java 源码测试 · 由 GitHub Actions 自动生成");
        sb.append("</div>\n");

        // JS
        sb.append("<script>\n");
        sb.append("function toggleDetails(id){var r=document.getElementById(id);");
        sb.append("r.style.display=r.style.display==='none'?'table-row':'none';}\n");
        sb.append("</script>\n");

        sb.append("</div>\n</body>\n</html>\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  主入口
    // ═══════════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        // 解析参数
        Set<Integer> filterTypes = new HashSet<>();
        boolean verbose = false;
        String outputDir = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--source":
                case "-s":
                    if (i + 1 < args.length) {
                        for (String s : args[++i].split(",")) {
                            filterTypes.add(Integer.parseInt(s.trim()));
                        }
                    }
                    break;
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                case "--output-dir":
                case "-o":
                    if (i + 1 < args.length) outputDir = args[++i];
                    break;
            }
        }

        // 选择源
        List<SourceEntry> sources = new ArrayList<>();
        for (SourceEntry se : ALL_SOURCES) {
            if (filterTypes.isEmpty() || filterTypes.contains(se.type)) {
                sources.add(se);
            }
        }

        // 初始化 App（确保静态方法可用）
        new App();

        // 打印自定义配置摘要
        if (!SOURCE_OVERRIDES.isEmpty()) {
            System.out.println("  📋 自定义配置:");
            for (Map.Entry<Integer, SourceOverride> e : SOURCE_OVERRIDES.entrySet()) {
                String name = "?";
                for (SourceEntry se : ALL_SOURCES) {
                    if (se.type == e.getKey()) { name = se.title; break; }
                }
                SourceOverride ov = e.getValue();
                String urlInfo = "";
                if (ov.connectUrl != null) urlInfo = ", 连通URL=\"" + ov.connectUrl + "\"";
                System.out.printf("     TYPE %d (%s): 关键词=%s, 漫画ID=%s%s\n",
                    e.getKey(), name,
                    ov.keyword != null ? "\"" + ov.keyword + "\"" : "(默认轮换)",
                    ov.comicId != null ? "\"" + ov.comicId + "\"" : "(自动提取)",
                    urlInfo);
            }
            System.out.println();
        }

        System.out.println("🔍 开始测试 " + sources.size() + " 个漫画源 (Java)...\n");

        List<SourceReport> reports = new ArrayList<>();
        long startTime = System.nanoTime();

        for (int i = 0; i < sources.size(); i++) {
            SourceEntry entry = sources.get(i);
            System.out.printf("  [%d/%d] %s (TYPE=%d)... ", i + 1, sources.size(), entry.title, entry.type);
            System.out.flush();

            SourceReport report = testSource(entry, verbose);
            reports.add(report);

            String statusSymbol = "pass".equals(report.overall) ? "✓" :
                                  "fail".equals(report.overall) ? "✗" : "⤬";
            System.out.printf("%s  %d/%d 通过\n", statusSymbol, report.passed(), report.total());

            if (verbose && report.failed() > 0) {
                for (TestResult tr : report.results) {
                    if ("fail".equals(tr.status) || "error".equals(tr.status)) {
                        System.out.println("    - " + tr.name + ": " + (tr.error != null ? tr.error : tr.detail));
                    }
                }
            }
        }

        long elapsed = (System.nanoTime() - startTime) / 1_000_000;

        // 输出摘要
        long totalPassed = reports.stream().filter(r -> "pass".equals(r.overall)).count();
        long totalFailed = reports.stream().filter(r -> "fail".equals(r.overall)).count();
        long testPassed = reports.stream().mapToInt(r -> r.passed()).sum();
        long testFailed = reports.stream().mapToInt(r -> r.failed()).sum();

        System.out.printf("\n%s\n", "=".repeat(50));
        System.out.printf("📊 测试完成! 耗时: %.1f 秒\n", elapsed / 1000.0);
        System.out.printf("   源: %d 通过 / %d 失败 / %d 跳过\n",
            totalPassed, totalFailed, reports.size() - totalPassed - totalFailed);
        System.out.printf("   用例: %d 通过 / %d 失败 / %d 跳过\n",
            testPassed, testFailed,
            reports.stream().mapToInt(r -> r.total()).sum() - testPassed - testFailed);

        // 输出 JSON + HTML 报告
        String json = toJson(reports, elapsed);
        String html = toHtml(reports, elapsed);

        if (outputDir != null) {
            new File(outputDir).mkdirs();
            try (PrintWriter pw = new PrintWriter(new File(outputDir, "report-java.json"), "UTF-8")) {
                pw.println(json);
            }
            System.out.println("  JSON 报告已写入: " + outputDir + "/report-java.json");
            try (PrintWriter pw = new PrintWriter(new File(outputDir, "report.html"), "UTF-8")) {
                pw.println(html);
            }
            System.out.println("  HTML 报告已写入: " + outputDir + "/report.html");
            // 同时写入 docs/ 目录（GitHub Pages 使用）
            // 优先: outputDir 的兄弟目录 docs/；其次: 上级的上级的 docs/
            File docsDir = null;
            if (new File(outputDir).getParentFile() != null) {
                docsDir = new File(new File(outputDir).getParentFile(), "docs");
                if (!docsDir.isDirectory()) {
                    // 尝试上级目录的 docs/（处理 Gradle 子项目工作目录的情况）
                    File grandParent = new File(outputDir).getParentFile().getParentFile();
                    if (grandParent != null) docsDir = new File(grandParent, "docs");
                }
            }
            if (docsDir == null || !docsDir.isDirectory()) {
                docsDir = new File("../docs");
            }
            if (docsDir.isDirectory()) {
                try (PrintWriter pw = new PrintWriter(new File(docsDir, "report.html"), "UTF-8")) {
                    pw.println(html);
                }
                System.out.println("  HTML 报告已同步到: " + docsDir + "/report.html");
            }
        } else {
            System.out.println("\n" + json);
        }

        // 非零退出码表示有失败
        if (totalFailed > 0) {
            System.exit(0);  // 不阻止 CI
        }
    }
}
