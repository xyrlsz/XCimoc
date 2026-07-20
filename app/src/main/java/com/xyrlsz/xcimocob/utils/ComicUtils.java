package com.xyrlsz.xcimocob.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import com.google.common.collect.Lists;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.core.Download;
import com.xyrlsz.xcimocob.manager.ChapterManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.parser.MangaParser;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Hiroshi on 2017/3/24.
 */

public class ComicUtils {

    public final static int SIMPLE = 0;
    public final static int ZIP = 1;
    public final static int EPUB = 2;
    public final static int CBZ = 3;

    public static LongSparseArray<Comic> buildComicMap(List<Comic> list) {
        LongSparseArray<Comic> array = new LongSparseArray<>();
        for (Comic comic : list) {
            array.put(comic.getId(), comic);
        }
        return array;
    }

    public static void OutputDownloadedComic(AppGetter appGetter, Context context, int type, Comic comic, OutputComicCallback callback) {
        Long sourceComic = IdCreator.createSourceComic(comic);
        SourceManager mSourceManager = SourceManager.getInstance(appGetter);
        CimocDocumentFile outputRoot = DocumentUtils.getOrCreateSubDirectory(App.getApp().getDocumentFile(), "output");

        MangaParser parser = mSourceManager.getParser(comic.getSource());
        Headers headers = parser.getHeader();

        List<Chapter> chapterList = ChapterManager.getInstance(appGetter).getChapterList(sourceComic);
        // ID 是倒过来的，所以顺序会反
        chapterList = Lists.reverse(chapterList);

        String coverUrl = comic.getCover();
        if (StringUtils.isEmpty(coverUrl)) {
            callback.onFailure("封面 URL 为空");
            return;
        }

        List<Chapter> finalChapterList = new ArrayList<>();
        for (Chapter c : chapterList) {
            if (c.isComplete()) {
                String group = c.getSourceGroup();
                String name = c.getTitle();
                if (group != null && !group.isEmpty()) {
                    name = group + "-" + name;
                }
                c.setTitle(name);
                finalChapterList.add(c);
            }
        }
        if (finalChapterList.isEmpty()) {
            callback.onFailure("没有可导出的章节");
            return;
        }
        // 优先使用缓存封面
        byte[] coverCacheBytes = FrescoUtils.getCacheFileBytes(coverUrl);
        if (coverCacheBytes == null) {
            Objects.requireNonNull(App.getHttpClient()).newCall(new Request.Builder().url(coverUrl).headers(headers).build()).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    callback.onFailure("封面下载失败: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    ResponseBody body = response.body();
                    byte[] coverBytes = body.bytes();
                    export(context, mSourceManager, comic, finalChapterList, outputRoot, coverBytes, callback, type);
                }
            });
        } else {
            try {
                export(context, mSourceManager, comic, finalChapterList, outputRoot, coverCacheBytes, callback, type);
            } catch (IOException e) {
//                HintUtils.showToast(context, "导出失败: " + e.getMessage());
                callback.onFailure("导出失败: " + e.getMessage());
            }

        }

    }

    private static void exportAsSimple(Context context, SourceManager sourceManager, Comic comic,
                                       List<Chapter> chapterList, CimocDocumentFile outputRoot,
                                       byte[] cover, OutputComicCallback callback) throws IOException {
        CimocDocumentFile typeRoot = DocumentUtils.getOrCreateSubDirectory(outputRoot, "simple");
        if (typeRoot == null) {
            callback.onFailure("无法创建 simple 目录");
            return;
        }
        CimocDocumentFile nomediaFile = DocumentUtils.findFile(typeRoot, ".nomedia");
        if (nomediaFile == null || !nomediaFile.exists()) {
            // 创建 .nomedia 文件
            CimocDocumentFile newNomedia = typeRoot.createFile(".nomedia");
            if (newNomedia == null) {
                // 如果创建失败可以记录日志，但不应该阻止后续流程
                Log.w("Nomedia", "无法在 simple 目录创建 .nomedia 文件");
            }
        }
        String sanitizedTitle = sanitizeFileName(comic.getTitle());
        CimocDocumentFile comicRoot = DocumentUtils.getOrCreateSubDirectory(typeRoot, sanitizedTitle);
        if (comicRoot == null) {
            callback.onFailure("无法创建漫画目录");
            return;
        }

        // 写入封面
        CimocDocumentFile newCoverFile = DocumentUtils.getOrCreateFile(comicRoot, "cover.jpg");
        if (newCoverFile != null) {
            DocumentUtils.writeBytesToFile(context.getContentResolver(), newCoverFile, cover);
        } else {
            callback.onFailure("无法创建封面文件");
            return;
        }

        List<Observable<Boolean>> tasks = new ArrayList<>();
        int sizeDigits = String.valueOf(chapterList.size()).length();
        int index = 1;

        for (Chapter chapter : chapterList) {

            String name = String.format("%0" + sizeDigits + "d", index) + "-" + chapter.getTitle();


            String displayName = sanitizeFileName(name);
            CimocDocumentFile chapterRoot = DocumentUtils.getOrCreateSubDirectory(comicRoot, displayName);
            if (chapterRoot == null) {
                callback.onFailure("无法创建章节目录: " + displayName);
                return;
            }

            io.reactivex.rxjava3.core.Single<Boolean> taskSingle = Download.images(App.getApp().getDocumentFile(), comic, chapter,
                            sourceManager.getParser(comic.getSource()).getTitle())
                    .all(imageUrls -> {
                        if (imageUrls == null || imageUrls.isEmpty()) return false;
                        for (int i = 0; i < imageUrls.size(); i++) {
                            ImageUrl imageUrl = imageUrls.get(i);
                            String url = imageUrl.getUrl();
                            CimocDocumentFile srcFile;

                            if (url.startsWith("file://")) {
                                File f = new File(Objects.requireNonNull(Uri.parse(url).getPath()));
                                srcFile = CimocDocumentFile.fromFile(f);
                            } else if (url.startsWith("content://")) {
                                srcFile = CimocDocumentFile.fromSubTreeUri(context, Uri.parse(url));
                            } else {
                                return false;
                            }
                            int imgDigits = String.valueOf(imageUrls.size()).length();
                            if (srcFile != null && srcFile.exists()) {
                                String imgName = String.format("%0" + imgDigits + "d", i + 1) + "_" + srcFile.getName(); // 防止重名
                                CimocDocumentFile destFile = DocumentUtils.getOrCreateFile(chapterRoot, imgName);
                                if (destFile != null) {
                                    DocumentUtils.copyFile(context.getContentResolver(), srcFile, destFile);
                                } else {
                                    Log.e("ExportComic", "目标文件创建失败: " + imgName);
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        }
                        return true;
                    });
            Observable<Boolean> task = taskSingle.toObservable();

            tasks.add(task);
            index++;
        }

        Observable.merge(tasks)
                .toList()
//                .observeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(results -> {
                    boolean allSuccess;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        allSuccess = results.stream().allMatch(r -> r);
                    } else {
                        allSuccess = true;
                        for (Boolean r : results) {
                            if (!r) {
                                allSuccess = false;
                                break;
                            }
                        }
                    }
                    if (allSuccess) {
                        callback.onSuccess(UriUtils.convertContentToFilePath(comicRoot.getUri()));
                    } else {
                        callback.onFailure("部分章节导出失败");
                    }
                }, throwable -> {
                    callback.onFailure("导出过程中发生异常: " + throwable.getMessage());
                    throwable.printStackTrace();
                });
    }

    private static void exportAsZip(Context context, SourceManager sourceManager, Comic comic,
                                    List<Chapter> chapterList, CimocDocumentFile outputRoot,
                                    byte[] cover, OutputComicCallback callback) {
        try {
            CimocDocumentFile typeRoot = DocumentUtils.getOrCreateSubDirectory(outputRoot, "zip");
            if (typeRoot == null) {
                callback.onFailure("无法创建 zip 目录");
                return;
            }

            String sanitizedTitle = sanitizeFileName(comic.getTitle());
            CimocDocumentFile zipFile = DocumentUtils.getOrCreateFile(typeRoot, sanitizedTitle + ".zip");
            if (zipFile == null) {
                callback.onFailure("无法创建 zip 文件");
                return;
            }

            OutputStream outputStream = context.getContentResolver().openOutputStream(zipFile.getUri());
            if (outputStream == null) {
                callback.onFailure("无法打开 zip 输出流");
                return;
            }

            ZipOutputStream zos = new ZipOutputStream(outputStream);

            // 添加封面
            zos.putNextEntry(new ZipEntry("cover.jpg"));
            zos.write(cover);
            zos.closeEntry();

            int chapterIndex = 1;
            int sizeDigits = String.valueOf(chapterList.size()).length();
            for (Chapter chapter : chapterList) {
                String chapterDir = String.format("%0" + sizeDigits + "d-%s/", chapterIndex, sanitizeFileName(chapter.getTitle()));
                List<ImageUrl> imageUrls = Download.images(App.getApp().getDocumentFile(), comic, chapter,
                                sourceManager.getParser(comic.getSource()).getTitle())
                        .blockingFirst();

                if (imageUrls == null || imageUrls.isEmpty()) {
                    callback.onFailure("章节 [" + chapter.getTitle() + "] 下载失败");
                    return;
                }

                int imgIndex = 0;
                int imgSizeDigits = String.valueOf(imageUrls.size()).length();
                for (ImageUrl imageUrl : imageUrls) {
                    String url = imageUrl.getUrl();
                    InputStream is;
                    String imgName = String.format("%0" + imgSizeDigits + "d_", imgIndex);
                    try {
                        Uri uri = Uri.parse(url);
                        if (url.startsWith("file://")) {
                            File f = new File(Objects.requireNonNull(uri.getPath()));
                            is = new FileInputStream(f);
                            imgName += f.getName();
                        } else if (url.startsWith("content://")) {
                            is = context.getContentResolver().openInputStream(uri);
                            CimocDocumentFile file = CimocDocumentFile.fromSubTreeUri(context, uri);
                            if (file != null) {
                                imgName += file.getName();
                            } else {
                                imgName += uri.getLastPathSegment();
                            }
                        } else {
                            callback.onFailure("不支持的 URL: " + url);
                            return;
                        }

                        if (is != null) {
                            zos.putNextEntry(new ZipEntry(chapterDir + imgName));
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }
                            zos.closeEntry();
                            is.close();
                        }
                    } catch (IOException e) {
                        callback.onFailure("写入 zip 失败: " + e.getMessage());
                        zos.close();
                        return;
                    }
                    imgIndex++;
                }
                chapterIndex++;
            }

            zos.close();
            callback.onSuccess(UriUtils.convertContentToFilePath(zipFile.getUri()));

        } catch (Exception e) {
            callback.onFailure("ZIP 导出异常: " + e.getMessage());
        }
    }

    private static void exportAsEpub(Context context, SourceManager sourceManager, Comic comic,
                                     List<Chapter> chapterList, CimocDocumentFile outputRoot,
                                     byte[] cover, OutputComicCallback callback) {
        try {
            CimocDocumentFile typeRoot = DocumentUtils.getOrCreateSubDirectory(outputRoot, "epub");
            if (typeRoot == null) {
                callback.onFailure("无法创建 epub 目录");
                return;
            }

            String sanitizedTitle = sanitizeFileName(comic.getTitle());
            CimocDocumentFile epubFile = DocumentUtils.getOrCreateFile(typeRoot, sanitizedTitle + ".epub");
            if (epubFile == null) {
                callback.onFailure("无法创建 epub 文件");
                return;
            }

            OutputStream outputStream = context.getContentResolver().openOutputStream(epubFile.getUri());
            if (outputStream == null) {
                callback.onFailure("无法打开 epub 输出流");
                return;
            }

            ZipOutputStream zos = new ZipOutputStream(outputStream);

            // 必须的 mimetype 文件
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes());
            zos.closeEntry();

            // META-INF/container.xml
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            String containerXml = "<?xml version=\"1.0\"?>\n" +
                    "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
                    "  <rootfiles>\n" +
                    "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
                    "  </rootfiles>\n" +
                    "</container>";
            zos.write(containerXml.getBytes());
            zos.closeEntry();

            // 写入封面图片
            zos.putNextEntry(new ZipEntry("OEBPS/images/cover.jpg"));
            zos.write(cover);
            zos.closeEntry();

            StringBuilder manifest = new StringBuilder();
            manifest.append("<item id=\"cover\" href=\"images/cover.jpg\" media-type=\"image/jpeg\" properties=\"cover-image\"/>\n");
            // 生成 toc.ncx 内容
            StringBuilder ncx = new StringBuilder();
            ncx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    .append("<!DOCTYPE ncx PUBLIC \"-//NISO//DTD ncx 2005-1//EN\" ")
                    .append("\"http://www.daisy.org/z3986/2005/ncx-2005-1.dtd\">\n")
                    .append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n")
                    .append("<head>\n")
                    .append("<meta name=\"dtb:uid\" content=\"com.example.epub.").append(comic.getId()).append("\"/>\n")
                    .append("<meta name=\"dtb:depth\" content=\"1\"/>\n")
                    .append("<meta name=\"dtb:totalPageCount\" content=\"0\"/>\n")
                    .append("<meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n")
                    .append("</head>\n")
                    .append("<docTitle><text>").append(comic.getTitle()).append("</text></docTitle>\n")
                    .append("<navMap>\n");

            StringBuilder spine = new StringBuilder();
            StringBuilder toc = new StringBuilder();
            toc.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    .append("<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<body>\n<ul>\n");

            int chapterIndex = 1;
            for (Chapter chapter : chapterList) {
                List<ImageUrl> imageUrls = Download.images(App.getApp().getDocumentFile(), comic, chapter,
                        sourceManager.getParser(comic.getSource()).getTitle()).blockingFirst();

                if (imageUrls == null || imageUrls.isEmpty()) {
                    callback.onFailure("章节 [" + chapter.getTitle() + "] 下载失败");
                    return;
                }

                String chapterId = "chapter" + chapterIndex;
                ncx.append("<navPoint id=\"navPoint-").append(chapterIndex).append("\" playOrder=\"")
                        .append(chapterIndex).append("\">\n")
                        .append("<navLabel><text>").append(chapter.getTitle()).append("</text></navLabel>\n")
                        .append("<content src=\"").append(chapterId).append(".xhtml\"/>\n")
                        .append("</navPoint>\n");
//                chapterIndex++;
                String chapterFileName = "OEBPS/" + chapterId + ".xhtml";

                StringBuilder html = new StringBuilder();
                html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                        .append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n")
                        .append("<head><title>").append(chapter.getTitle()).append("</title></head><body>\n");

                int imgIndex = 1;
                for (ImageUrl imageUrl : imageUrls) {
                    Uri uri = Uri.parse(imageUrl.getUrl());
                    InputStream is;
                    String imgFileName = StringUtils.format("images/%s_%d.jpg", chapterId, imgIndex);

                    if (imageUrl.getUrl().startsWith("file://")) {
                        is = new FileInputStream(Objects.requireNonNull(uri.getPath()));
                    } else if (imageUrl.getUrl().startsWith("content://")) {
                        is = context.getContentResolver().openInputStream(uri);
                    } else {
                        continue;
                    }

                    if (is != null) {
                        zos.putNextEntry(new ZipEntry("OEBPS/" + imgFileName));
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                        zos.closeEntry();
                        is.close();

                        html.append("<div style=\"text-align:center;\"><img src=\"").append(imgFileName).append("\" style=\"max-width:100%;height:auto;\"/></div><br/>\n");
                    }

                    imgIndex++;
                }

                html.append("</body></html>");
                zos.putNextEntry(new ZipEntry(chapterFileName));
                zos.write(html.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                manifest.append("<item id=\"").append(chapterId).append("\" href=\"").append(chapterId)
                        .append(".xhtml\" media-type=\"application/xhtml+xml\"/>\n");

                spine.append("<itemref idref=\"").append(chapterId).append("\" />\n");

                toc.append("<li><a href=\"").append(chapterId).append(".xhtml\">")
                        .append(chapter.getTitle()).append("</a></li>\n");

                chapterIndex++;
            }
            ncx.append("</navMap>\n</ncx>");
            toc.append("</ul></body></html>");
            zos.putNextEntry(new ZipEntry("OEBPS/toc.ncx"));
            zos.write(ncx.toString().getBytes(StandardCharsets.UTF_8));
            zos.putNextEntry(new ZipEntry("OEBPS/toc.xhtml"));
            zos.write(toc.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // content.opf 构建
            String opf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"BookId\">\n" +
                    "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
                    "<dc:title>" + comic.getTitle() + "</dc:title>\n" +
                    "<dc:identifier id=\"BookId\">com.example.epub." + comic.getId() + "</dc:identifier>\n" +
                    "<meta name=\"cover\" content=\"cover\"/>\n" +
                    "</metadata>\n" +
                    "<manifest>\n" +
                    manifest +
                    "<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n" +
                    "<item id=\"toc\" href=\"toc.xhtml\" media-type=\"application/xhtml+xml\"/>\n" +
                    "</manifest>\n" +
                    "<spine toc=\"ncx\">\n" +
                    spine +
                    "</spine>\n" +
                    "</package>";

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opf.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.close();
            callback.onSuccess(UriUtils.convertContentToFilePath(epubFile.getUri()));
        } catch (Exception e) {
            callback.onFailure("EPUB 导出失败: " + e.getMessage());
        }
    }

    private static void exportAsCBZ(Context context, SourceManager sourceManager, Comic comic,
                                    List<Chapter> chapterList, CimocDocumentFile outputRoot,
                                    byte[] cover, OutputComicCallback callback) {
        try {
            // 创建 cbz 文件所在目录
            CimocDocumentFile typeRoot = DocumentUtils.getOrCreateSubDirectory(outputRoot, "cbz");
            if (typeRoot == null) {
                callback.onFailure("无法创建 cbz 目录");
                return;
            }

            // 文件名：漫画标题.cbz
            String sanitizedTitle = sanitizeFileName(comic.getTitle());
            CimocDocumentFile cbzFile = DocumentUtils.getOrCreateFile(typeRoot, sanitizedTitle + ".cbz");
            if (cbzFile == null) {
                callback.onFailure("无法创建 cbz 文件");
                return;
            }

            OutputStream outputStream = context.getContentResolver().openOutputStream(cbzFile.getUri());
            if (outputStream == null) {
                callback.onFailure("无法打开 cbz 输出流");
                return;
            }

            ZipOutputStream zos = new ZipOutputStream(outputStream);

            int imageIndex = 0;

            // 可选：将封面作为第一张图插入
            if (cover != null && cover.length > 0) {
                String fileName = StringUtils.format("%06d_cover.jpg", imageIndex++);
                zos.putNextEntry(new ZipEntry(fileName));
                zos.write(cover);
                zos.closeEntry();
            }

            // 依次导出所有章节图片
            for (Chapter chapter : chapterList) {
                List<ImageUrl> imageUrls = Download.images(App.getApp().getDocumentFile(), comic, chapter,
                        sourceManager.getParser(comic.getSource()).getTitle()).blockingFirst();

                if (imageUrls == null || imageUrls.isEmpty()) {
                    callback.onFailure("章节 [" + chapter.getTitle() + "] 下载失败");
                    zos.close();
                    return;
                }

                for (ImageUrl imageUrl : imageUrls) {
                    String url = imageUrl.getUrl();
                    Uri uri = Uri.parse(url);
                    InputStream is;

                    try {
                        if (url.startsWith("file://")) {
                            File f = new File(Objects.requireNonNull(uri.getPath()));
                            is = new FileInputStream(f);
                        } else if (url.startsWith("content://")) {
                            is = context.getContentResolver().openInputStream(uri);
                        } else {
                            callback.onFailure("不支持的 URL: " + url);
                            zos.close();
                            return;
                        }

                        if (is != null) {
                            String imgExt = getImageExtensionFromUri(context, uri);
                            String fileName = StringUtils.format("%06d%s", imageIndex++, imgExt);
                            zos.putNextEntry(new ZipEntry(fileName));

                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }
                            zos.closeEntry();
                            is.close();
                        }
                    } catch (Exception e) {
                        callback.onFailure("写入 CBZ 失败: " + e.getMessage());
                        zos.close();
                        return;
                    }
                }
            }

            zos.close();
            callback.onSuccess(UriUtils.convertContentToFilePath(cbzFile.getUri()));

        } catch (Exception e) {
            callback.onFailure("CBZ 导出异常: " + e.getMessage());
        }
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String getImageExtensionFromUri(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) return ".jpg"; // 默认

        switch (mimeType) {
            case "image/png":
                return ".png";
            case "image/webp":
                return ".webp";
            case "image/gif":
                return ".gif";
            case "image/bmp":
                return ".bmp";
            default:
                return ".jpg";
        }
    }

    private static void export(Context context, SourceManager sourceManager, Comic comic,
                               List<Chapter> chapterList, CimocDocumentFile outputRoot,
                               byte[] cover, OutputComicCallback callback, int type) throws IOException {
        switch (type) {
            case SIMPLE:
                exportAsSimple(context, sourceManager, comic, chapterList, outputRoot, cover, callback);
                break;
            case ZIP:
                exportAsZip(context, sourceManager, comic, chapterList, outputRoot, cover, callback);
                break;
            case EPUB:
                exportAsEpub(context, sourceManager, comic, chapterList, outputRoot, cover, callback);
                break;
            case CBZ:
                exportAsCBZ(context, sourceManager, comic, chapterList, outputRoot, cover, callback);
                break;
            default:
                callback.onFailure("未知导出类型");
                break;
        }

    }

    public interface OutputComicCallback {
        void onSuccess(String path);

        void onFailure(String message);
    }

}
