package handlers

import (
	"encoding/json"
	"log"
	"net/http"
	"time"

	"xcimoc-data-server/database"
	"xcimoc-data-server/models"
	"xcimoc-data-server/query"
	"xcimoc-data-server/utils"

	"github.com/gin-gonic/gin"
	"gorm.io/gen/field"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// EventHandler 暴露事件同步接口（Pull / Push / Status）。
// 所有 DB 读写统一走 gorm.io/gen 生成的 query 包：
//   - WHERE 条件不再出现裸字符串列名（例如 "user_id = ? AND id > ?"），
//     替换为 query.SyncEvent.UserID.Eq(userID) + query.SyncEvent.ID.Gt(...)；
//   - 列名/类型不匹配会在编译期直接报错，从源头避免注入与重构不一致。
type EventHandler struct{}

// NewEventHandler 创建 EventHandler。
func NewEventHandler() *EventHandler { return &EventHandler{} }

type pullReq struct {
	SinceID int64 `form:"since_id,default=0"`
	Limit   int   `form:"limit,default=500"`
}

type pullResp struct {
	Events     []models.SyncEvent `json:"events"`
	LatestID   uint               `json:"latest_id"`
	HasMore    bool               `json:"has_more"`
	ServerTime int64              `json:"server_time"`
}

// PullEvents 按自增主键 since_id 拉取增量事件。
//
// 为避免客户端在 events=[] 时 cursor 永久卡住，无论是否返回事件，
// 都会附加当前用户维度“真尾 ID(latest_id)”，客户端可直接推进到该值。
func (h *EventHandler) PullEvents(c *gin.Context) {
	userID := c.GetUint("user_id")
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "缺少认证令牌"})
		return
	}

	var req pullReq
	if err := c.ShouldBindQuery(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数无效: " + err.Error()})
		return
	}
	if req.Limit <= 0 {
		req.Limit = 500
	}
	if req.Limit > 2000 {
		req.Limit = 2000
	}
	se := query.SyncEvent

	// 真尾 ID：MAX(id)。gen 未内置 Max() 聚合快捷方法，这里用带类型安全 WHERE 的
	// UnderlyingDB() 做一次 SELECT 扫描，仍受 user_id 条件保护，字段名不手写。
	var latestID uint
	{
		tail := se.Where(se.UserID.Eq(userID))
		if err := tail.UnderlyingDB().Select("COALESCE(MAX(id),0)").Scan(&latestID).Error; err != nil {
			log.Printf("[PullEvents] compute latest_id failed: %v", err)
			c.JSON(http.StatusInternalServerError, gin.H{"error": "计算游标失败"})
			return
		}
	}

	// 游标失效兜底：客户端 since_id 大于服务端真尾 MAX(id)，典型场景是服务端数据库被重置
	// （事件 ID 从头计数，旧游标如 1578 已远大于新库 MAX(id)=22）。此时按原 since 过滤永远
	// 查不到事件，客户端游标会永久卡死、服务端已有的新事件永远拉不到。检测到则从 0 重新
	// 拉取全部事件，配合客户端侧"游标失效自愈"逻辑一起让客户端自动恢复。
	since := uint(req.SinceID)
	if since > latestID {
		log.Printf("[PullEvents] cursor %d ahead of tail %d (server data was reset?), resetting to 0", since, latestID)
		since = 0
	}

	// since_id 过滤 + 排序 + 分页，全部字段用生成的类型安全访问。
	q := se.Where(se.UserID.Eq(userID), se.ID.Gt(since))
	rows, err := q.Order(se.ID).Limit(req.Limit).Find()
	if err != nil {
		log.Printf("[PullEvents] query events failed: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "拉取事件失败"})
		return
	}

	c.JSON(http.StatusOK, pullResp{
		Events:     derefSyncEvents(rows),
		LatestID:   latestID,
		HasMore:    len(rows) == req.Limit,
		ServerTime: time.Now().UnixMilli(),
	})
}

type pushReq struct {
	Events   []models.SyncEvent `json:"events"`
	ClientID string             `json:"client_id"`
}

type pushResp struct {
	Received   int   `json:"received"`
	Accepted   int   `json:"accepted"`
	LatestID   uint  `json:"latest_id"`
	ServerTime int64 `json:"server_time"`
}

