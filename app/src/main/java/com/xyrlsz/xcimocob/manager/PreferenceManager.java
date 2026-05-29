package com.xyrlsz.xcimocob.manager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * Created by Hiroshi on 2016/8/4.
 */
public class PreferenceManager {

    public static final int READER_MODE_PAGE = 0;
    public static final int READER_MODE_STREAM = 1;

    public static final int READER_TURN_LTR = 0;
    public static final int READER_TURN_RTL = 1;
    public static final int READER_TURN_ATB = 2;

    public static final int READER_ORIENTATION_PORTRAIT = 0;
    public static final int READER_ORIENTATION_LANDSCAPE = 1;
    public static final int READER_ORIENTATION_AUTO = 2;

    public static final int HOME_FAVORITE = 0;
    public static final int HOME_SOURCE = 1;
    public static final int HOME_TAG = 2;
    public static final int HOME_HISTORY = 3;
    public static final int HOME_DOWNLOAD = 4;
    public static final int HOME_LOCAL = 5;

    public static final int DETAIL_TEXT_DEFAULT = 0;
    public static final int DETAIL_TEXT_SIMPLE = 1;
    public static final int DETAIL_TEXT_TRADITIONAL = 2;
//    public static final int DETAIL_TEXT_TRADITIONAL_TW = 3;

    public static final int ST_JCC = 0;
    public static final int ST_OPENCC = 1;
//    public static final int ST_OPENCCGO = 2;

    public static final int DARK_MODE_FALLOW_SYSTEM = 0;
    public static final int DARK_MODE_ALWAYS_DARK = 1;
    public static final int DARK_MODE_ALWAYS_LIGHT = 2;

    public static final String PREF_APP_VERSION = "pref_app_version";

    public static final String PREF_MAIN_NOTICE = "pref_main_notice";
    public static final String PREF_MAIN_NOTICE_LAST = "pref_main_notice_last";

    public static final String PREF_READER_MODE = "pref_reader_mode";
    public static final String PREF_READER_KEEP_BRIGHT = "pref_reader_keep_on";
    public static final String PREF_READER_HIDE_INFO = "pref_reader_hide";
    public static final String PREF_READER_HIDE_NAV = "pref_reader_hide_nav";
    public static final String PREF_READER_BAN_DOUBLE_CLICK = "pref_reader_ban_double_click";
    public static final String PREF_READER_PAGING = "pref_reader_paging";
    public static final String PREF_READER_CLOSEAUTORESIZEIMAGE = "pref_reader_closeautoresizeimage ";
    public static final String PREF_READER_PAGING_REVERSE = "pref_reader_paging_reverse";
    public static final String PREF_READER_PAGING_STREAM_OFF = "pref_reader_paging_stream_off";
    public static final String PREF_READER_WHITE_EDGE = "pref_reader_white_edge";
    public static final String PREF_READER_WHITE_BACKGROUND = "pref_reader_white_background";
    public static final String PREF_READER_SCALE_FACTOR = "pref_reader_scale_factor";
    public static final String PREF_READER_CONTROLLER_TRIG_THRESHOLD = "pref_reader_controller_trig_threshold";
    public static final String PREF_READER_VOLUME_KEY_CONTROLS_PAGE_TURNING = "pref_reader_volume_key_controls_page_turning";

