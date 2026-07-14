package com.xyrlsz.xcimocob.manager;

import android.util.SparseArray;

import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.fresco.ComicFrescoHeaders;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.model.Source_;
import com.xyrlsz.xcimocob.parser.MangaParser;
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
import com.xyrlsz.xcimocob.source.Locality;
import com.xyrlsz.xcimocob.source.MH5;
import com.xyrlsz.xcimocob.source.MYCOMIC;
import com.xyrlsz.xcimocob.source.ManBen;
import com.xyrlsz.xcimocob.source.ManHuaGui;
import com.xyrlsz.xcimocob.source.ManWa;
import com.xyrlsz.xcimocob.source.MangaBZ;
import com.xyrlsz.xcimocob.source.Manhuatai;
import com.xyrlsz.xcimocob.source.Manhuayu;
import com.xyrlsz.xcimocob.source.Null;
import com.xyrlsz.xcimocob.source.Tencent;
import com.xyrlsz.xcimocob.source.Vomicmh;
import com.xyrlsz.xcimocob.source.YKMH;
import com.xyrlsz.xcimocob.source.YYManHua;
import com.xyrlsz.xcimocob.source.ZaiManhua;

import java.util.List;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Headers;

/**
 * Created by Hiroshi on 2016/8/11.
 */
public class SourceManager {

    private static volatile SourceManager mInstance;

    // 1. 修改：使用 ObjectBox 的 Box 替代 SourceDao
    private final Box<Source> mSourceBox;
    private final SparseArray<MangaParser> mParserArray = new SparseArray<>();

    private SourceManager(AppGetter getter) {
        // 2. 修改：从 BoxStore 获取 Box
        BoxStore boxStore = getter.getAppInstance().getBoxStore();
        mSourceBox = boxStore.boxFor(Source.class);
    }

    public static SourceManager getInstance(AppGetter getter) {
        if (mInstance == null) {
            synchronized (SourceManager.class) {
                if (mInstance == null) {
                    mInstance = new SourceManager(getter);
                }
            }
        }
        return mInstance;
    }

    // 3. 修改：使用 ObjectBox Query 查询，包装在 Observable 中
    public Observable<List<Source>> list() {
        return Observable.fromCallable(() ->
                mSourceBox.query()
                        .order(Source_.type) // 升序
                        .build()
                        .find()
        ).subscribeOn(Schedulers.io());
    }

    public Observable<List<Source>> listEnableInRx() {
        return Observable.fromCallable(() ->
                mSourceBox.query()
                        .equal(Source_.enable, true)
                        .order(Source_.type)
                        .build()
                        .find()
        ).subscribeOn(Schedulers.io());
    }

    public List<Source> listEnable() {
        return mSourceBox.query()
                .equal(Source_.enable, true)
                .order(Source_.type)
                .build()
                .find();
    }

    // 4. 修改：load 方法。ObjectBox 没有直接的 unique 方法，使用 findFirst
    public Source load(int type) {
        return mSourceBox.query()
                .equal(Source_.type, type)
                .build()
                .findFirst();
    }

    // 5. 修改：CRUD 操作
    public long insert(Source source) {
        return mSourceBox.put(source); // put 返回 id
    }

    public void update(Source source) {
        mSourceBox.put(source);
    }

    // 6. 保持不变：解析器管理逻辑（这部分与数据库无关）
    public MangaParser getParser(int type) {
        MangaParser parser = mParserArray.get(type);
        if (parser == null) {
            Source source = load(type);
            parser = switch (type) {
                case ManHuaGui.TYPE -> new ManHuaGui(source);
                case DM5.TYPE -> new DM5(source);
                case Locality.TYPE -> new Locality();
                case Tencent.TYPE -> new Tencent(source);
                case BuKa.TYPE -> new BuKa(source);
                case Manhuatai.TYPE -> new Manhuatai(source);
                case CopyMH.TYPE -> new CopyMH(source);
                case HotManga.TYPE -> new HotManga(source);
                case MangaBZ.TYPE -> new MangaBZ(source);
                case DongManManHua.TYPE -> new DongManManHua(source);
                case YKMH.TYPE -> new YKMH(source);
                case Baozi.TYPE -> new Baozi(source);
                case MYCOMIC.TYPE -> new MYCOMIC(source);
                case DuManWu.TYPE -> new DuManWu(source);
                case Komiic.TYPE -> new Komiic(source);
                case Manhuayu.TYPE -> new Manhuayu(source);
                case GoDaManHua.TYPE -> new GoDaManHua(source);
                case Vomicmh.TYPE -> new Vomicmh(source);
                case YYManHua.TYPE -> new YYManHua(source);
                case ZaiManhua.TYPE -> new ZaiManhua(source);
                case ManBen.TYPE -> new ManBen(source);
                case GFMH.TYPE -> new GFMH(source);
                case ManWa.TYPE -> new ManWa(source);
                case MH5.TYPE -> new MH5(source);
                case DuManWuApp.TYPE -> new DuManWuApp(source);
                case CopyMHWeb.TYPE -> new CopyMHWeb(source);
                case DongManHi.TYPE -> new DongManHi(source);
                default -> new Null();
            };
            mParserArray.put(type, parser);
        }
        return parser;
    }

    // 内部类保持不变
    public class TitleGetter {
        public String getTitle(int type) {
            return getParser(type).getTitle();
        }
    }

    public class HeaderGetter {
        public Headers getHeader(int type) {
            Headers headers = getParser(type).getHeader();
            ComicFrescoHeaders.setHeaders(headers);
            return headers;
        }
    }
}