// PushEvents 接收客户端上报的一批事件（append-only 幂等写入）。
//
// 去重键采用 (user_id, client_id, type, payload_hash)：同一客户端重复推送的完全相同
// 载荷视为幂等，不重复写入。写入用唯一索引 + ON CONFLICT DO NOTHING 在单条 SQL 内
// 原子完成「查重+插入」，并发重推不会产生双写（此前「先查后插」存在 TOCTOU 窗口）。
// 整批写入放在事务中，事务内部通过 query.Use(tx) 创建临时 query 句柄，
// 避免并发请求污染全局 query.Q。
func (h *EventHandler) PushEvents(c *gin.Context) {
	userID := c.GetUint("user_id")
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "缺少认证令牌"})
		return
	}

	var req pushReq
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "参数无效: " + err.Error()})
		return
	}

	received := len(req.Events)
	accepted := 0

	err := database.DB.Transaction(func(tx *gorm.DB) error {
		qTx := query.Use(tx)

		now := time.Now()
		for _, raw := range req.Events {
			if raw.Type == "" || raw.Payload == "" {
				// 跳过空骨架载荷，不影响其他事件落库
				continue
			}

			createdAt := raw.CreatedAt
			if createdAt.IsZero() {
				createdAt = now
			}
			ev := &models.SyncEvent{
				UserID:      userID,
				Type:        raw.Type,
				Payload:     raw.Payload,
				PayloadHash: utils.SHA256Hex(raw.Payload),
				ClientID:    req.ClientID,
				CreatedAt:   createdAt,
			}
			// 幂等写入：唯一索引 (user_id, client_id, type, payload_hash) +
			// ON CONFLICT DO NOTHING，重复/并发重推时 RowsAffected=0。
			// 用事务句柄 tx 直接执行（gen 的 Create 只返回 error，拿不到 RowsAffected）。
			res := tx.Clauses(clause.OnConflict{DoNothing: true}).Create(ev)
			if res.Error != nil {
				return res.Error
			}
			accepted++
			if res.RowsAffected == 0 {
				// 幂等命中（此前已写入并已应用过）→ 无需重复 applyEvent
				continue
			}

			// 事件写入日志后，同步应用到 comics/settings/tags 数据表。
			// 若只写日志不应用，服务端 comics 表会保留过期数据且不产生墓碑，
			// 其他设备全量对账（/api/comics）时会把已删除的收藏/历史“复活”。
			if !h.applyEvent(qTx, userID, ev) {
				log.Printf("[PushEvents] apply event skipped/failed (type=%s, user_id=%d)", ev.Type, userID)
			}
		}
		return nil
	})
	if err != nil {
		log.Printf("[PushEvents] transaction failed: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "写入事件失败"})
		return
	}

	var latestID uint
	{
		tail := query.SyncEvent.Where(query.SyncEvent.UserID.Eq(userID))
		if err := tail.UnderlyingDB().Select("COALESCE(MAX(id),0)").Scan(&latestID).Error; err != nil {
			log.Printf("[PushEvents] latest_id lookup failed: %v", err)
			latestID = 0
		}
	}

	c.JSON(http.StatusOK, pushResp{
		Received:   received,
		Accepted:   accepted,
		LatestID:   latestID,
		ServerTime: time.Now().UnixMilli(),
	})
}

// EventStatus 返回当前用户的事件流统计：最新事件 ID、事件总数、漫画总数。
func (h *EventHandler) EventStatus(c *gin.Context) {
	userID := c.GetUint("user_id")
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "缺少认证令牌"})
		return
	}

	se := query.SyncEvent
	cm := query.Comic

	latestID := uint(0)
	if err := se.Where(se.UserID.Eq(userID)).
		UnderlyingDB().
		Select("COALESCE(MAX(id),0)").
		Scan(&latestID).Error; err != nil {
		log.Printf("[EventStatus] latest_id failed: %v", err)
	}

	total, err := se.Where(se.UserID.Eq(userID)).Count()
	if err != nil {
		log.Printf("[EventStatus] count events failed: %v", err)
		total = 0
	}

	comicCount, err := cm.Where(cm.UserID.Eq(userID)).Count()
	if err != nil {
		log.Printf("[EventStatus] count comics failed: %v", err)
		comicCount = 0
	}

	c.JSON(http.StatusOK, models.EventStatusResponse{
		LatestID:    latestID,
		TotalEvents: total,
		ComicCount:  comicCount,
	})
}

func derefSyncEvents(rows []*models.SyncEvent) []models.SyncEvent {
	if rows == nil {
		return []models.SyncEvent{}
	}
	out := make([]models.SyncEvent, 0, len(rows))
	for _, r := range rows {
		if r != nil {
			out = append(out, *r)
		}
	}
	return out
}

