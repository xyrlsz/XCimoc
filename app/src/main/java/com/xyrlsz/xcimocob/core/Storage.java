package com.xyrlsz.xcimocob.core;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;

import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;
import com.xyrlsz.xcimocob.utils.DecryptionUtils;
import com.xyrlsz.xcimocob.utils.DocumentUtils;
import com.xyrlsz.xcimocob.utils.IdCreator;
import com.xyrlsz.xcimocob.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Hiroshi on 2016/10/16.
 */

public class Storage {
    private static final String DOWNLOAD = "download";
    private static final String PICTURE = "picture";
    private static final String BACKUP = "backup";

    public static CimocDocumentFile initRoot(Context context, String uri) {
        if (uri == null || uri.isEmpty()) {
            //            File file = new File(Environment.getExternalStorageDirectory(), "Cimoc");
            File file = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "Cimoc");
            //            File file = new
            //            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            //            "Cimoc");
            if (file.exists() || file.mkdirs()) {
                return CimocDocumentFile.fromFile(file);
            } else {
                return null;
            }
        } else if (uri.startsWith("content")) {
            return CimocDocumentFile.fromTreeUri(context, Uri.parse(uri));
        } else if (uri.startsWith("file")) {
            return CimocDocumentFile.fromFile(
                    new File(Objects.requireNonNull(Uri.parse(uri).getPath())));
        } else {
            return CimocDocumentFile.fromFile(new File(uri, "Cimoc"));
        }
    }

    private static boolean copyFile(ContentResolver resolver, CimocDocumentFile src,
                                    CimocDocumentFile parent, io.reactivex.rxjava3.core.ObservableEmitter<? super String> emitter) {
        CimocDocumentFile file = DocumentUtils.getOrCreateFile(parent, src.getName());
        if (file != null) {
            emitter.onNext(
                    StringUtils.format("正在移动 %s...", src.getUri().getLastPathSegment()));
            try {
                DocumentUtils.writeBinaryToFile(resolver, src, file);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static boolean copyDir(ContentResolver resolver, CimocDocumentFile src,
                                   CimocDocumentFile parent, io.reactivex.rxjava3.core.ObservableEmitter<? super String> emitter) {
        if (src.isDirectory()) {
            CimocDocumentFile dir = DocumentUtils.getOrCreateSubDirectory(parent, src.getName());
            CimocDocumentFile[] children = src.listFiles();
            // 并行复制子文件和子目录，充分利用 I/O 吞吐量
            return Arrays.stream(children).parallel().allMatch(file -> {
                if (file.isDirectory()) {
                    return copyDir(resolver, file, dir, emitter);
                } else {
                    return copyFile(resolver, file, dir, emitter);
                }
            });
        }
        return true;
    }

    private static boolean copyDir(ContentResolver resolver, CimocDocumentFile src,
                                   CimocDocumentFile dst, String name, io.reactivex.rxjava3.core.ObservableEmitter<? super String> emitter) {
        CimocDocumentFile file = src.findFile(name);
        if (file != null && file.isDirectory()) {
            return copyDir(resolver, file, dst, emitter);
        }
        return true;
    }

    private static void deleteDir(
            CimocDocumentFile parent, String name, io.reactivex.rxjava3.core.ObservableEmitter<? super String> emitter) {
        CimocDocumentFile file = parent.findFile(name);
        if (file != null && file.isDirectory()) {
            emitter.onNext(
                    StringUtils.format("正在删除 %s", file.getUri().getLastPathSegment()));
            file.delete();
        }
    }

    private static boolean isDirSame(CimocDocumentFile root, CimocDocumentFile dst) {
        return Objects.requireNonNull(root.getUri().getScheme()).equals("file")
                && Objects.requireNonNull(dst.getUri().getPath()).endsWith("primary:Cimoc")
                || Objects.requireNonNull(root.getUri().getPath()).equals(dst.getUri().getPath());
    }

    public static Observable<String> moveRootDir(
            final ContentResolver resolver, final CimocDocumentFile root, final CimocDocumentFile dst) {
        return Observable
                .create((io.reactivex.rxjava3.core.ObservableOnSubscribe<String>) emitter -> {
                    if (dst.canRead() && !isDirSame(root, dst)) {
                        root.refresh();
                        if (copyDir(resolver, root, dst, BACKUP, emitter)
                                && copyDir(resolver, root, dst, DOWNLOAD, emitter)
                                && copyDir(resolver, root, dst, PICTURE, emitter)) {
                            // 复制完成后，先刷新根目录确保缓存最新，再并行删除源目录
                            root.refresh();
                            Arrays.asList(BACKUP, DOWNLOAD, PICTURE).parallelStream()
                                    .forEach(name -> deleteDir(root, name, emitter));
                            emitter.onComplete();
                            return;
                        }
                    }
                    emitter.onError(new Exception());
                })
                .subscribeOn(Schedulers.io());
    }

    public static Observable<Uri> savePicture(final ContentResolver resolver,
                                              final CimocDocumentFile root, final InputStream stream, final String filename) {
        return Observable
                .create((io.reactivex.rxjava3.core.ObservableOnSubscribe<Uri>) emitter -> {
                    try {
                        CimocDocumentFile dir = DocumentUtils.getOrCreateSubDirectory(root, PICTURE);
                        if (dir != null) {
                            CimocDocumentFile file = DocumentUtils.getOrCreateFile(dir, filename);
                            DocumentUtils.writeBinaryToFile(
                                    resolver, Objects.requireNonNull(file), stream);
                            emitter.onNext(file.getUri());
                            emitter.onComplete();
                            return;
                        }
                        stream.close();
                    } catch (IOException ignored) {

                    }
                    emitter.onError(new Exception());
                })
                .subscribeOn(Schedulers.io());
    }

    public static List<ImageUrl> buildImageUrlFromDocumentFile(
            List<CimocDocumentFile> list, String chapterStr, int max, Chapter chapter) {
        int count = 0;
        List<ImageUrl> result = new ArrayList<>(list.size());

        // 并行处理图片尺寸获取
        List<ImageInfo> imageInfos = new ArrayList<>(list.size());
        for (CimocDocumentFile file : list) {
            imageInfos.add(new ImageInfo(file, count++));
        }

        // 使用并行流处理
        imageInfos.parallelStream().forEach(info -> {
            String uri = info.file.getUri().toString();
            if (uri.startsWith("file")) {
                // file:// 类型，获取尺寸并解码中文路径
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                try {
                    BitmapFactory.decodeStream(info.file.openInputStream(), null, opts);
                    info.width = opts.outWidth;
                    info.height = opts.outHeight;
                    info.uri = DecryptionUtils.urlDecrypt(uri);
                } catch (Exception e) {
                    e.printStackTrace();
                    info.uri = uri;
                }
            } else if (uri.startsWith("content")) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                // 【修改点】：去掉 inSampleSize。
                // 在 Bounds 模式下，它不影响速度（都是只读文件头），但会影响精度。

                try {
                    InputStream is = info.file.openInputStream();
                    // 这一步非常快，因为它只读取流的前几十个字节（文件头）
                    BitmapFactory.decodeStream(is, null, opts);
                    is.close();

                    // 直接赋值，精准获取原始尺寸
                    info.width = opts.outWidth;
                    info.height = opts.outHeight;
                    info.uri = uri;
                } catch (Exception e) {
                    e.printStackTrace();
                    info.uri = uri;
                }
            } else {
                // 其他类型
                info.uri = uri;
            }
        });

        // 按原始顺序构建结果
        count = 0;
        for (ImageInfo info : imageInfos) {
            Long comicChapter = chapter.getId();
            Long id = IdCreator.createImageId(comicChapter, count);
            ImageUrl image = new ImageUrl(id, comicChapter, ++count, info.uri, false);
            image.setHeight(info.height);
            image.setWidth(info.width);
            image.setChapter(chapterStr);
            result.add(image);

            if (count >= max) {
                break;
            }
        }

        return result;
    }

    private static class ImageInfo {
        CimocDocumentFile file;
        int index;
        int width;
        int height;
        String uri;

        ImageInfo(CimocDocumentFile file, int index) {
            this.file = file;
            this.index = index;
            this.width = 0;
            this.height = 0;
            this.uri = null;
        }
    }
}
