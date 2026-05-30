package models

import "time"

// Comic represents a manga/comic with history and favorite state.
// Mirrors the Android app's Comic entity fields.
type Comic struct {
	ID           uint      `gorm:"primaryKey" json:"id"`
	UserID       uint      `gorm:"index;not null" json:"user_id"`
	Source       int       `gorm:"not null" json:"source"`
	Cid          string    `gorm:"size:256;not null" json:"cid"`
	Title        string    `gorm:"size:512" json:"title"`
	Cover        string    `gorm:"size:1024" json:"cover"`
	Update       string    `gorm:"size:64" json:"update"`
	Finish       bool      `json:"finish"`
	Highlight    bool      `json:"highlight"`
	Favorite     *int64    `json:"favorite"` // timestamp millis, null if not favorited
	History      *int64    `json:"history"`  // timestamp millis, null if no history
	Last         string    `gorm:"size:256" json:"last"`
	Page         *int      `json:"page"`
	Chapter      string    `gorm:"size:256" json:"chapter"`
	ChapterCount *int      `json:"chapter_count"`
	CreatedAt    time.Time `json:"created_at"`
	UpdatedAt    time.Time `json:"updated_at"`
}

// ComicSyncRequest is the payload for uploading/merging comics.
type ComicSyncRequest struct {
	Comics []ComicSyncItem `json:"comics" binding:"required"`
}

type ComicSyncItem struct {
	Source       int    `json:"source"`
	Cid          string `json:"cid"`
	Title        string `json:"title"`
	Cover        string `json:"cover"`
	Update       string `json:"update"`
	Finish       bool   `json:"finish"`
	Highlight    bool   `json:"highlight"`
	Favorite     *int64 `json:"favorite"`
	History      *int64 `json:"history"`
	Last         string `json:"last"`
	Page         *int   `json:"page"`
	Chapter      string `json:"chapter"`
	ChapterCount *int   `json:"chapter_count"`
}
