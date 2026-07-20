package com.xyrlsz.xcimocob.helper;

import android.content.Context;
import android.util.Pair;

import com.xyrlsz.opencc.android.lib.ChineseConverter;
import com.xyrlsz.xcimocob.BuildConfig;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.source.Baozi;
import com.xyrlsz.xcimocob.source.BuKa;
import com.xyrlsz.xcimocob.source.CopyMH;
import com.xyrlsz.xcimocob.source.CopyMHWeb;
import com.xyrlsz.xcimocob.source.DM5;
import com.xyrlsz.xcimocob.source.DongManHi;
import com.xyrlsz.xcimocob.source.DongManManHua;
import com.xyrlsz.xcimocob.source.DuManWu;
import com.xyrlsz.xcimocob.source.DuManWuApp;
import com.xyrlsz.xcimocob.source.GFMH;
import com.xyrlsz.xcimocob.source.GoDaManHua;
import com.xyrlsz.xcimocob.source.HotManga;
import com.xyrlsz.xcimocob.source.Komiic;
import com.xyrlsz.xcimocob.source.MH5;
import com.xyrlsz.xcimocob.source.MYCOMIC;
import com.xyrlsz.xcimocob.source.ManBen;
import com.xyrlsz.xcimocob.source.ManHuaGui;
import com.xyrlsz.xcimocob.source.ManWa;
import com.xyrlsz.xcimocob.source.MangaBZ;
import com.xyrlsz.xcimocob.source.Manhuatai;
import com.xyrlsz.xcimocob.source.Manhuayu;
import com.xyrlsz.xcimocob.source.Tencent;
import com.xyrlsz.xcimocob.source.Vomicmh;
import com.xyrlsz.xcimocob.source.YKMH;
import com.xyrlsz.xcimocob.source.YYManHua;
import com.xyrlsz.xcimocob.source.ZaiManhua;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.objectbox.Box;
import io.objectbox.BoxStore;

/**
 * Created by Hiroshi on 2017/1/18.
 */

public class UpdateHelper {
    // 1.04.08.008
    private static final int VERSION = BuildConfig.VERSION_CODE;

    private static final Map<Integer, Source> ComicSourceTable = new HashMap<>();

    public static Map<Integer, Source> getComicSourceTable() {
        if (ComicSourceTable.isEmpty()) {
            initComicSourceTable();
        }
        return ComicSourceTable;
    }

    /**
     * 初始化图源
     */
    private static void initComicSourceTable() {
        if (ComicSourceTable.isEmpty()) {
            //            ComicSourceTable.put(Animx2.TYPE, Animx2.getDefaultSource());
            ComicSourceTable.put(Baozi.TYPE, Baozi.getDefaultSource());
            ComicSourceTable.put(BuKa.TYPE, BuKa.getDefaultSource());
            //            ComicSourceTable.put(Cartoonmad.TYPE, Cartoonmad.getDefaultSource());
            ComicSourceTable.put(CopyMH.TYPE, CopyMH.getDefaultSource());
            ComicSourceTable.put(DM5.TYPE, DM5.getDefaultSource());
            ComicSourceTable.put(HotManga.TYPE, HotManga.getDefaultSource());
            ComicSourceTable.put(ManHuaGui.TYPE, ManHuaGui.getDefaultSource());
            //            ComicSourceTable.put(Mangakakalot.TYPE, Mangakakalot.getDefaultSource());
            ComicSourceTable.put(MangaBZ.TYPE, MangaBZ.getDefaultSource());
            ComicSourceTable.put(Manhuatai.TYPE, Manhuatai.getDefaultSource());
            ComicSourceTable.put(MYCOMIC.TYPE, MYCOMIC.getDefaultSource());
            ComicSourceTable.put(Tencent.TYPE, Tencent.getDefaultSource());
            ComicSourceTable.put(DongManManHua.TYPE, DongManManHua.getDefaultSource());
            ComicSourceTable.put(YKMH.TYPE, YKMH.getDefaultSource());
            ComicSourceTable.put(DuManWu.TYPE, DuManWu.getDefaultSource());
            //            ComicSourceTable.put(DuManWuOrg.TYPE, DuManWuOrg.getDefaultSource());
            ComicSourceTable.put(Komiic.TYPE, Komiic.getDefaultSource());
            ComicSourceTable.put(Manhuayu.TYPE, Manhuayu.getDefaultSource());
            ComicSourceTable.put(GoDaManHua.TYPE, GoDaManHua.getDefaultSource());
            //            ComicSourceTable.put(TTKMH.TYPE, TTKMH.getDefaultSource());
            ComicSourceTable.put(Vomicmh.TYPE, Vomicmh.getDefaultSource());
            ComicSourceTable.put(YYManHua.TYPE, YYManHua.getDefaultSource());
            //            ComicSourceTable.put(DmzjV4.TYPE, DmzjV4.getDefaultSource());
            ComicSourceTable.put(ZaiManhua.TYPE, ZaiManhua.getDefaultSource());
            ComicSourceTable.put(ManBen.TYPE, ManBen.getDefaultSource());
            ComicSourceTable.put(GFMH.TYPE, GFMH.getDefaultSource());
            ComicSourceTable.put(ManWa.TYPE, ManWa.getDefaultSource());
            ComicSourceTable.put(MH5.TYPE, MH5.getDefaultSource());
            ComicSourceTable.put(DuManWuApp.TYPE, DuManWuApp.getDefaultSource());
            ComicSourceTable.put(CopyMHWeb.TYPE, CopyMHWeb.getDefaultSource());
            ComicSourceTable.put(DongManHi.TYPE, DongManHi.getDefaultSource());
        }
    }

