package com.xyrlsz.xcimocob.manager;

import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Chapter_;
import com.xyrlsz.xcimocob.utils.IdCreator;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.exception.UniqueViolationException;
import io.reactivex.rxjava3.core.Observable;

/**
 * Created by Hiroshi on 2016/7/9.
 */
public class ChapterManager {
    private static volatile ChapterManager mInstance;

    private final Box<Chapter> mChapterBox;

    private ChapterManager(AppGetter getter) {
        BoxStore boxStore = getter.getAppInstance().getBoxStore();
        mChapterBox = boxStore.boxFor(Chapter.class);
    }

    public static ChapterManager getInstance(AppGetter getter) {
        if (mInstance == null) {
            synchronized (ChapterManager.class) {
                if (mInstance == null) {
                    mInstance = new ChapterManager(getter);
                }
            }
        }
        return mInstance;
    }

    public void runInTx(Runnable runnable) {
        mChapterBox.getStore().runInTx(runnable);
    }

    public <T> T callInTx(Callable<T> callable) throws Exception {
        return mChapterBox.getStore().callInTx(callable);
    }

    public Observable<List<Chapter>> getListChapter(long sourceComic) {
        return Observable.fromCallable(() -> {

            Long sourceComic0 = IdCreator.recreateSourceComic(sourceComic, 0L);
            List<Chapter> list = mChapterBox.query()
                    .equal(Chapter_.sourceComic, sourceComic)
                    .build()
                    .find();

            return list.stream()
                    .filter(chapter -> !IdCreator.getSourceComicFromChapter(chapter.getId()).equals(sourceComic0))
                    .collect(Collectors.toList());
        });
    }

    public List<Chapter> getChapterList(long sourceComic) {
        return mChapterBox.query().equal(Chapter_.sourceComic, sourceComic).build().find();
    }

    public List<Chapter> getChapter(String path, String title) {
        return mChapterBox.query(Chapter_.path.equal(path).and(Chapter_.title.equal(title)))
                .build()
                .find();
    }

    public List<Chapter> getChapter(String path) {
        return mChapterBox.query(Chapter_.path.equal(path)).build().find();
    }

    public List<Chapter> getChapter(long sourceComic, String path) {
        return mChapterBox.query(Chapter_.path.equal(path))
                .equal(Chapter_.sourceComic, sourceComic)
                .build()
                .find();
    }

    public Chapter load(long id) {
        return mChapterBox.get(id);
    }

    public void updateOrInsert(List<Chapter> chapterList) {
        for (Chapter chapter : chapterList) {
            // 先按 sourceComic + path 查找是否已有记录
            List<Chapter> existing = getChapter(chapter.getSourceComic(), chapter.getPath());
            if (!existing.isEmpty()) {
                // 已有记录，复用其 ID 进行更新
                chapter.setId(existing.get(0).getId());
            }
            try {
                mChapterBox.put(chapter);
            } catch (UniqueViolationException ignored) {
                // 仍然冲突则跳过（极少发生）
            }
        }
    }

    public void update(Chapter chapter) {
        if (chapter.getId() != 0) {
            try {
                mChapterBox.put(chapter);
            } catch (UniqueViolationException ignored) {
            }
        }
    }

    public void deleteByKey(long key) {
        mChapterBox.remove(key);
    }

    public void deleteBySourceComic(long sourceComic) {
        List<Chapter> chapters =
                mChapterBox.query().equal(Chapter_.sourceComic, sourceComic).build().find();
        if (!chapters.isEmpty()) {
            mChapterBox.remove(chapters);
        }
    }
}