// ==================== 事件应用逻辑 ====================
// 事件写入日志后同步应用到用户的 comics/settings/tags 数据表，保证服务端
// 状态与事件流一致：收藏/阅读/取消收藏/清除历史会同步维护 comics 表与
// ComicDelete 墓碑，防止其他设备通过全量对账（/api/comics）把已删除数据“复活”。

// applyEvent 将单个事件应用到用户数据表。返回 true 表示应用成功，false 表示跳过或失败
// （不影响事件日志已落库，客户端本地重放仍可兜底）。
func (h *EventHandler) applyEvent(q *query.Query, userID uint, event *models.SyncEvent) bool {
	switch event.Type {

	case models.EventTypeFavorite:
		var p models.FavoritePayload
		if !unmarshalPayload(event.Payload, &p) {
			return false
		}
		return h.applyFavorite(q, userID, &p)

	case models.EventTypeUnfavorite:
		var p models.UnfavoritePayload
		if !unmarshalPayload(event.Payload, &p) {
			return false
		}
		return h.applyUnfavorite(q, userID, &p)

	case models.EventTypeRead:
		var p models.ReadPayload
		if !unmarshalPayload(event.Payload, &p) {
			return false
		}
		return h.applyRead(q, userID, &p)

	case models.EventTypeClearHistory:
		var p models.ClearHistoryPayload
		if !unmarshalPayload(event.Payload, &p) {
			return false
		}
		return h.applyClearHistory(q, userID, &p)

	case models.EventTypeUpdateInfo:
		var p models.UpdateInfoPayload
		if !unmarshalPayload(event.Payload, &p) {
			return false
		}
		return h.applyUpdateInfo(q, userID, &p)

	case models.EventTypeSettingUpdate:
		var p models.SettingPayload
		if !unmarshalPayload(event.Payload, &p) {
			return false
		}
		return h.applySettingUpdate(q, userID, &p)

	case models.EventTypeTagCreate:
		var p models.TagPayload
		if !unmarshalPayload(event.Payload, &p) {
			return false
		}
		return h.applyTagCreate(q, userID, &p)

	case models.EventTypeTagDelete:
		var p models.TagPayload
		if !unmarshalPayload(event.Payload, &p) {
			return false
		}
		return h.applyTagDelete(q, userID, &p)

	case models.EventTypeTagAddComic:
		var p models.TagPayload
		if !unmarshalPayload(event.Payload, &p) {
			return false
		}
		return h.applyTagAddComic(q, userID, &p)

	case models.EventTypeTagRemoveComic:
		var p models.TagPayload
		if !unmarshalPayload(event.Payload, &p) {
			return false
		}
		return h.applyTagRemoveComic(q, userID, &p)

	default:
		log.Printf("未知事件类型: %s (user_id=%d)", event.Type, userID)
		return false
	}
}

func unmarshalPayload(data string, v interface{}) bool {
	if err := json.Unmarshal([]byte(data), v); err != nil {
		log.Printf("解析事件 payload 失败: %v, data=%s", err, data)
		return false
	}
	return true
}

// applyFavorite 收藏：更新或创建 comic，只接受更新的时间戳，并解决收藏墓碑。
func (h *EventHandler) applyFavorite(q *query.Query, userID uint, p *models.FavoritePayload) bool {
	if p.Cid == "" {
		return false
	}
	cm := q.Comic
	existing, err := cm.Where(
		cm.UserID.Eq(userID),
		cm.Source.Eq(p.Source),
		cm.Cid.Eq(p.Cid),
	).Take()
	if err == nil && existing != nil {
		if existing.Favorite == nil || p.Timestamp > *existing.Favorite {
			existing.Favorite = &p.Timestamp
			if p.Title != "" {
				existing.Title = p.Title
				existing.Cover = p.Cover
				existing.Update = p.Update
				existing.Finish = p.Finish
				if p.ChapterCount != nil {
					existing.ChapterCount = p.ChapterCount
				}
			}
			if saveErr := cm.Save(existing); saveErr != nil {
				log.Printf("applyFavorite: 保存漫画失败: %v", saveErr)
				return false
			}
			h.resolveTombstone(q, userID, p.Source, p.Cid, true, false)
		}
		return true
	}
	if err != nil && !isNotFound(err) {
		log.Printf("applyFavorite: 查询漫画失败: %v", err)
		return false
	}
	comic := &models.Comic{
		UserID:       userID,
		Source:       p.Source,
		Cid:          p.Cid,
		Title:        p.Title,
		Cover:        p.Cover,
		Update:       p.Update,
		Finish:       p.Finish,
		Favorite:     &p.Timestamp,
		ChapterCount: p.ChapterCount,
	}
	if createErr := cm.Create(comic); createErr != nil {
		log.Printf("applyFavorite: 创建漫画失败: %v", createErr)
		return false
	}
	h.resolveTombstone(q, userID, p.Source, p.Cid, true, false)
	return true
}

