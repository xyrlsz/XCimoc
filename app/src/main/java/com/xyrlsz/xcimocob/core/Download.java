package com.xyrlsz.xcimocob.core;

import android.content.ContentResolver;
import android.util.Pair;

import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Task;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;
import com.xyrlsz.xcimocob.utils.DecryptionUtils;
import com.xyrlsz.xcimocob.utils.DocumentUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Hiroshi on 2016/9/9.
 */
public class Download {

    /**
     * version 1 [1.4.3.0, ...)
     * comic:
     * {
     * list: 章节列表 array
     * [{
     * title: 章节名称 string
     * path: 章节路径 string
     * }]
     * source: 图源 int
     * cid: 漫画ID string
     * title: 标题 string
     * cover: 封面 string
     * type: 类型 string ("comic")
     * version: 版本 string ("1")
     * }
     * chapter:
     * {
     * title: 章节名称 string
     * path: 章节路径 string
     * type: 类型 string ("chapter")
     * version: 版本 string ("1")
     * }
     * <p>
     * version 2 [遥遥无期, 遥遥无期)
     * comic:
     * {
     * list: 章节列表 array
     * [ 章节路径 string ]
     * source: 图源 int
     * cid: 漫画ID string
     * title: 标题 string
     * cover: 封面 string
     * type: 类型 int (1)
     * version: 版本 int (2)
     * }
     * chapter:
     * {
     * title: 章节名称 string
     * path: 章节路径 string
     * max: 总页数 int
     * list: 图片列表 array
     * [ 文件名 string ]
     * type: 类型 int (2)
     * version: 版本 int (2)
     * }
     */

    private static final String JSON_KEY_VERSION = "version";
    private static final String JSON_KEY_TYPE = "type";
    private static final String JSON_KEY_TYPE_COMIC = "comic";
    private static final String JSON_KEY_TYPE_CHAPTER = "chapter";
    private static final String JSON_KEY_COMIC_LIST = "list";
    private static final String JSON_KEY_COMIC_SOURCE = "source";
    private static final String JSON_KEY_COMIC_CID = "cid";
    private static final String JSON_KEY_COMIC_TITLE = "title";
    private static final String JSON_KEY_COMIC_COVER = "cover";
    private static final String JSON_KEY_CHAPTER_PATH = "path";
    private static final String JSON_KEY_CHAPTER_TITLE = "title";

    private static final String DOWNLOAD = "download";
    private static final String FILE_INDEX = "index.cdif";
    private static final String NO_MEDIA = ".nomedia";

    private static void createNoMedia(CimocDocumentFile root) {
        CimocDocumentFile home = DocumentUtils.getOrCreateSubDirectory(root, DOWNLOAD);
        DocumentUtils.getOrCreateFile(Objects.requireNonNull(home), NO_MEDIA);
    }

    private static CimocDocumentFile createComicIndex(CimocDocumentFile root, Comic comic) {
        CimocDocumentFile home = DocumentUtils.getOrCreateSubDirectory(root, DOWNLOAD);
        CimocDocumentFile source = DocumentUtils.getOrCreateSubDirectory(Objects.requireNonNull(home), String.valueOf(comic.getSource()));
        CimocDocumentFile dir = DocumentUtils.getOrCreateSubDirectory(Objects.requireNonNull(source), comic.getCid());
        if (dir != null) {
            return DocumentUtils.getOrCreateFile(dir, FILE_INDEX);
        }
        return null;
    }