    public static final String PREF_READER_PAGE_TURN = "pref_reader_page_turn";
    public static final String PREF_READER_PAGE_ORIENTATION = "pref_reader_page_orientation";
    public static final String PREF_READER_PAGE_CLICK_LEFT = "pref_reader_page_click_left";
    public static final String PREF_READER_PAGE_CLICK_TOP = "pref_reader_page_click_top";
    public static final String PREF_READER_PAGE_CLICK_MIDDLE = "pref_reader_page_click_middle";
    public static final String PREF_READER_PAGE_CLICK_BOTTOM = "pref_reader_page_click_bottom";
    public static final String PREF_READER_PAGE_CLICK_RIGHT = "pref_reader_page_click_right";
    public static final String PREF_READER_PAGE_CLICK_UP = "pref_reader_page_click_up";
    public static final String PREF_READER_PAGE_CLICK_DOWN = "pref_reader_page_click_down";
    public static final String PREF_READER_PAGE_JOY_LT = "pref_reader_page_joy_lt";
    public static final String PREF_READER_PAGE_JOY_RT = "pref_reader_page_joy_rt";
    public static final String PREF_READER_PAGE_JOY_A = "pref_reader_page_joy_a";
    public static final String PREF_READER_PAGE_JOY_B = "pref_reader_page_joy_b";
    public static final String PREF_READER_PAGE_JOY_X = "pref_reader_page_joy_x";
    public static final String PREF_READER_PAGE_JOY_Y = "pref_reader_page_joy_y";
    public static final String PREF_READER_PAGE_JOY_LEFT = "pref_reader_page_joy_left";
    public static final String PREF_READER_PAGE_JOY_RIGHT = "pref_reader_page_joy_right";
    public static final String PREF_READER_PAGE_JOY_UP = "pref_reader_page_joy_up";
    public static final String PREF_READER_PAGE_JOY_DOWN = "pref_reader_page_joy_down";
    public static final String PREF_READER_PAGE_LONG_CLICK_LEFT = "pref_reader_page_long_click_left";
    public static final String PREF_READER_PAGE_LONG_CLICK_TOP = "pref_reader_page_long_click_top";
    public static final String PREF_READER_PAGE_LONG_CLICK_MIDDLE = "pref_reader_page_long_click_middle";
    public static final String PREF_READER_PAGE_LONG_CLICK_BOTTOM = "pref_reader_page_long_click_bottom";
    public static final String PREF_READER_PAGE_LONG_CLICK_RIGHT = "pref_reader_page_long_click_right";
    public static final String PREF_READER_PAGE_LOAD_PREV = "pref_reader_page_load_prev";
    public static final String PREF_READER_PAGE_LOAD_NEXT = "pref_reader_page_load_next";
    public static final String PREF_READER_PAGE_TRIGGER = "pref_reader_page_trigger";
    public static final String PREF_READER_PAGE_BAN_TURN = "pref_reader_page_ban_turn";
    public static final String PREF_READER_PAGE_QUICK_TURN = "pref_reader_page_quick_turn";