// applyUnfavorite 取消收藏：置 favorite 为空并记录删除墓碑。
func (h *EventHandler) applyUnfavorite(q *query.Query, userID uint, p *models.UnfavoritePayload) bool {
	if p.Cid == "" {
		return false
	}
	cm := q.Comic
	if _, err := cm.Where(
		cm.UserID.Eq(userID),
		cm.Source.Eq(p.Source),
		cm.Cid.Eq(p.Cid),
	).Update(cm.Favorite, nil); err != nil {
		log.Printf("applyUnfavorite: 更新漫画失败: %v", err)
		return false
	}
	h.recordTombstone(q, userID, p.Source, p.Cid, true, false)
	return true
}

// applyRead 阅读进度：更新或创建 comic，只接受更新的时间戳，并解决历史墓碑。
func (h *EventHandler) applyRead(q *query.Query, userID uint, p *models.ReadPayload) bool {
	if p.Cid == "" {
		return false
	}
	cm := q.Comic
	existing, err := cm.Where(
		cm.UserID.Eq(userID),
		cm.Source.Eq(p.Source),
		cm.Cid.Eq(p.Cid),
	).Take()
	pageVal := p.Page
	if err == nil && existing != nil {
		if existing.History == nil || p.Timestamp > *existing.History {
			existing.History = &p.Timestamp
			existing.Chapter = p.Chapter
			existing.Page = &pageVal
			existing.Last = p.Last
			if saveErr := cm.Save(existing); saveErr != nil {
				log.Printf("applyRead: 保存漫画失败: %v", saveErr)
				return false
			}
			h.resolveTombstone(q, userID, p.Source, p.Cid, false, true)
		}
		return true
	}
	if err != nil && !isNotFound(err) {
		log.Printf("applyRead: 查询漫画失败: %v", err)
		return false
	}
	comic := &models.Comic{
		UserID:  userID,
		Source:  p.Source,
		Cid:     p.Cid,
		History: &p.Timestamp,
		Chapter: p.Chapter,
		Page:    &pageVal,
		Last:    p.Last,
	}
	if createErr := cm.Create(comic); createErr != nil {
		log.Printf("applyRead: 创建漫画失败: %v", createErr)
		return false
	}
	h.resolveTombstone(q, userID, p.Source, p.Cid, false, true)
	return true
}

// applyClearHistory 清除历史：置 history/last/page/chapter 为空并记录删除墓碑。
func (h *EventHandler) applyClearHistory(q *query.Query, userID uint, p *models.ClearHistoryPayload) bool {
	if p.Cid == "" {
		return false
	}
	cm := q.Comic
	existing, err := cm.Where(
		cm.UserID.Eq(userID),
		cm.Source.Eq(p.Source),
		cm.Cid.Eq(p.Cid),
	).Take()
	if err != nil {
		if isNotFound(err) {
			// 记录不存在则无需清除，但仍记录墓碑防止其他设备恢复
			h.recordTombstone(q, userID, p.Source, p.Cid, false, true)
			return true
		}
		log.Printf("applyClearHistory: 查询漫画失败: %v", err)
		return false
	}
	existing.History = nil
	existing.Last = ""
	existing.Page = nil
	existing.Chapter = ""
	if saveErr := cm.Save(existing); saveErr != nil {
		log.Printf("applyClearHistory: 保存漫画失败: %v", saveErr)
		return false
	}
	h.recordTombstone(q, userID, p.Source, p.Cid, false, true)
	return true
}

