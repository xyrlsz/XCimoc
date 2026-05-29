package models

import "time"

type User struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	Username  string    `gorm:"uniqueIndex;size:64;not null" json:"username"`
	Password  string    `gorm:"size:64;not null" json:"-"` // SHA256(salt+password)  hex
	Salt      string    `gorm:"size:64;not null" json:"-"` // 随机盐值 hex
	IsAdmin   bool      `gorm:"not null;default:false" json:"is_admin"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type LoginRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
}

type LoginResponse struct {
	Token string `json:"token"`
	User  User   `json:"user"`
}

// CreateUserRequest 管理员创建用户
type CreateUserRequest struct {
	Username string `json:"username" binding:"required,min=2,max=32"`
	Password string `json:"password" binding:"required,min=6,max=64"`
}

// ChangePasswordRequest 修改密码
type ChangePasswordRequest struct {
	Username    string `json:"username" binding:"required"`
	NewPassword string `json:"new_password" binding:"required,min=6,max=64"`
}
