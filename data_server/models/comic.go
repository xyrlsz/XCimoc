package models

import "time"

// Comic represents a manga/comic with history and favorite state.
// Mirrors the Android app's Comic entity fields.
type Comic struct {
	ID           uint      `gorm:"primaryKey" json:"id"`
	UserID       uint      `gorm:"index:idx_user_updated,priority:1;not null;uniqueIndex:idx_user_source_cid" json:"user_id"`
	Source       int       `gorm:"not null;uniqueIndex:idx_user_source_cid" json:"source"`
	Cid          string    `gorm:"size:256;not null;uniqueIndex:idx_user_source_cid" json:"cid"`
	Title        string    `gorm:"size:512" json:"title"`
	Cover        string    `gorm:"size:1024" json:"cover"`
	Update       string    `gorm:"size:64" json:"update"`
	Finish       bool      `json:"finish"`
	Favorite     *int64    `json:"favorite"` // timestamp millis, null if not favorited
	History      *int64    `json:"history"`  // timestamp millis, null if no history
	Last         string    `gorm:"size:256" json:"last"`
	Page         *int      `json:"page"`
	Chapter      string    `gorm:"size:256" json:"chapter"`
	ChapterCount *int      `json:"chapter_count"`
	CreatedAt    time.Time `json:"created_at"`
	UpdatedAt    time.Time `gorm:"index:idx_user_updated,priority:2" json:"updated_at"`
}

// ComicDelete 记录漫画删除操作，用于多端同步时传播删除信息。
// 当用户在设备A上清除历史/收藏或删除漫画时，在此表记录。
// 其他设备同步时查询此表，避免将已删除的数据重新上传。
type ComicDelete struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	UserID    uint      `gorm:"index:idx_del_user_created,priority:1;not null;uniqueIndex:idx_user_del_source_cid" json:"user_id"`
	Source    int       `gorm:"not null;uniqueIndex:idx_user_del_source_cid" json:"source"`
	Cid       string    `gorm:"size:256;not null;uniqueIndex:idx_user_del_source_cid" json:"cid"`
	DeleteFav bool      `json:"delete_fav"`                                              // 是否清除了收藏
	DeleteHis bool      `json:"delete_his"`                                              // 是否清除了历史
	CreatedAt time.Time `gorm:"index:idx_del_user_created,priority:2" json:"created_at"` // 删除时间（用于增量查询 since）
}

// SyncStatus 服务端同步状态信息
type SyncStatus struct {
	ServerTime int64 `json:"server_time"` // 当前服务器时间（毫秒）
	ComicCount int   `json:"comic_count"` // 用户漫画总数
}

// ComicSyncRequest is the payload for uploading/merging comics.
// Comics 不再使用 binding:"required"：允许客户端上传空数组（"我这边没变化"），
// 否则空漫画库 / 仅下载 / 只拉取增量的场景会被 Gin 在 400 就挡掉。
// handler 内部会按业务判断 len(Comics)==0 的分支。
type ComicSyncRequest struct {
	Comics   []ComicSyncItem `json:"comics"`
	Since    *int64          `json:"since"`     // 客户端上次同步时间，服务端可用此过滤（可选）
	PushOnly bool            `json:"push_only"` // 仅推送变更，不返回全量数据
}

type ComicSyncItem struct {
	Source        int    `json:"source"`
	Cid           string `json:"cid"`
	Title         string `json:"title"`
	Cover         string `json:"cover"`
	Update        string `json:"update"`
	Finish        bool   `json:"finish"`
	Favorite      *int64 `json:"favorite"`
	History       *int64 `json:"history"`
	Last          string `json:"last"`
	Page          *int   `json:"page"`
	Chapter       string `json:"chapter"`
	ChapterCount  *int   `json:"chapter_count"`
	ClearHistory  bool   `json:"clear_history"`  // true 时表示客户端明确要求清除历史记录
	ClearFavorite bool   `json:"clear_favorite"` // true 时表示客户端明确要求清除收藏
}

// ComicSyncResponse 同步响应（增强版，支持增量同步）
type ComicSyncResponse struct {
	Synced     int               `json:"synced"`
	Skipped    int               `json:"skipped"`
	Message    string            `json:"message"`
	ServerTime int64             `json:"server_time"`       // 服务器时间，客户端存储为 last_synced_at
	Comics     []Comic           `json:"comics,omitempty"`  // 全量返回时填充
	Deletes    []ComicDeleteItem `json:"deletes,omitempty"` // 其他设备的删除记录
	HasMore    bool              `json:"has_more"`          // 是否还有更多数据（分页用）
}

// ComicDeleteItem 删除记录的精简表示（返回给客户端）
type ComicDeleteItem struct {
	Source    int    `json:"source"`
	Cid       string `json:"cid"`
	DeleteFav bool   `json:"delete_fav"`
	DeleteHis bool   `json:"delete_his"`
}

// ComicListResponse 增量拉取时的响应
type ComicListResponse struct {
	Comics     []Comic           `json:"comics"`
	Deletes    []ComicDeleteItem `json:"deletes,omitempty"`
	ServerTime int64             `json:"server_time"`
}