// applyUpdateInfo 更新漫画元信息（标题/封面/更新状态/完结/章节数）。
func (h *EventHandler) applyUpdateInfo(q *query.Query, userID uint, p *models.UpdateInfoPayload) bool {
	if p.Cid == "" {
		return false
	}
	cm := q.Comic
	assigns := []field.AssignExpr{
		cm.Title.Value(p.Title),
		cm.Cover.Value(p.Cover),
		cm.Update_.Value(p.Update),
		cm.Finish.Value(p.Finish),
	}
	if p.ChapterCount != nil {
		assigns = append(assigns, cm.ChapterCount.Value(*p.ChapterCount))
	}
	if _, err := cm.Where(
		cm.UserID.Eq(userID),
		cm.Source.Eq(p.Source),
		cm.Cid.Eq(p.Cid),
	).UpdateSimple(assigns...); err != nil {
		log.Printf("applyUpdateInfo: 更新漫画失败: %v", err)
		return false
	}
	return true
}

// applySettingUpdate 设置变更：按 (user_id, key) upsert。
func (h *EventHandler) applySettingUpdate(q *query.Query, userID uint, p *models.SettingPayload) bool {
	if p.Key == "" {
		return false
	}
	st := q.Setting
	existing, err := st.Where(st.UserID.Eq(userID), st.Key.Eq(p.Key)).Take()
	if err == nil && existing != nil {
		existing.Value = p.Value
		if saveErr := st.Save(existing); saveErr != nil {
			log.Printf("applySettingUpdate: 保存设置失败: %v", saveErr)
			return false
		}
		return true
	}
	if err != nil && !isNotFound(err) {
		log.Printf("applySettingUpdate: 查询设置失败: %v", err)
		return false
	}
	if createErr := st.Create(&models.Setting{
		UserID: userID,
		Key:    p.Key,
		Value:  p.Value,
	}); createErr != nil {
		log.Printf("applySettingUpdate: 创建设置失败: %v", createErr)
		return false
	}
	return true
}

// applyTagCreate 创建标签（幂等）并写入初始关联。
func (h *EventHandler) applyTagCreate(q *query.Query, userID uint, p *models.TagPayload) bool {
	if p.Title == "" {
		return false
	}
	tg := q.Tag
	tr := q.TagRef
	existing, err := tg.Where(tg.UserID.Eq(userID), tg.Title.Eq(p.Title)).Take()
	if err == nil && existing != nil {
		return true // 幂等：已存在跳过
	}
	if err != nil && !isNotFound(err) {
		log.Printf("applyTagCreate: 查询标签失败: %v", err)
		return false
	}
	tag := &models.Tag{UserID: userID, Title: p.Title}
	if createErr := tg.Create(tag); createErr != nil {
		log.Printf("applyTagCreate: 创建标签失败: %v", createErr)
		return false
	}
	for _, ref := range p.Comics {
		if ref.Cid == "" {
			continue
		}
		if refCreateErr := tr.Create(&models.TagRef{
			UserID: userID,
			TagID:  tag.ID,
			Source: ref.Source,
			Cid:    ref.Cid,
		}); refCreateErr != nil {
			log.Printf("applyTagCreate: 创建标签关联失败: %v", refCreateErr)
		}
	}
	return true
}

// applyTagDelete 删除标签及其关联（幂等）。
func (h *EventHandler) applyTagDelete(q *query.Query, userID uint, p *models.TagPayload) bool {
	if p.Title == "" {
		return false
	}
	tg := q.Tag
	tr := q.TagRef
	tag, err := tg.Where(tg.UserID.Eq(userID), tg.Title.Eq(p.Title)).Take()
	if err != nil {
		if isNotFound(err) {
			return true // 幂等：不存在跳过
		}
		log.Printf("applyTagDelete: 查询标签失败: %v", err)
		return false
	}
	if _, delErr := tr.Where(tr.TagID.Eq(tag.ID)).Delete(); delErr != nil {
		log.Printf("applyTagDelete: 删除标签关联失败: %v", delErr)
		return false
	}
	if _, delErr := tg.Where(tg.ID.Eq(tag.ID)).Delete(); delErr != nil {
		log.Printf("applyTagDelete: 删除标签失败: %v", delErr)
		return false
	}
	return true
}

