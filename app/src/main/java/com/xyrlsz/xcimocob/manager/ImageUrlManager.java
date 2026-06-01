package com.xyrlsz.xcimocob.manager;

import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.ImageUrl_;

import java.util.List;
import java.util.concurrent.Callable;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by HaleyDu on 2020/8/27.
 */
public class ImageUrlManager {
    private static volatile ImageUrlManager mInstance;

    // 1. 修改：使用 ObjectBox 的 Box 替代 Dao
    private final Box<ImageUrl> mImageUrlBox;

    private ImageUrlManager(AppGetter getter) {
        // 2. 修改：从 BoxStore 获取 Box
        BoxStore boxStore = getter.getAppInstance().getBoxStore();
        mImageUrlBox = boxStore.boxFor(ImageUrl.class);
    }

    public static ImageUrlManager getInstance(AppGetter getter) {
        if (mInstance == null) {
            synchronized (ImageUrlManager.class) {
                if (mInstance == null) {
                    mInstance = new ImageUrlManager(getter);
                }
            }
        }
        return mInstance;
    }

    // 3. 修改：封装 runInTx 和 callInTx 方法
    public void runInTx(Runnable runnable) {
        mImageUrlBox.getStore().runInTx(runnable);
    }

    public <T> T callInTx(Callable<T> callable) throws Exception {
        return mImageUrlBox.getStore().callInTx(callable);
    }

    // 4. 修改：使用 ObjectBox Query 查询
    public Observable<List<ImageUrl>> getListImageUrlRX(Long comicChapter) {
        return Observable.fromCallable(() ->
                mImageUrlBox.query()
                        .equal(ImageUrl_.comicChapter, comicChapter) // 注意：ObjectBox 使用字段名直接比较
                        .build()
                        .find()
        ).subscribeOn(Schedulers.io());
    }

    public List<ImageUrl> getListImageUrl(Long comicChapter) {
        return mImageUrlBox.query()
                .equal(ImageUrl_.comicChapter, comicChapter)
                .build()
                .find();
    }

    // 5. 修改：load 方法
    public ImageUrl load(long id) {
        return mImageUrlBox.get(id);
    }

    // 6. 修改：updateOrInsert 逻辑
    public void updateOrInsert(List<ImageUrl> imageUrlList) {
        mImageUrlBox.put(imageUrlList);
    }


    // 9. 修改：删除单个
    public void deleteByKey(long key) {
        mImageUrlBox.remove(key);
    }

    // 10. 修改：根据 comicChapter 删除（使用事务 + 批量删除）
    public void deleteByComicChapter(Long comicChapter) {
        runInTx(() -> {
            long[] ids = mImageUrlBox.query()
                    .equal(ImageUrl_.comicChapter, comicChapter)
                    .build()
                    .findIds();
            if (ids.length > 0) {
                mImageUrlBox.remove(ids);
            }
        });
    }

}