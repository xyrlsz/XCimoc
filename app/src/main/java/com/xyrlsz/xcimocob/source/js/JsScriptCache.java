package com.xyrlsz.xcimocob.source.js;

import android.util.Log;

import com.xyrlsz.quickjs.QuickJSEngine;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.utils.BinStreamUtils;
import com.xyrlsz.xcimocob.utils.HashUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 「源 SDK + JS 源脚本」求值入口，带 QuickJS 字节码缓存。
 * <p>
 * 每次创建引擎都要先求值 {@code assets/source_sdk.js}（约 64KB）与源脚本，
 * 解析 + 编译这段代码是创建引擎的主要开销。这里把两者用
 * {@link QuickJSEngine#compileToBytecode} 预编译后落盘到应用 cache 目录，
 * 后续创建引擎直接 {@link QuickJSEngine#evaluateBytecode} 加载，跳过解析编译；
 * 只有 SDK 或脚本内容变化（缓存 key 变化）时才重新编译一次。
 * <p>
 * 缓存按内容寻址（key 含 {@code FORMAT_VERSION}、QuickJS 版本与脚本原文），
 * 命中失败（文件损坏、版本不匹配）或读盘失败时回退到源码求值，不会影响功能。
 * <p>
 * 安全提示：QuickJS 字节码格式不防御恶意输入，因此缓存只由本机已入库的脚本文本编译产生，
 * 绝不从网络下载字节码。
 */
public final class JsScriptCache {

    private static final String TAG = "JsScriptCache";

    /**
     * 缓存格式版本：编译策略或 key 组成变化时递增，使旧缓存整体失效。
     */
    private static final int FORMAT_VERSION = 1;

    private static final String SDK_ASSET = "source_sdk.js";
    private static final String DIR_NAME = "jsbc";
    private static final String PREFIX = "bc-";
    private static final String SUFFIX = ".qbc";
    /**
     * 缓存文件数量上限：每个源 + SDK 各一份，多出的旧版本（源更新后遗留）按最后修改时间淘汰。
     */
    private static final int MAX_FILES = 64;

    private static volatile String sSdk;
    private static volatile String sKeyPrefix;
    private static volatile File sSdkCacheFile;

    private JsScriptCache() {
    }

    /**
     * @return 源 SDK 文本（{@code assets/source_sdk.js}），进程内只读取一次。
     */
    public static String getSdk() {
        String sdk = sSdk;
        if (sdk == null) {
            try {
                sdk = new String(
                        BinStreamUtils.readAllBytesCompat(
                                App.getAppContext().getAssets().open(SDK_ASSET)),
                        StandardCharsets.UTF_8);
            } catch (Exception e) {
                Log.e(TAG, "read " + SDK_ASSET + " failed: " + e);
                sdk = "";
            }
            sSdk = sdk;
        }
        return sdk;
    }

    /**
     * 把 SDK 与源脚本依次求值进 engine（等价于旧实现的 {@code evaluate(SDK + "\n" + script)}），
     * 两者各自优先走字节码缓存。
     *
     * @param engine 目标引擎，未求值过 SDK 的新实例
     * @param script 源脚本原文
     * @return 脚本的求值结果（失败时为空串）
     */
    public static String evaluate(QuickJSEngine engine, String script) {
        execute(engine, getSdk(), sdkCacheFile());
        return execute(engine, script == null ? "" : script, cacheFile(script == null ? "" : script));
    }

    /**
     * 求值单个脚本：命中缓存直接加载字节码，未命中则编译（顺带写缓存）后执行。
     *
     * @param cache 该脚本的缓存文件，null 表示不可用（直接求值源码，不缓存）
     */
    private static String execute(QuickJSEngine engine, String source, File cache) {
        if (cache != null && cache.isFile()) {
            byte[] bytecode = read(cache);
            if (bytecode != null) {
                String result = engine.evaluateBytecode(bytecode);
                if (result != null) {
                    return result;
                }
                Log.w(TAG, "bytecode rejected, recompiling: " + cache.getName());
                delete(cache);
            }
        }
        // 未命中：先编译再执行，避免"求值一次 + 再编译一次"的重复解析
        byte[] compiled = engine.compileToBytecode(source, "<source>");
        if (compiled == null) {
            // 编译失败（语法错误等）：退回源码求值路径，保留原有的报错日志与返回值语义
            return engine.evaluate(source);
        }
        String result = engine.evaluateBytecode(compiled);
        if (result == null) {
            Log.w(TAG, "compiled bytecode failed to run, falling back to source");
            return engine.evaluate(source);
        }
        write(cache, compiled);
        return result;
    }

    /**
     * 缓存 key = FORMAT_VERSION | QuickJS 版本 | 脚本原文，按内容寻址：
     * SDK 或脚本任一改动、引擎升级都会自动换文件，旧文件由 {@link #prune(File, File)} 淘汰。
     */
    private static File cacheFile(String source) {
        File dir = cacheDir();
        if (dir == null) {
            return null;
        }
        String hash = HashUtils.MD5(keyPrefix() + source);
        return hash == null ? null : new File(dir, PREFIX + hash + SUFFIX);
    }

    /**
     * SDK 的缓存文件只算一次：它是全进程共用的一份，避免每次创建引擎都哈希 64KB 文本。
     */
    private static File sdkCacheFile() {
        File file = sSdkCacheFile;
        if (file == null) {
            file = cacheFile(getSdk());
            if (file != null) {
                sSdkCacheFile = file;
            }
        }
        return file;
    }

    private static String keyPrefix() {
        String prefix = sKeyPrefix;
        if (prefix == null) {
            String version;
            try {
                version = QuickJSEngine.getVersion();
            } catch (Throwable t) {
                version = "unknown";
            }
            prefix = FORMAT_VERSION + "|" + version + "|";
            sKeyPrefix = prefix;
        }
        return prefix;
    }

    private static File cacheDir() {
        try {
            File dir = new File(App.getAppContext().getCacheDir(), DIR_NAME);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            return dir;
        } catch (Throwable t) {
            Log.w(TAG, "cache dir unavailable: " + t);
            return null;
        }
    }

    private static byte[] read(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return BinStreamUtils.readAllBytesCompat(in);
        } catch (Throwable t) {
            Log.w(TAG, "read cache failed: " + file.getName() + ", " + t);
            return null;
        }
    }

    /**
     * 先写临时文件再 rename：写到一半被打断也不会留下被误加载的半个字节码。
     * 临时文件名唯一，避免多线程同时首次编译同一脚本时互相覆盖。
     */
    private static void write(File file, byte[] data) {
        if (file == null) {
            return;
        }
        File dir = file.getParentFile();
        File tmp = null;
        try {
            tmp = File.createTempFile(PREFIX, ".tmp", dir);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
            }
        } catch (Throwable t) {
            Log.w(TAG, "write cache failed: " + file.getName() + ", " + t);
            if (tmp != null) {
                delete(tmp);
            }
            return;
        }
        if (!tmp.renameTo(file)) {
            Log.w(TAG, "rename cache failed: " + file.getName());
            delete(tmp);
            return;
        }
        prune(dir, file);
    }

    private static void delete(File file) {
        try {
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "delete failed: " + file.getName());
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * 淘汰超出上限的旧缓存（源更新会留下上一个版本的字节码）。
     * 只统计 / 删除 {@code .qbc}，不碰其他线程正在写入的临时文件。
     */
    private static void prune(File dir, File keep) {
        File[] files = dir == null ? null : dir.listFiles(
                (d, name) -> name.startsWith(PREFIX) && name.endsWith(SUFFIX));
        if (files == null || files.length <= MAX_FILES) {
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        int excess = files.length - MAX_FILES;
        for (File file : files) {
            if (excess <= 0) {
                break;
            }
            if (!file.equals(keep)) {
                delete(file);
                excess--;
            }
        }
    }
}