// applyTagAddComic 给标签添加漫画关联（幂等）。
func (h *EventHandler) applyTagAddComic(q *query.Query, userID uint, p *models.TagPayload) bool {
	if p.Title == "" || p.Cid == "" {
		return false
	}
	tg := q.Tag
	tr := q.TagRef
	tag, err := tg.Where(tg.UserID.Eq(userID), tg.Title.Eq(p.Title)).Take()
	if err != nil {
		if isNotFound(err) {
			return false // 标签不存在，静默跳过
		}
		log.Printf("applyTagAddComic: 查询标签失败: %v", err)
		return false
	}
	existing, refErr := tr.Where(
		tr.UserID.Eq(userID),
		tr.TagID.Eq(tag.ID),
		tr.Source.Eq(p.Source),
		tr.Cid.Eq(p.Cid),
	).Take()
	if refErr == nil && existing != nil {
		return true // 幂等：已存在
	}
	if refErr != nil && !isNotFound(refErr) {
		log.Printf("applyTagAddComic: 查询关联失败: %v", refErr)
		return false
	}
	if createErr := tr.Create(&models.TagRef{
		UserID: userID,
		TagID:  tag.ID,
		Source: p.Source,
		Cid:    p.Cid,
	}); createErr != nil {
		log.Printf("applyTagAddComic: 创建关联失败: %v", createErr)
		return false
	}
	return true
}

// applyTagRemoveComic 移除标签下的漫画关联（幂等）。
func (h *EventHandler) applyTagRemoveComic(q *query.Query, userID uint, p *models.TagPayload) bool {
	if p.Title == "" || p.Cid == "" {
		return false
	}
	tg := q.Tag
	tr := q.TagRef
	tag, err := tg.Where(tg.UserID.Eq(userID), tg.Title.Eq(p.Title)).Take()
	if err != nil {
		if isNotFound(err) {
			return true // 幂等
		}
		log.Printf("applyTagRemoveComic: 查询标签失败: %v", err)
		return false
	}
	if _, delErr := tr.Where(
		tr.UserID.Eq(userID),
		tr.TagID.Eq(tag.ID),
		tr.Source.Eq(p.Source),
		tr.Cid.Eq(p.Cid),
	).Delete(); delErr != nil {
		log.Printf("applyTagRemoveComic: 删除关联失败: %v", delErr)
		return false
	}
	return true
}

// ==================== 墓碑（删除标记）维护 ====================
// 与 /api/comics/sync 的 ComicDelete 表保持一致：事件路径的删除也记录墓碑，
// 防止其他设备通过全量上传把已删除的数据重新上传；重新收藏/恢复历史时移除墓碑。

// recordTombstone 记录删除墓碑（幂等，可重复调用）。
func (h *EventHandler) recordTombstone(q *query.Query, userID uint, source int, cid string, deleteFav, deleteHis bool) {
	cd := q.ComicDelete
	existing, err := cd.Where(
		cd.UserID.Eq(userID),
		cd.Source.Eq(source),
		cd.Cid.Eq(cid),
	).Take()
	if err == nil && existing != nil {
		changed := false
		if deleteFav && !existing.DeleteFav {
			existing.DeleteFav = true
			changed = true
		}
		if deleteHis && !existing.DeleteHis {
			existing.DeleteHis = true
			changed = true
		}
		if changed {
			if saveErr := cd.Save(existing); saveErr != nil {
				log.Printf("recordTombstone: 保存墓碑失败: %v", saveErr)
			}
		}
		return
	}
	if err != nil && !isNotFound(err) {
		log.Printf("recordTombstone: 查询墓碑失败: %v", err)
		return
	}
	if createErr := cd.Create(&models.ComicDelete{
		UserID:    userID,
		Source:    source,
		Cid:       cid,
		DeleteFav: deleteFav,
		DeleteHis: deleteHis,
	}); createErr != nil {
		log.Printf("recordTombstone: 创建墓碑失败: %v", createErr)
	}
}

// resolveTombstone 移除墓碑对应标志（重新收藏/恢复历史即视为已认可该删除）。
func (h *EventHandler) resolveTombstone(q *query.Query, userID uint, source int, cid string, fav, his bool) {
	cd := q.ComicDelete
	existing, err := cd.Where(
		cd.UserID.Eq(userID),
		cd.Source.Eq(source),
		cd.Cid.Eq(cid),
	).Take()
	if err != nil {
		if isNotFound(err) {
			return
		}
		log.Printf("resolveTombstone: 查询墓碑失败: %v", err)
		return
	}
	if fav {
		existing.DeleteFav = false
	}
	if his {
		existing.DeleteHis = false
	}
	if !existing.DeleteFav && !existing.DeleteHis {
		if _, delErr := cd.Where(cd.ID.Eq(existing.ID)).Delete(); delErr != nil {
			log.Printf("resolveTombstone: 删除空墓碑失败: %v", delErr)
		}
	} else {
		if saveErr := cd.Save(existing); saveErr != nil {
			log.Printf("resolveTombstone: 保存墓碑失败: %v", saveErr)
		}
	}
}
