package handlers

import (
	"net/http"

	"xcimoc-data-server/database"
	"xcimoc-data-server/models"

	"github.com/gin-gonic/gin"
)

type TagHandler struct{}

func NewTagHandler() *TagHandler {
	return &TagHandler{}
}

// List returns all tags with their associated comics for the authenticated user.
func (h *TagHandler) List(c *gin.Context) {
	userID := c.GetUint("user_id")

	var tags []models.Tag
	database.DB.Where("user_id = ?", userID).Find(&tags)

	type TagWithComics struct {
		models.Tag
		Comics []models.TagRef `json:"comics"`
	}

	result := make([]TagWithComics, 0, len(tags))
	for _, tag := range tags {
		var refs []models.TagRef
		database.DB.Where("user_id = ? AND tag_id = ?", userID, tag.ID).Find(&refs)
		result = append(result, TagWithComics{
			Tag:    tag,
			Comics: refs,
		})
	}

	c.JSON(http.StatusOK, gin.H{"tags": result})
}

// Sync uploads/merges tags and their comic references.
// Strategy: replace all tags for the user — delete existing, then insert new ones.
// This is simpler than per-item merge for tagging data.
func (h *TagHandler) Sync(c *gin.Context) {
	userID := c.GetUint("user_id")

	var req models.TagSyncRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "请求参数无效: " + err.Error()})
		return
	}

	// Start a transaction
	tx := database.DB.Begin()

	// Delete existing tags and tag refs for this user
	tx.Where("user_id = ?", userID).Delete(&models.TagRef{})
	tx.Where("user_id = ?", userID).Delete(&models.Tag{})

	// Insert new tags
	for _, item := range req.Tags {
		if item.Title == "" {
			continue
		}

		tag := models.Tag{
			UserID: userID,
			Title:  item.Title,
		}
		if result := tx.Create(&tag); result.Error != nil {
			tx.Rollback()
			c.JSON(http.StatusInternalServerError, gin.H{"error": "保存标签失败: " + result.Error.Error()})
			return
		}

		// Insert tag refs
		for _, comic := range item.Comics {
			if comic.Cid == "" {
				continue
			}
			ref := models.TagRef{
				UserID: userID,
				TagID:  tag.ID,
				Source: comic.Source,
				Cid:    comic.Cid,
			}
			if result := tx.Create(&ref); result.Error != nil {
				tx.Rollback()
				c.JSON(http.StatusInternalServerError, gin.H{"error": "保存标签关联失败"})
				return
			}
		}
	}

	tx.Commit()

	c.JSON(http.StatusOK, gin.H{
		"message": "标签同步完成",
	})
}