// ==================== 事件同步系统 (Event Sourcing) ====================

// SyncEvent 是同步的最小单位，记录用户在某个设备上执行的一个操作。
// 所有客户端通过拉取事件日志来实现多端同步，无需比较完整状态。
// 事件是不可变的（append-only），全局有序（自增ID），可幂等重放。
//
// 幂等去重：(user_id, client_id, type, payload_hash) 唯一索引 + 写入时
// ON CONFLICT DO NOTHING（见 handlers/events.PushEvents）。Payload 为 text，
// 无法直接进唯一索引（MySQL 需前缀长度），故冗余一列 payload_hash = SHA-256(payload)
// 作为索引键。
//
// ⚠️ payload_hash 声明为 `-:migration`（gorm 迁移完全跳过该列）：
// gorm 的 SQLite MigrateColumn 发现列定义差异时会触发 recreateTable 表重建，
// 而重建的 INSERT 不拷贝「被修改的列」（该列数据被重置为默认值）——曾导致哈希
// 被清空、唯一索引创建失败（UNIQUE constraint failed）。该列的补列/回填/去重/
// 唯一索引统一由 database.ensureSyncEventDedupe() 手工管理，不让 gorm 介入。
type SyncEvent struct {
	ID          uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID      uint      `gorm:"index:idx_user_events;not null" json:"user_id"`
	Type        string    `gorm:"size:32;not null;index:idx_user_events" json:"type"` // 事件类型
	Payload     string    `gorm:"type:text;not null" json:"payload"`                  // JSON 格式
	PayloadHash string    `gorm:"-:migration;size:64;not null;default:''" json:"-"`   // SHA-256(payload)，幂等去重键（迁移手工管理）
	ClientID    string    `gorm:"size:64" json:"client_id,omitempty"`                 // 产生事件的设备
	CreatedAt   time.Time `gorm:"index:idx_user_events" json:"created_at"`
}

// 事件类型常量
const (
	EventTypeFavorite       = "favorite"
	EventTypeUnfavorite     = "unfavorite"
	EventTypeRead           = "read"
	EventTypeClearHistory   = "clear_history"
	EventTypeUpdateInfo     = "update_info"
	EventTypeTagCreate      = "tag_create"
	EventTypeTagDelete      = "tag_delete"
	EventTypeTagAddComic    = "tag_add_comic"
	EventTypeTagRemoveComic = "tag_remove_comic"
	EventTypeSettingUpdate  = "setting_update"
)

// FavoritePayload 收藏事件
type FavoritePayload struct {
	Source       int    `json:"source"`
	Cid          string `json:"cid"`
	Title        string `json:"title,omitempty"`
	Cover        string `json:"cover,omitempty"`
	Update       string `json:"update,omitempty"`
	Finish       bool   `json:"finish,omitempty"`
	ChapterCount *int   `json:"chapter_count,omitempty"`
	Timestamp    int64  `json:"timestamp"`
}

// UnfavoritePayload 取消收藏
type UnfavoritePayload struct {
	Source int    `json:"source"`
	Cid    string `json:"cid"`
}

// ReadPayload 阅读进度
type ReadPayload struct {
	Source    int    `json:"source"`
	Cid       string `json:"cid"`
	Chapter   string `json:"chapter"`
	Page      int    `json:"page"`
	Last      string `json:"last"`
	Timestamp int64  `json:"timestamp"`
}

// ClearHistoryPayload 清除历史
type ClearHistoryPayload struct {
	Source int    `json:"source"`
	Cid    string `json:"cid"`
}

// UpdateInfoPayload 更新元信息
type UpdateInfoPayload struct {
	Source       int    `json:"source"`
	Cid          string `json:"cid"`
	Title        string `json:"title"`
	Cover        string `json:"cover"`
	Update       string `json:"update"`
	Finish       bool   `json:"finish"`
	ChapterCount *int   `json:"chapter_count,omitempty"`
}

// TagPayload 标签操作
type TagPayload struct {
	Title  string        `json:"title"`
	Source int           `json:"source,omitempty"`
	Cid    string        `json:"cid,omitempty"`
	Comics []TagComicRef `json:"comics,omitempty"`
}

// SettingPayload 设置变更
type SettingPayload struct {
	Key   string `json:"key"`
	Value string `json:"value"`
}

// PushEventsRequest 客户端推送事件
// Events 不使用 binding:"required"：允许客户端推送空事件数组（心跳/拉取触发空推送等）。
// 空数组分支由 handlers/events.PushEvents 业务层直接返回 {"received":0}。
type PushEventsRequest struct {
	Events   []SyncEvent `json:"events"`
	ClientID string      `json:"client_id"`
}

// PullEventsResponse 拉取事件响应
type PullEventsResponse struct {
	Events   []SyncEvent `json:"events"`
	LatestID uint        `json:"latest_id"`
	HasMore  bool        `json:"has_more"`
}

// EventStatusResponse 事件流状态
type EventStatusResponse struct {
	LatestID    uint  `json:"latest_id"`
	TotalEvents int64 `json:"total_events"`
	ComicCount  int64 `json:"comic_count"`
}
