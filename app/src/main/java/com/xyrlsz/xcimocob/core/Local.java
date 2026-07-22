package com.xyrlsz.xcimocob.core;

import android.util.Pair;

import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Task;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;
import com.xyrlsz.xcimocob.source.Locality;
import com.xyrlsz.xcimocob.utils.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Hiroshi on 2017/5/20.
 */

public class Local {

    private static Pattern chapterPattern = null;

    public static Observable<List<Pair<Comic, ArrayList<Task>>>> scan(final CimocDocumentFile root) {
        return Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<List<Pair<Comic, ArrayList<Task>>>>) emitter -> {
            List<Pair<Comic, ArrayList<Task>>> result = new ArrayList<>();

            ScanInfo info = new ScanInfo(root);
            countPicture(info);
            if (info.count > 5) {
                Pair<Comic, ArrayList<Task>> pair = Pair.create(buildComic(info.dir, info.cover), new ArrayList<>());
                pair.second.add(buildTask(info.dir, info.count, true));
                result.add(pair);
            } else {
                List<CimocDocumentFile> list = new LinkedList<>();
                list.add(root);

                while (!list.isEmpty()) {
                    CimocDocumentFile dir = list.get(0);

                    List<ScanInfo> guessChapter = new LinkedList<>();
                    List<ScanInfo> guessComic = new LinkedList<>();
                    List<CimocDocumentFile> guessOther = classify(guessChapter, guessComic, dir);

                    if (guessChapter.size() > 2 * guessComic.size()) {  // 章节
                        result.add(merge(dir, guessChapter, guessComic));
                    } else {    // 单章节漫画
                        split(guessChapter, result);
                        split(guessComic, result);
                        list.addAll(guessOther);
                    }

                    list.remove(0);
                }
            }
            emitter.onNext(result);
            emitter.onComplete();
        }).subscribeOn(Schedulers.io());
    }

    public static Observable<List<ImageUrl>> images(final CimocDocumentFile dir, final Chapter chapter) {
        return Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<List<ImageUrl>>) emitter -> {
            List<CimocDocumentFile> files = dir.listFiles(file -> file.isFile() && StringUtils.endWith(file.getName(), "jpg", "png", "jpeg", "webp"), Comparator.comparing(CimocDocumentFile::getName));
            List<ImageUrl> list = Storage.buildImageUrlFromDocumentFile(files, chapter.getTitle(), chapter.getCount(), chapter);
            if (!list.isEmpty()) {
                emitter.onNext(list);
                emitter.onComplete();
            } else {
                emitter.onError(new Exception("No local images found"));
            }
        }).subscribeOn(Schedulers.io());
    }

    private static void countPicture(ScanInfo info) {
        String name = null;
        int other = 0;
        for (CimocDocumentFile file : info.dir.listFiles()) {
            if (file.isFile() && StringUtils.endWith(file.getName(), "png", "jpg", "jpeg", "webp")) {
                ++info.count;
            } else {
                ++other;
            }
            if (name == null || file.getName().compareTo(name) < 0) {
                name = file.getName();
                info.cover = file.getUri().toString();
            }
            if (other > 5) {
                info.count = 0;
                break;
            }
        }
    }

    private static List<CimocDocumentFile> classify(List<ScanInfo> chapter,
                                                    List<ScanInfo> comic,
                                                    CimocDocumentFile dir) {
        List<CimocDocumentFile> other = new LinkedList<>();
        for (CimocDocumentFile file : dir.listFiles()) {
            if (file.isDirectory()) {
                ScanInfo info = new ScanInfo(file);
                countPicture(info);
                if (info.count > 5) {
                    if (isNameChapter(file)) {
                        chapter.add(info);
                    } else {
                        comic.add(info);
                    }
                } else {
                    other.add(file);
                }
            }
        }
        return other;
    }

    private static boolean isNameChapter(CimocDocumentFile file) {
        if (chapterPattern == null) {
            if (Character.isDigit(file.getName().charAt(0))) {
                chapterPattern = Pattern.compile("^\\d+(-.+)?$");
            } else {
                chapterPattern = Pattern.compile("^[^(\\[]{0,5}[0-9]+|[0-9]+.{0,5}$");
            }
        }
        Matcher matcher = chapterPattern.matcher(file.getName());
        return matcher.find() && ((float) matcher.group().length() / file.getName().length() > 0.8);
    }

    private static Comic buildComic(CimocDocumentFile dir, String cover) {
        return new Comic(null, Locality.TYPE, dir.getUri().toString(), dir.getName(), cover,
                false, true, null, null, null, null, null, null, null, null, null, 0, null, null);
    }

    private static Task buildTask(CimocDocumentFile dir, int count, boolean single) {
        return single ? new Task(null, -1, dir.getUri().toString(), "第01话", count, count) :
                new Task(null, -1, dir.getUri().toString(), dir.getName(), count, count);
    }

    private static Pair<Comic, ArrayList<Task>> merge(CimocDocumentFile dir, List<ScanInfo> list1, List<ScanInfo> list2) {
        String cover = findCover(dir);
        Pair<Comic, ArrayList<Task>> pair = cover != null ? Pair.create(buildComic(dir, cover), new ArrayList<>()) : Pair.create(buildComic(dir, list1.get(0).cover), new ArrayList<>());
        for (ScanInfo info : list1) {
            pair.second.add(buildTask(info.dir, info.count, false));
        }
        for (ScanInfo info : list2) {
            pair.second.add(buildTask(info.dir, info.count, false));
        }
        return pair;
    }

    private static void split(List<ScanInfo> list, List<Pair<Comic, ArrayList<Task>>> result) {
        for (ScanInfo info : list) {
            Pair<Comic, ArrayList<Task>> pair = Pair.create(buildComic(info.dir, info.cover), new ArrayList<Task>());
            pair.second.add(buildTask(info.dir, info.count, true));
            result.add(pair);
        }
    }

    private static String findCover(CimocDocumentFile dir) {
        for (CimocDocumentFile file : dir.listFiles()) {
            if (file.isFile()) {
                String name = file.getName().toLowerCase();
                if (name.equals("cover.jpg") || name.equals("cover.png") || name.equals("cover.jpeg") || name.startsWith("cover.")) {
                    return file.getUri().toString();
                }
            }
        }
        return null;
    }

    private static class ScanInfo {
        CimocDocumentFile dir;
        String cover = null;
        int count = 0;

        ScanInfo(CimocDocumentFile dir) {
            this.dir = dir;
        }
    }
}