    public static final String PREF_READER_STREAM_TURN = "pref_reader_stream_turn";
    public static final String PREF_READER_STREAM_ORIENTATION = "pref_reader_stream_orientation";
    public static final String PREF_READER_STREAM_CLICK_LEFT = "pref_reader_stream_click_left";
    public static final String PREF_READER_STREAM_CLICK_TOP = "pref_reader_stream_click_top";
    public static final String PREF_READER_STREAM_CLICK_MIDDLE = "pref_reader_stream_click_middle";
    public static final String PREF_READER_STREAM_CLICK_BOTTOM = "pref_reader_stream_click_bottom";
    public static final String PREF_READER_STREAM_CLICK_RIGHT = "pref_reader_stream_click_right";
    public static final String PREF_READER_STREAM_CLICK_UP = "pref_reader_stream_click_up";
    public static final String PREF_READER_STREAM_CLICK_DOWN = "pref_reader_stream_click_down";
    public static final String PREF_READER_STREAM_JOY_LT = "pref_reader_stream_joy_lt";
    public static final String PREF_READER_STREAM_JOY_RT = "pref_reader_stream_joy_rt";
    public static final String PREF_READER_STREAM_JOY_A = "pref_reader_stream_joy_a";
    public static final String PREF_READER_STREAM_JOY_B = "pref_reader_stream_joy_b";
    public static final String PREF_READER_STREAM_JOY_X = "pref_reader_stream_joy_x";
    public static final String PREF_READER_STREAM_JOY_Y = "pref_reader_stream_joy_y";
    public static final String PREF_READER_STREAM_JOY_LEFT = "pref_reader_stream_joy_left";
    public static final String PREF_READER_STREAM_JOY_RIGHT = "pref_reader_stream_joy_right";
    public static final String PREF_READER_STREAM_JOY_UP = "pref_reader_stream_joy_up";
    public static final String PREF_READER_STREAM_JOY_DOWN = "pref_reader_stream_joy_down";
    public static final String PREF_READER_STREAM_LONG_CLICK_LEFT = "pref_reader_stream_long_click_left";
    public static final String PREF_READER_STREAM_LONG_CLICK_TOP = "pref_reader_stream_long_click_top";
    public static final String PREF_READER_STREAM_LONG_CLICK_MIDDLE = "pref_reader_stream_long_click_middle";
    public static final String PREF_READER_STREAM_LONG_CLICK_BOTTOM = "pref_reader_stream_long_click_bottom";
    public static final String PREF_READER_STREAM_LONG_CLICK_RIGHT = "pref_reader_stream_long_click_right";
    public static final String PREF_READER_STREAM_LOAD_PREV = "pref_reader_stream_load_prev";
    public static final String PREF_READER_STREAM_LOAD_NEXT = "pref_reader_stream_load_next";
    public static final String PREF_READER_STREAM_INTERVAL = "pref_reader_stream_interval";

    public static final String PREF_NIGHT = "pref_night";

    public static final String PREF_UPDATE_APP_AUTO = "pref_update_app_auto";

    public static final String PREF_UPDATE_CURRENT_URL = "pref_update_current_url";

    public static final String PREF_OTHER_CHECK_UPDATE = "pref_other_check_update";
    public static final String PREF_OTHER_CHECK_SOFTWARE_UPDATE = "pref_other_check_software_update";
    public static final String PREF_OTHER_CONNECT_ONLY_WIFI = "pref_other_connect_only_wifi";
    public static final String PREF_OTHER_LOADCOVER_ONLY_WIFI = "pref_other_loadcover_only_wifi";
    public static final String PREF_OTHER_FIREBASE_EVENT = "pref_other_firebase_event";
    public static final String PREF_OTHER_REDUCE_AD = "pref_other_reduce_ad";
    public static final String PREF_OTHER_CHECK_UPDATE_LAST = "pref_other_check_update_last";
    public static final String PREF_OTHER_STORAGE = "pref_other_storage";
    public static final String PREF_OTHER_THEME = "pref_other_theme";
    public static final String PREF_OTHER_DARK_MOD = "pref_other_dark_mod";
    public static final String PREF_OTHER_LAUNCH = "pref_other_launch";
    public static final String PREF_OTHER_NIGHT_ALPHA = "pref_other_night_alpha";
    public static final String PREF_OTHER_SHOW_TOPBAR = "pref_other_show_topbar";

    public static final String PREF_DOWNLOAD_THREAD = "pref_download_thread";

    public static final String PREF_BACKUP_SAVE_COMIC = "pref_backup_save_favorite";
    public static final String PREF_BACKUP_SAVE_COMIC_CLOUD = "pref_backup_save_favorite_cloud";
    public static final String PREF_BACKUP_SAVE_COMIC_COUNT = "pref_backup_save_favorite_count";

    public static final String PREF_SEARCH_AUTO_COMPLETE = "pref_search_auto_complete";

