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
// Optimized: 使用批量查询替代 N+1 循环查询
func (h *TagHandler) List(c *gin.Context) {
	userID := c.GetUint("user_id")

	var tags []models.Tag
	database.DB.Where("user_id = ?", userID).Find(&tags)

	// 批量查询所有 tag_id 的关联漫画
	var allRefs []models.TagRef
	database.DB.Where("user_id = ?", userID).Find(&allRefs)

	// 按 tag_id 分组
	refsByTagID := make(map[uint][]models.TagRef, len(tags))
	for _, ref := range allRefs {
		refsByTagID[ref.TagID] = append(refsByTagID[ref.TagID], ref)
	}

	type TagWithComics struct {
		models.Tag
		Comics []models.TagRef `json:"comics"`
	}

	result := make([]TagWithComics, 0, len(tags))
	for _, tag := range tags {
		refs := refsByTagID[tag.ID]
		if refs == nil {
			refs = []models.TagRef{}
		}
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
	defer tx.Rollback() // 异常时自动回滚，Commit 后调用无副作用

	// Delete existing tags and tag refs for this user
	tx.Where("user_id = ?", userID).Delete(&models.TagRef{})
	tx.Where("user_id = ?", userID).Delete(&models.Tag{})

	// Insert new tags with batch refs
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

		// 批量插入 TagRef（减少数据库 round-trip）
		refs := make([]models.TagRef, 0, len(item.Comics))
		for _, comic := range item.Comics {
			if comic.Cid == "" {
				continue
			}
			refs = append(refs, models.TagRef{
				UserID: userID,
				TagID:  tag.ID,
				Source: comic.Source,
				Cid:    comic.Cid,
			})
		}
		if len(refs) > 0 {
			if result := tx.Create(&refs); result.Error != nil {
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