    /**
     * 写漫画索引，不关心是否成功，若没有索引文件，则不能排序章节及扫描恢复漫画，但不影响下载及观看
     *
     * @param list
     * @param comic
     */
    public static void updateComicIndex(ContentResolver resolver, CimocDocumentFile root, List<Chapter> list, Comic comic) {
        try {
            createNoMedia(root);
            String jsonString = getJsonFromComic(list, comic);
            CimocDocumentFile file = createComicIndex(root, comic);
            DocumentUtils.writeStringToFile(resolver, Objects.requireNonNull(file), "cimoc".concat(jsonString));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getJsonFromComic(List<Chapter> list, Comic comic) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(JSON_KEY_VERSION, "1");
        object.put(JSON_KEY_TYPE, JSON_KEY_TYPE_COMIC);
        object.put(JSON_KEY_COMIC_SOURCE, comic.getSource());
        object.put(JSON_KEY_COMIC_CID, comic.getCid());
        object.put(JSON_KEY_COMIC_TITLE, comic.getTitle());
        object.put(JSON_KEY_COMIC_COVER, comic.getCover());
        JSONArray array = new JSONArray();
        for (Chapter chapter : list) {
            JSONObject temp = new JSONObject();
            temp.put(JSON_KEY_CHAPTER_TITLE, chapter.getTitle());
            temp.put(JSON_KEY_CHAPTER_PATH, chapter.getPath());
            array.put(temp);
        }
        object.put(JSON_KEY_COMIC_LIST, array);
        return object.toString();
    }

    public static CimocDocumentFile updateChapterIndex(ContentResolver resolver, CimocDocumentFile root, Task task) {
        try {
            String jsonString = getJsonFromChapter(task.getTitle(), task.getPath());
            CimocDocumentFile dir1 = DocumentUtils.getOrCreateSubDirectory(root, DOWNLOAD);
            CimocDocumentFile dir2 = DocumentUtils.getOrCreateSubDirectory(Objects.requireNonNull(dir1), String.valueOf(task.getSource()));
            CimocDocumentFile dir3 = DocumentUtils.getOrCreateSubDirectory(Objects.requireNonNull(dir2), task.getCid().replaceAll("[:/(\\\\)(\\?)<>\"(\\|)(\\.)]", ""));
            CimocDocumentFile dir4 = DocumentUtils.getOrCreateSubDirectory(Objects.requireNonNull(dir3), DecryptionUtils.urlDecrypt(task.getPath().replaceAll("[:/(\\\\)(\\?)<>\"(\\|)(\\.)]", "-")));
            if (dir4 != null) {
                CimocDocumentFile file = DocumentUtils.getOrCreateFile(dir4, FILE_INDEX);
                DocumentUtils.writeStringToFile(resolver, Objects.requireNonNull(file), "cimoc".concat(jsonString));
                return dir4;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getJsonFromChapter(String title, String path) throws JSONException {
        JSONObject object = new JSONObject();
        object.put(JSON_KEY_VERSION, "1");
        object.put(JSON_KEY_TYPE, JSON_KEY_TYPE_CHAPTER);
        object.put(JSON_KEY_CHAPTER_TITLE, title);
        object.put(JSON_KEY_CHAPTER_PATH, path);
        return object.toString();
    }

    /**
     * 1.4.4 以前位于 存储目录/download/图源名称/漫画名称
     * 1.4.4 以后位于 存储目录/download/图源ID/漫画ID
     *
     * @param root  存储目录
     * @param comic
     * @return
     */
    private static CimocDocumentFile getComicDir(CimocDocumentFile root, Comic comic, String title) {
        CimocDocumentFile result = DocumentUtils.findFile(root, DOWNLOAD, String.valueOf(comic.getSource()), comic.getCid());
        if (result == null) {
            result = DocumentUtils.findFile(root, DOWNLOAD, title, comic.getTitle());
        }
        return result;
    }

    public static List<String> getComicIndex(ContentResolver resolver, CimocDocumentFile root, Comic comic, String title) {
        CimocDocumentFile dir = getComicDir(root, comic, title);
        if (dir != null) {
            CimocDocumentFile file = dir.findFile(FILE_INDEX);
            if (file != null) {
                if (hasMagicNumber(resolver, file)) {
                    String jsonString = DocumentUtils.readLineFromFile(resolver, file);
                    if (jsonString != null) {
                        try {
                            return readPathFromJson(jsonString.substring(5));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<String> readPathFromJson(String jsonString) throws JSONException {
        JSONObject jsonObject = new JSONObject(jsonString);
        // We use "c" as the key in old version
        JSONArray array = jsonObject.has(JSON_KEY_COMIC_LIST) ? jsonObject.getJSONArray(JSON_KEY_COMIC_LIST) : jsonObject.getJSONArray("c");
        int size = array.length();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i != size; ++i) {
            JSONObject object = array.getJSONObject(i);
            list.add(object.has(JSON_KEY_CHAPTER_PATH) ? object.getString(JSON_KEY_CHAPTER_PATH) : object.getString("p"));
        }
        return list;
    }

    public static CimocDocumentFile getChapterDir(CimocDocumentFile root, Comic comic, Chapter chapter, String title) {
        CimocDocumentFile result = DocumentUtils.findFile(root, DOWNLOAD,
                String.valueOf(comic.getSource()),
                comic.getCid().replaceAll("[:/\\\\?<>\"|.]", ""),
                DecryptionUtils.urlDecrypt(chapter.getPath().replaceAll("[:/\\\\?<>\"|.]", "-"))
        );
        if (result == null) {
            result = DocumentUtils.findFile(root, DOWNLOAD, title, comic.getTitle(), chapter.getTitle());
        }
        return result;
    }

    //    public static Observable<List<ImageUrl>> images(final CimocDocumentFile root, final Comic comic, final Chapter chapter, final String title) {
//        return Observable.create((Observable.OnSubscribe<List<ImageUrl>>) subscriber -> {
//            CimocDocumentFile dir = getChapterDir(root, comic, chapter, title);
//            List<CimocDocumentFile> files = dir.listFiles(file -> !file.getName().endsWith("cdif"), new Comparator<CimocDocumentFile>() {
//                @Override
//                public int compare(CimocDocumentFile lhs, CimocDocumentFile rhs) {
//                    return lhs.getName().compareTo(rhs.getName());
//                }
//            });
//
//            List<ImageUrl> list = Storage.buildImageUrlFromDocumentFile(files, chapter.getPath(), chapter.getCount(),chapter);
//            if (list.size() != 0) {
//                subscriber.onNext(list);
//                subscriber.onCompleted();
//            } else {
//                subscriber.onError(new Exception());
//            }
//        }).subscribeOn(Schedulers.io());
//    }
    public static Observable<List<ImageUrl>> images(final CimocDocumentFile root, final Comic comic, final Chapter chapter, final String title) {
        return Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<List<ImageUrl>>) emitter -> {
            try {
                CimocDocumentFile dir = getChapterDir(root, comic, chapter, title);

                // Check if directory is null or doesn't exist
                if (dir == null || !dir.exists()) {
                    emitter.onError(new Exception("Chapter directory not found"));
                    return;
                }

                List<CimocDocumentFile> files = dir.listFiles(file -> !file.getName().endsWith("cdif"), new Comparator<CimocDocumentFile>() {
                    @Override
                    public int compare(CimocDocumentFile lhs, CimocDocumentFile rhs) {
                        return lhs.getName().compareTo(rhs.getName());
                    }
                });

                // Check if files list is null (in case listFiles() returns null)
                if (files == null) {
                    emitter.onError(new Exception("Failed to list files in directory"));
                    return;
                }

                List<ImageUrl> list = Storage.buildImageUrlFromDocumentFile(files, chapter.getPath(), chapter.getCount(), chapter);
                if (!list.isEmpty()) {
                    emitter.onNext(list);
                    emitter.onComplete();
                } else {
                    emitter.onError(new Exception("No valid images found"));
                }
            } catch (Exception e) {
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * 批量删除章节，并行删除各章节目录以提升速度。
     * 对于 RawCimocDocumentFile（本地文件系统）和 TreeCimocDocumentFile（SAF），
     * TreeCimocDocumentFile 的 mSubFiles 已改为 ConcurrentHashMap，支持并发访问。
     */
    public static void delete(CimocDocumentFile root, Comic comic, List<Chapter> list, String title) {
        // 使用 parallelStream 并行删除各章节目录
        list.parallelStream().forEach(chapter -> {
            CimocDocumentFile dir = getChapterDir(root, comic, chapter, title);
            if (dir != null) {
                dir.delete();
            }
        });
    }

    public static void delete(CimocDocumentFile root, Comic comic, String title) {
        CimocDocumentFile dir = getComicDir(root, comic, title);
        if (dir != null) {
            dir.delete();
        }
    }

    private static String getIndexJsonFromDir(ContentResolver resolver, CimocDocumentFile dir) {
        if (dir.isDirectory()) {
            CimocDocumentFile file = dir.findFile(FILE_INDEX);
            if (hasMagicNumber(resolver, file)) {
                String jsonString = DocumentUtils.readLineFromFile(resolver, file);
                if (jsonString != null) {
                    return jsonString.substring(5);
                }
            }
        }
        return null;
    }

    private static Task buildTaskFromDir(ContentResolver resolver, CimocDocumentFile dir) {
        String jsonString = getIndexJsonFromDir(resolver, dir);
        if (jsonString != null) {
            try {
                JSONObject jsonObject = new JSONObject(jsonString);
                if (JSON_KEY_TYPE_CHAPTER.equals(jsonObject.get(JSON_KEY_TYPE))) {
                    int count = DocumentUtils.countWithoutSuffix(dir, "cdif");
                    if (count != 0) {
                        String path = jsonObject.getString(JSON_KEY_CHAPTER_PATH);
                        String title = jsonObject.getString(JSON_KEY_CHAPTER_TITLE);
                        return new Task(null, -1, path, title, count, count);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 1.4.3 之后，因为有在章节文件夹内写索引文件，所以恢复起来简单
     * 1.4.3 之前，章节文件夹内没有索引文件，需要比较文件夹名称，有点麻烦，暂不实现
     */
    private static Comic buildComicFromDir(ContentResolver resolver, CimocDocumentFile dir) {
        String jsonString = getIndexJsonFromDir(resolver, dir);
        if (jsonString != null) {
            try {
                JSONObject jsonObject = new JSONObject(jsonString);
                if (!JSON_KEY_TYPE_COMIC.equals(jsonObject.get(JSON_KEY_TYPE))) {
                    return null;
                }
                int source = jsonObject.getInt(JSON_KEY_COMIC_SOURCE);
                String title = jsonObject.getString(JSON_KEY_COMIC_TITLE);
                String cid = jsonObject.getString(JSON_KEY_COMIC_CID);
                String cover = jsonObject.getString(JSON_KEY_COMIC_COVER);
                return new Comic(source, cid, title, cover, System.currentTimeMillis());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static Observable<Pair<Comic, List<Task>>> scan(final ContentResolver resolver, final CimocDocumentFile root) {
        return Observable.create((io.reactivex.rxjava3.core.ObservableOnSubscribe<Pair<Comic, List<Task>>>) emitter -> {
                root.refresh();
                CimocDocumentFile downloadDir = DocumentUtils.getOrCreateSubDirectory(root, DOWNLOAD);
                if (downloadDir != null) {
                    for (CimocDocumentFile sourceDir : downloadDir.listFiles()) {
                        if (sourceDir.isDirectory()) {
                            for (CimocDocumentFile comicDir : sourceDir.listFiles()) {
                                Comic comic = buildComicFromDir(resolver, comicDir);
                                if (comic != null) {
                                    List<Task> list = new LinkedList<>();
                                    for (CimocDocumentFile chapterDir : comicDir.listFiles()) {
                                        Task task = buildTaskFromDir(resolver, chapterDir);
                                        if (task != null) {
                                            list.add(task);
                                        }
                                    }
                                    if (!list.isEmpty()) {
                                        emitter.onNext(Pair.create(comic, list));
                                    }
                                }
                            }
                        }
                    }
                }
                emitter.onComplete();
        }).subscribeOn(Schedulers.io());
    }

    private static boolean hasMagicNumber(ContentResolver resolver, CimocDocumentFile file) {
        if (file != null) {
            char[] magic = DocumentUtils.readCharFromFile(resolver, file, 5);
            return Arrays.equals(magic, "cimoc".toCharArray());
        }
        return false;
    }

}
