package com.xyrlsz.xcimocob.helper;

import android.content.Context;

import com.xyrlsz.opencc.android.lib.ChineseConverter;
import com.xyrlsz.xcimocob.BuildConfig;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.JsSource;
import com.xyrlsz.xcimocob.model.JsSource_;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.source.Locality;
import com.xyrlsz.xcimocob.source.Null;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.Query;

/**
 * Created by Hiroshi on 2017/1/18.
 */

public class UpdateHelper {
    // 1.04.08.008
    private static final int VERSION = BuildConfig.VERSION_CODE;

    /**
     * 内置 Java 漫画源已移除，网络漫画源全部由 JS 源（JsSourceManager）提供。
     * 该表保留为空，仅用于兼容旧的测试代码。
     */
    private static final Map<Integer, Source> ComicSourceTable = Collections.emptyMap();

    public static Map<Integer, Source> getComicSourceTable() {
        return ComicSourceTable;
    }

    public static void update(PreferenceManager manager, final BoxStore boxStore, Context context) {
        int version = manager.getNumber(PreferenceManager.PREF_APP_VERSION, 0).intValue();

        if (version != VERSION) {
            // ObjectBox会自动处理 schema 变更，不需要手动添加列
            // 数据清洗：删除重复的 Comic（保留唯一组合）
            if (version <= 1508) {
                cleanupDuplicateComics(boxStore);
            }

            // 清理旧版本残留的内置源（无对应 JS 源的网络源条目）
            cleanupStaleSources(boxStore);

            manager.putNumber(PreferenceManager.PREF_APP_VERSION, VERSION);
            ChineseConverter.clearDictDataFolder(context);
            ChineseConverter.init(context);
        }
    }

    /**
     * 删除 Source 表中不再有对应 JS 源的内置源残留（本地漫画 Locality 与兜底 Null 除外）。
     */
    private static void cleanupStaleSources(BoxStore boxStore) {
        Box<Source> sourceBox = boxStore.boxFor(Source.class);
        Box<JsSource> jsBox = boxStore.boxFor(JsSource.class);
        List<Source> toRemove = new ArrayList<>();
        for (Source source : sourceBox.getAll()) {
            int type = source.getType();
            if (type == Locality.TYPE || type == Null.TYPE) {
                continue;
            }
            try (Query<JsSource> query = jsBox.query().equal(JsSource_.type, type).build()) {
                if (query.findFirst() == null) {
                    toRemove.add(source);
                }
            }
        }
        if (!toRemove.isEmpty()) {
            sourceBox.remove(toRemove);
        }
    }

    /**
     * 清洗重复的 Comic 数据：
     * 按 source + cid 分组，每组只保留 id 最小的那条
     */
    private static void cleanupDuplicateComics(BoxStore boxStore) {
        Box<Comic> comicBox = boxStore.boxFor(Comic.class);
        List<Comic> allComics = comicBox.getAll();

        if (allComics.isEmpty()) {
            return;
        }

        // 按 source + cid 分组
        Map<String, List<Comic>> groups = new HashMap<>();
        for (Comic comic : allComics) {
            String key = comic.getSource() + "_" + comic.getCid();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(comic);
        }

        List<Comic> toRemove = new ArrayList<>();

        for (List<Comic> group : groups.values()) {
            // 按 id 升序排序，保留第一条
            group.sort(Comparator.comparingLong(Comic::getId));
            // 删除多余的重复记录
            for (int i = 1; i < group.size(); i++) {
                toRemove.add(group.get(i));
            }
        }

        if (!toRemove.isEmpty()) {
            comicBox.remove(toRemove);
        }
    }
}