    public static void update(PreferenceManager manager, final BoxStore boxStore, Context context) {
        int version = manager.getNumber(PreferenceManager.PREF_APP_VERSION, 0).intValue();

        if (version != VERSION) {
            // ObjectBox会自动处理 schema 变更，不需要手动添加列
            initComicSourceTable();

            // 数据清洗：删除重复的 Chapter 和 Comic（保留唯一组合）
            if (version <= 1508) {
                cleanupDuplicateChapters(boxStore);
                cleanupDuplicateComics(boxStore);
            }

            manager.putNumber(PreferenceManager.PREF_APP_VERSION, VERSION);
            updateComicSource(boxStore);
            ChineseConverter.clearDictDataFolder(context);
            ChineseConverter.init(context);
        }
    }

    /**
     * 清洗重复的 Chapter 数据：
     * 1. 按 sourceComic + path 分组，每组只保留 id 最小的那条
     * 2. 为所有记录补填 sourceComicPath 字段（新增的 @Unique 字段）
     */
    private static void cleanupDuplicateChapters(BoxStore boxStore) {
        Box<Chapter> chapterBox = boxStore.boxFor(Chapter.class);
        List<Chapter> allChapters = chapterBox.getAll();

        if (allChapters.isEmpty()) {
            return;
        }

        // 按 sourceComic + path 分组
        Map<String, List<Chapter>> groups = new HashMap<>();
        for (Chapter chapter : allChapters) {
            String key = chapter.getSourceComic() + "_" + chapter.getPath();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(chapter);
        }

        List<Chapter> toRemove = new ArrayList<>();
        List<Chapter> toUpdate = new ArrayList<>();

        for (List<Chapter> group : groups.values()) {
            // 按 id 升序排序，保留第一条
            group.sort((a, b) -> Long.compare(a.getId(), b.getId()));
            Chapter keep = group.get(0);

            // 补填 sourceComicPath
            if (keep.getSourceComicPath() == null) {
                keep.setSourceComicPath(new Pair<>(keep.getSourceComic(), keep.getPath()));
                toUpdate.add(keep);
            }

            // 删除多余的重复记录
            for (int i = 1; i < group.size(); i++) {
                toRemove.add(group.get(i));
            }
        }

        if (!toRemove.isEmpty()) {
            chapterBox.remove(toRemove);
        }
        if (!toUpdate.isEmpty()) {
            chapterBox.put(toUpdate);
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
            group.sort((a, b) -> Long.compare(a.getId(), b.getId()));
            // 删除多余的重复记录
            for (int i = 1; i < group.size(); i++) {
                toRemove.add(group.get(i));
            }
        }

        if (!toRemove.isEmpty()) {
            comicBox.remove(toRemove);
        }
    }

    private static void updateComicSource(BoxStore boxStore) {
        Box<Source> sourceBox = boxStore.boxFor(Source.class);
        List<Source> sourceList = sourceBox.getAll();
        List<Source> sourcesToDelete = new ArrayList<>();
        List<Source> sourcesToAdd = new ArrayList<>();
        for (Source source : sourceList) {
            if (!ComicSourceTable.containsKey(source.getType())) {
                sourcesToDelete.add(source);
            }
        }
        for (Integer cType : ComicSourceTable.keySet()) {
            boolean isExist = false;
            for (Source source : sourceList) {
                if (source.getType() == cType) {
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                sourcesToAdd.add(ComicSourceTable.get(cType));
            }
        }
        if (!sourcesToDelete.isEmpty()) {
            sourceBox.remove(sourcesToDelete);
        }
        if (!sourcesToAdd.isEmpty()) {
            sourceBox.put(sourcesToAdd);
        }
        sourceList = sourceBox.getAll();
        for (Source source : sourceList) {
            if (ComicSourceTable.containsKey(source.getType())) {
                Source sourceToUpdate = ComicSourceTable.get(source.getType());
                if (sourceToUpdate != null) {
                    String title1 = source.getTitle();
                    String title2 = sourceToUpdate.getTitle();
                    String baseUrl1 = source.getBaseUrl();
                    String baseUrl2 = sourceToUpdate.getBaseUrl();

                    boolean titleDiff = (title1 == null && title2 != null)
                            || (title1 != null && !title1.equals(title2));
                    boolean baseUrlDiff = (baseUrl1 == null && baseUrl2 != null)
                            || (baseUrl1 != null && !baseUrl1.equals(baseUrl2));

                    if (titleDiff || baseUrlDiff) {
                        source.setTitle(title2);
                        source.setBaseUrl(baseUrl2);
                        sourceBox.put(source);
                    }
                }
            }
        }
    }
}
