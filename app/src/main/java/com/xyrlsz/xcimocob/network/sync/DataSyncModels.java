package com.xyrlsz.xcimocob.network.sync;

import java.util.List;

/**
 * 与 data_server (Go) 通信的请求/响应数据模型
 * 对应 data_server/models/ 中的 Go 结构体
 */
public class DataSyncModels {

    // ========== Auth ==========

    public static class LoginRequest {
        public String username;
        public String password;

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    public static class LoginResponse {
        public String token;
        public User user;
    }

    public static class User {
        public long id;
        public String username;
        public String created_at;
        public String updated_at;
    }

    // ========== Comic ==========

    public static class ComicSyncItem {
        public int source;
        public String cid;
        public String title;
        public String cover;
        public String update;
        public boolean finish;
        public Long favorite;  // timestamp millis, null if not favorited
        public Long history;   // timestamp millis, null if no history
        public String last;
        public Integer page;
        public String chapter;
        public Integer chapter_count;
        public boolean clear_history; // true 表示客户端要求清除历史记录

        public ComicSyncItem() {
        }
    }

    public static class ComicSyncRequest {
        public List<ComicSyncItem> comics;

        public ComicSyncRequest(List<ComicSyncItem> comics) {
            this.comics = comics;
        }
    }

    public static class ComicSyncResponse {
        public int synced;
        public int skipped;
        public String message;
    }

    public static class ComicListResponse {
        public List<ComicServerItem> comics;
    }

    public static class ComicServerItem {
        public long id;
        public long user_id;
        public int source;
        public String cid;
        public String title;
        public String cover;
        public String update;
        public boolean finish;

        public Long favorite;
        public Long history;
        public String last;
        public Integer page;
        public String chapter;
        public Integer chapter_count;
        public String created_at;
        public String updated_at;
    }

    // ========== Setting ==========

    public static class SettingItem {
        public String key;
        public String value;

        public SettingItem() {
        }

        public SettingItem(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public static class SettingSyncRequest {
        public List<SettingItem> settings;

        public SettingSyncRequest(List<SettingItem> settings) {
            this.settings = settings;
        }
    }

    public static class SettingSyncResponse {
        public int synced;
        public String message;
    }

    public static class SettingListResponse {
        public List<SettingServerItem> settings;
    }

    public static class SettingServerItem {
        public long id;
        public long user_id;
        public String key;
        public String value;
        public String created_at;
        public String updated_at;
    }

    // ========== Error ==========

    public static class ErrorResponse {
        public String error;
    }

    // ========== Health ==========

    public static class HealthResponse {
        public String status;
        public String service;
    }
}
