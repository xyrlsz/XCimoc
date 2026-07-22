package com.xyrlsz.xcimocob.manager;

import android.util.SparseArray;

import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.parser.MangaParser;

/**
 * Minimal stub for SourceManager — 用于 Komiic 等需要获取 parser 的源
 */
public class SourceManager {
    private static volatile SourceManager mInstance;
    private final SparseArray<MangaParser> mParserArray = new SparseArray<>();

    private SourceManager(AppGetter getter) {}

    public static SourceManager getInstance(AppGetter getter) {
        if (mInstance == null) {
            mInstance = new SourceManager(getter);
        }
        return mInstance;
    }

    public MangaParser getParser(int type) {
        return mParserArray.get(type);
    }

    public void update(com.xyrlsz.xcimocob.model.Source source) {
        // Stub: do nothing
    }
}
