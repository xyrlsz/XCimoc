package handlers

import (
	"net/http"
	"strconv"

	"xcimoc-data-server/database"
	"xcimoc-data-server/models"

	"github.com/gin-gonic/gin"
)

type ComicHandler struct{}

func NewComicHandler() *ComicHandler {
	return &ComicHandler{}
}

// List returns all comics for the authenticated user.
func (h *ComicHandler) List(c *gin.Context) {
	userID := c.GetUint("user_id")

	var comics []models.Comic
	result := database.DB.Where("user_id = ?", userID).
		Order("updated_at DESC").
		Find(&comics)
	if result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取漫画列表失败"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"comics": comics})
}

// Sync merges uploaded comics with the server data.
// Merge strategy: match by (source, cid) per user.
// - If the comic exists on server and has a newer history timestamp, keep the server version.
// - Otherwise, update with the client version.
func (h *ComicHandler) Sync(c *gin.Context) {
	userID := c.GetUint("user_id")

	var req models.ComicSyncRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "请求参数无效: " + err.Error()})
		return
	}

	synced := 0
	skipped := 0

	for _, item := range req.Comics {
		if item.Cid == "" {
			continue
		}

		// Look for existing comic by (source, cid)
		// 使用 Find 而非 First，避免 GORM 打印 ErrRecordNotFound 日志噪音
		var existing models.Comic
		result := database.DB.Where("user_id = ? AND source = ? AND cid = ?", userID, item.Source, item.Cid).Limit(1).Find(&existing)

		if result.RowsAffected > 0 {
			// Comic exists — merge
			needsUpdate := false

			// Prefer newer history
			if item.History != nil && (existing.History == nil || *item.History > *existing.History) {
				existing.History = item.History
				existing.Last = item.Last
				existing.Page = item.Page
				existing.Chapter = item.Chapter
				needsUpdate = true
			}

			// Prefer newer favorite
			if item.Favorite != nil && (existing.Favorite == nil || *item.Favorite > *existing.Favorite) {
				existing.Favorite = item.Favorite
				needsUpdate = true
			}

			// Update metadata if client has newer data (treat history as update timestamp)
			if item.History != nil && (existing.History == nil || *item.History > *existing.History) {
				existing.Title = item.Title
				existing.Cover = item.Cover
				existing.Update = item.Update
				existing.Finish = item.Finish
				if item.ChapterCount != nil {
					existing.ChapterCount = item.ChapterCount
				}
				needsUpdate = true
			}

			if needsUpdate {
				database.DB.Save(&existing)
				synced++
			} else {
				skipped++
			}
			// Update highlight if client sent different value
			if item.Highlight != existing.Highlight {
				existing.Highlight = item.Highlight
				needsUpdate = true
			}

		} else {
			// New comic — create
			comic := models.Comic{
				UserID:       userID,
				Source:       item.Source,
				Cid:          item.Cid,
				Title:        item.Title,
				Cover:        item.Cover,
				Update:       item.Update,
				Finish:       item.Finish,
				Highlight:    item.Highlight,
				Favorite:     item.Favorite,
				History:      item.History,
				Last:         item.Last,
				Page:         item.Page,
				Chapter:      item.Chapter,
				ChapterCount: item.ChapterCount,
			}
			database.DB.Create(&comic)
			synced++
		}
	}

	c.JSON(http.StatusOK, gin.H{
		"synced":  synced,
		"skipped": skipped,
		"message": "同步完成",
	})
}

// Delete removes a specific comic sync record.
func (h *ComicHandler) Delete(c *gin.Context) {
	userID := c.GetUint("user_id")
	idStr := c.Param("id")
	id, err := strconv.ParseUint(idStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "无效的ID"})
		return
	}

	result := database.DB.Where("id = ? AND user_id = ?", id, userID).Delete(&models.Comic{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "记录不存在"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "删除成功"})
}