    public static final String PREF_CHAPTER_BUTTON_MODE = "pref_chapter_button_mode";
    public static final String PREF_CHAPTER_ASCEND_MODE = "pref_chapter_ascend_mode";
    public static final String PREFERENCES_USER_TOCKEN = "user_tocken";
    public static final String PREFERENCES_USER_NAME = "user_name";
    public static final String PREFERENCES_USER_EMAIL = "user_email";
    public static final String PREFERENCES_USER_ID = "user_id";
    public static final String PREFERENCES_MH50_KEY_MSG = "preferences_mh50_key_msg";
    public static final String PREFERENCES_MH50_IV_MSG = "preferences_mh50_iv_msg";
    public static final String PREF_HHAAZZ_BASEURL = "pref_hhaazz_baseurl";
    public static final String PREF_HHAAZZ_SW = "pref_hhaazz_sw";
    public static final String PREF_DETAIL_TEXT_ST = "pref_detail_text_st";
    public static final String PREF_ST_ENGINE = "pref_st_engine";

    // 数据同步服务器配置
    public static final String PREF_DATA_SERVER_URL = "pref_data_server_url";
    public static final String PREF_DATA_SERVER_AUTO_SYNC = "pref_data_server_auto_sync";

    private static final String PREFERENCES_NAME = "cimoc_preferences";
    private final SharedPreferences mSharedPreferences;

    public PreferenceManager(Context context) {
        mSharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defValue) {
        return mSharedPreferences.getString(key, defValue);
    }

    public boolean getBoolean(String key, boolean defValue) {
        try {
            return mSharedPreferences.getBoolean(key, defValue);
        } catch (ClassCastException e) {
            // 兼容从服务器同步时存为 String 类型的情况
            String str = mSharedPreferences.getString(key, null);
            if (str != null) {
                return "true".equalsIgnoreCase(str) || "1".equals(str);
            }
            return defValue;
        }
    }

    public Number getNumber(String key, Number defValue) {
        Map<String, ?> map = mSharedPreferences.getAll();
        Object value = map.get(key);

        if (value instanceof Number) {
            return (Number) value;
        }

        // 兼容从服务器同步时存为 String 类型的情况
        if (value instanceof String) {
            try {
                String str = (String) value;
                if (str.contains(".")) {
                    return Double.parseDouble(str);
                } else {
                    return Long.parseLong(str);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return defValue;
    }

    public void putString(String key, String value) {
        mSharedPreferences.edit().putString(key, value).apply();
    }

    public void putBoolean(String key, boolean value) {
        mSharedPreferences.edit().putBoolean(key, value).apply();
    }

    public void putNumber(String key, Number value) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        if (value instanceof Integer) {
            editor.putInt(key, value.intValue());
        }
        else if (value instanceof Long) {
            editor.putLong(key, value.longValue());
        }
        else if (value instanceof Float) {
            editor.putFloat(key, value.floatValue());
        }
        else if (value instanceof Double) {
            editor.putFloat(key, value.floatValue()); // SharedPreferences 没有 double
        }
        else {
            editor.putLong(key, value.longValue());
        }

        editor.apply();
    }

    public Map<String, ?> getAll() {
        return mSharedPreferences.getAll();
    }

    public void putObject(String key, Object value) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        }
        else if (value instanceof String) {
            String str = (String) value;
            // 尝试将字符串转回正确的类型（兼容从服务器同步的场景）
            if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                editor.putBoolean(key, Boolean.parseBoolean(str));
            } else {
                try {
                    long l = Long.parseLong(str);
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        editor.putInt(key, (int) l);
                    } else {
                        editor.putLong(key, l);
                    }
                } catch (NumberFormatException e1) {
                    try {
                        float f = Float.parseFloat(str);
                        editor.putFloat(key, f);
                    } catch (NumberFormatException e2) {
                        editor.putString(key, str);
                    }
                }
            }
        }
        else if (value instanceof Number num) {

            // 判断是否整数
            double d = num.doubleValue();
            if (d == Math.floor(d)) {
                long l = num.longValue();

                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    editor.putInt(key, (int) l);
                } else {
                    editor.putLong(key, l);
                }
            } else {
                editor.putFloat(key, num.floatValue());
            }
        }

        editor.apply();
    }
}
