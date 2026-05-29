package models

import "time"

// Tag represents a tag/label.
type Tag struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	UserID    uint      `gorm:"index;not null" json:"user_id"`
	Title     string    `gorm:"size:128;not null" json:"title"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

// TagRef links a tag to a comic (by source+cid).
type TagRef struct {
	ID     uint   `gorm:"primaryKey" json:"id"`
	UserID uint   `gorm:"index;not null" json:"user_id"`
	TagID  uint   `gorm:"index;not null" json:"tag_id"`
	Source int    `gorm:"not null" json:"source"`
	Cid    string `gorm:"size:256;not null" json:"cid"`
}

// TagSyncRequest is the payload for uploading/merging tags with their comic references.
type TagSyncRequest struct {
	Tags []TagSyncItem `json:"tags" binding:"required"`
}

type TagSyncItem struct {
	Title  string        `json:"title"`
	Comics []TagComicRef `json:"comics"`
}

type TagComicRef struct {
	Source int    `json:"source"`
	Cid    string `json:"cid"`
}
