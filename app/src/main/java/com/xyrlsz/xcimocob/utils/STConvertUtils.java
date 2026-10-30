package com.xyrlsz.xcimocob.utils;

import com.xyrlsz.opencc.android.lib.ChineseConverter;
import com.xyrlsz.opencc.android.lib.ConversionType;
import com.xyrlsz.xcimocob.App;
import com.xyrlsz.xcimocob.manager.PreferenceManager;

import taobe.tec.jcc.JChineseConvertor;

public class STConvertUtils {
    public static String T2S(String s) {
        PreferenceManager preferenceManager = App.getPreferenceManager();
        try {
            switch (preferenceManager.getNumber(PreferenceManager.PREF_ST_ENGINE, PreferenceManager.ST_JCC).intValue()) {
                case PreferenceManager.ST_JCC:
                    return JChineseConvertor.getInstance().t2s(s);
                case PreferenceManager.ST_OPENCC:
//                    return ZhConverterUtil.toSimple(s);
                    return ChineseConverter.convert(s, ConversionType.T2S);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;

    }

    public static String S2T(String s) {
        PreferenceManager preferenceManager = App.getPreferenceManager();
        try {
            switch (preferenceManager.getNumber(PreferenceManager.PREF_ST_ENGINE, PreferenceManager.ST_JCC).intValue()) {
                case PreferenceManager.ST_JCC:
                    return JChineseConvertor.getInstance().s2t(s);
                case PreferenceManager.ST_OPENCC:
//                    return ZhConverterUtil.toTraditional(s);
                    return ChineseConverter.convert(s, ConversionType.S2T);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

    public static String convert(final String s) {
        PreferenceManager preferenceManager = App.getPreferenceManager();

        switch (preferenceManager.getNumber(PreferenceManager.PREF_DETAIL_TEXT_ST, PreferenceManager.DETAIL_TEXT_DEFAULT).intValue()) {
            case PreferenceManager.DETAIL_TEXT_SIMPLE:
                try {

                    switch (preferenceManager.getNumber(PreferenceManager.PREF_ST_ENGINE, PreferenceManager.ST_JCC).intValue()) {
                        case PreferenceManager.ST_JCC:
                            return JChineseConvertor.getInstance().t2s(s);
                        case PreferenceManager.ST_OPENCC:
//                            return ZhConverterUtil.toSimple(s);
                            return ChineseConverter.convert(s, ConversionType.T2S);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case PreferenceManager.DETAIL_TEXT_TRADITIONAL:
                try {
                    switch (preferenceManager.getNumber(PreferenceManager.PREF_ST_ENGINE, PreferenceManager.ST_JCC).intValue()) {
                        case PreferenceManager.ST_JCC:
                            return JChineseConvertor.getInstance().s2t(s);
                        case PreferenceManager.ST_OPENCC:
//                            return ZhConverterUtil.toTraditional(s);
                            return ChineseConverter.convert(s, ConversionType.S2T);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
//            case PreferenceManager.DETAIL_TEXT_TRADITIONAL_TW:
//                try {
//                    switch (preferenceManager.getInt(PreferenceManager.PREF_ST_ENGINE, PreferenceManager.ST_JCC)) {
//                        case PreferenceManager.ST_JCC:
//                            return JChineseConvertor.getInstance().s2t(s);
//                        case PreferenceManager.ST_OPENCC4J:
//                            return ZhTwConverterUtil.toTraditional(s);
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                break;
            default:
                break;

        }
        return s;
    }

}