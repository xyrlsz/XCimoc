package handlers

import (
	"log"
	"net/http"

	"xcimoc-data-server/database"
	"xcimoc-data-server/models"

	"github.com/gin-gonic/gin"
)

type SettingHandler struct{}

func NewSettingHandler() *SettingHandler {
	return &SettingHandler{}
}

// List returns all settings for the authenticated user.
func (h *SettingHandler) List(c *gin.Context) {
	userID := c.GetUint("user_id")

	var settings []models.Setting
	result := database.DB.Where("user_id = ?", userID).Find(&settings)
	if result.Error != nil {
		log.Printf("获取设置失败 (user_id=%d): %v", userID, result.Error)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取设置失败"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"settings": settings})
}

// Sync merges uploaded settings with the server data.
// Client settings take precedence (client is source of truth for user preferences).
func (h *SettingHandler) Sync(c *gin.Context) {
	userID := c.GetUint("user_id")

	var req models.SettingSyncRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "请求参数无效: " + err.Error()})
		return
	}

	synced := 0

	for _, item := range req.Settings {
		if item.Key == "" {
			continue
		}

		var existing models.Setting
		result := database.DB.Where("user_id = ? AND key = ?", userID, item.Key).Limit(1).Find(&existing)

		if result.RowsAffected > 0 {
			// Update existing setting
			existing.Value = item.Value
			database.DB.Save(&existing)
		} else {
			// Create new setting
			setting := models.Setting{
				UserID: userID,
				Key:    item.Key,
				Value:  item.Value,
			}
			database.DB.Create(&setting)
		}
		synced++
	}

	c.JSON(http.StatusOK, gin.H{
		"synced":  synced,
		"message": "设置同步完成",
	})
}
