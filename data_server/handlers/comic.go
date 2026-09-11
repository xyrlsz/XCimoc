package handlers

import (
	"errors"
	"log"
	"net/http"
	"sort"
	"strconv"
	"sync"
	"time"

	"xcimoc-data-server/database"
	"xcimoc-data-server/models"
	"xcimoc-data-server/query"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type ComicHandler struct{}

func NewComicHandler() *ComicHandler { return &ComicHandler{} }

// List 返回当前用户的漫画列表；支持 ?since=millis 做增量拉取。
// 所有过滤条件（user_id / updated_at / 排序）统一通过 gorm.io/gen 生成的字段表达，
// 避免在代码中出现 "user_id = ?"、"updated_at > ?" 这类字符串 WHERE。
func (h *ComicHandler) List(c *gin.Context) {
	userID := c.GetUint("user_id")

	cm := query.Comic
	q := cm.Where(cm.UserID.Eq(userID))

	if sinceStr := c.Query("since"); sinceStr != "" {
		if sinceMillis, err := strconv.ParseInt(sinceStr, 10, 64); err == nil && sinceMillis > 0 {
			sinceTime := time.Unix(0, sinceMillis*int64(time.Millisecond))
			q = q.Where(cm.UpdatedAt.Gt(sinceTime))
		}
	}

	rows, err := q.Order(cm.UpdatedAt.Desc()).Find()
	if err != nil {
		log.Printf("获取漫画列表失败 (user_id=%d): %v", userID, err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "获取漫画列表失败"})
		return
	}
	comics := make([]models.Comic, 0, len(rows))
	for _, r := range rows {
		if r != nil {
			comics = append(comics, *r)
		}
	}

	serverTime := time.Now().UnixMilli()

	var deletes []models.ComicDeleteItem
	if c.Query("since") != "" {
		cd := query.ComicDelete
		dq := cd.Where(cd.UserID.Eq(userID))
		if sinceMillis, err := strconv.ParseInt(c.Query("since"), 10, 64); err == nil && sinceMillis > 0 {
			sinceTime := time.Unix(0, sinceMillis*int64(time.Millisecond))
			dq = dq.Where(cd.CreatedAt.Gt(sinceTime))
		}
		delRows, dErr := dq.Find()
		if dErr == nil {
			deletes = make([]models.ComicDeleteItem, 0, len(delRows))
			for _, d := range delRows {
				if d == nil {
					continue
				}
				deletes = append(deletes, models.ComicDeleteItem{
					Source:    d.Source,
					Cid:       d.Cid,
					DeleteFav: d.DeleteFav,
					DeleteHis: d.DeleteHis,
				})
			}
		}
	}

	c.JSON(http.StatusOK, models.ComicListResponse{
		Comics:     comics,
		Deletes:    deletes,
		ServerTime: serverTime,
	})
}

// Sync 按 (user_id, source, cid) 复合匹配合并客户端上传的漫画状态。
// 这里的比较/写入/删表操作都通过生成的 query 字段执行，保证字段名/类型在编译期一致。
//
// 性能优化要点（针对客户端每次全量上传所有收藏/历史漫画的场景）：
//  1. 事务内一次性批量预载本用户全部漫画 + 墓碑到内存 map；
//  2. 循环里只做内存对账 + 收集写入目标（不执行任何 SQL），消除 N 条
//     Save/Create 语句（SQLite 与 localhost MySQL/PG 每条 ~0.3ms × N
//     是 156ms 的主因）；
//  3. 循环结束后用 4~5 条批量 SQL（Save/Create/批量 Delete）一次性写入；
//  4. 非 PushOnly 时基于内存 map 用协程并行构建响应切片，省掉两次全表 SELECT。
func (h *ComicHandler) Sync(c *gin.Context) {
	// t0 := time.Now()
	userID := c.GetUint("user_id")
	// bodyKB := c.Request.ContentLength / 1024

	var req models.ComicSyncRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "请求参数无效: " + err.Error()})
		return
	}
	// tParse := time.Since(t0)

	synced := 0
	skipped := 0

	// 事务内的内存快照：事务结束后直接基于这些 map 构建响应，
	// 避免再发两次全表 SELECT。
	var (
		existingMap map[string]*models.Comic
		delMap      map[string]*models.ComicDelete
		newComics   []*models.Comic
		// 批量写入收集器：循环里只收集、不执行，结束后一次性批量写
		comicsToUpdate       []*models.Comic
		comicsToCreate       []*models.Comic
		tombstoneUpdates     []*models.ComicDelete
		tombstoneCreates     []*models.ComicDelete
		tombstoneIDsToDelete []uint
	)

	// tTxStart := time.Now()
	err := database.DB.Transaction(func(tx *gorm.DB) error {
		qTx := query.Use(tx)
		cm := qTx.Comic
		cd := qTx.ComicDelete

		// 1) 批量预载现有漫画 → map[source:cid]*models.Comic
		existingRows, loadErr := cm.Where(cm.UserID.Eq(userID)).Find()
		if loadErr != nil {
			log.Printf("批量读取漫画失败 (user_id=%d): %v", userID, loadErr)
			return loadErr
		}
		existingMap = make(map[string]*models.Comic, len(existingRows))
		for _, r := range existingRows {
			if r != nil {
				existingMap[comicKey(r.Source, r.Cid)] = r
			}
		}

		// 2) 批量预载删除墓碑 → map[source:cid]*models.ComicDelete
		delRows, loadErr := cd.Where(cd.UserID.Eq(userID)).Find()
		if loadErr != nil {
			log.Printf("批量读取墓碑失败 (user_id=%d): %v", userID, loadErr)
			return loadErr
		}
		delMap = make(map[string]*models.ComicDelete, len(delRows))
		for _, r := range delRows {
			if r != nil {
				delMap[comicKey(r.Source, r.Cid)] = r
			}
		}
		// tPreload := time.Since(tTxStart)

		// 3) 循环对账：只收集写入目标，不执行任何 SQL
		// tLoopStart := time.Now()
		for _, item := range req.Comics {
			if item.Cid == "" {
				continue
			}
			key := comicKey(item.Source, item.Cid)

			// 1) 多端删除墓碑冲突处理（命中内存 map）
			delRecord := delMap[key]
			favResolved := false
			hisResolved := false

			if delRecord != nil {
				if delRecord.DeleteFav && item.Favorite != nil && !item.ClearFavorite {
					item.Favorite = nil
					item.ClearFavorite = true
					log.Printf("多端冲突: 漫画 %d:%s 的收藏被其他设备删除，拒绝恢复", item.Source, item.Cid)
				}
				if delRecord.DeleteHis && item.History != nil && !item.ClearHistory {
					item.History = nil
					item.ClearHistory = true
					log.Printf("多端冲突: 漫画 %d:%s 的历史被其他设备删除，拒绝恢复", item.Source, item.Cid)
				}

				if delRecord.DeleteFav && item.ClearFavorite {
					delRecord.DeleteFav = false
					favResolved = true
				}
				if delRecord.DeleteHis && item.ClearHistory {
					delRecord.DeleteHis = false
					hisResolved = true
				}
				if !delRecord.DeleteFav && !delRecord.DeleteHis {
					// 两个标志都清掉 → 整条墓碑删除（收集 ID，批量删）
					tombstoneIDsToDelete = append(tombstoneIDsToDelete, delRecord.ID)
					delete(delMap, key)
				} else {
					// 有变更 → 收集待批量 Save
					tombstoneUpdates = append(tombstoneUpdates, delRecord)
				}
			}

			// 2) 查找现有漫画记录（内存 map）
			existing := existingMap[key]
			if existing != nil {
				needsUpdate := false

				if item.History != nil && (existing.History == nil || *item.History > *existing.History) {
					existing.History = item.History
					existing.Last = item.Last
					existing.Page = item.Page
					existing.Chapter = item.Chapter
					existing.Title = item.Title
					existing.Cover = item.Cover
					existing.Update = item.Update
					existing.Finish = item.Finish
					if item.ChapterCount != nil {
						existing.ChapterCount = item.ChapterCount
					}
					needsUpdate = true
				}
				if item.Favorite != nil && (existing.Favorite == nil || *item.Favorite > *existing.Favorite) {
					existing.Favorite = item.Favorite
					needsUpdate = true
				}
				if item.ClearHistory {
					if existing.History != nil {
						existing.History = nil
						needsUpdate = true
					}
					if !hisResolved {
						h.recordDelete(delMap, userID, item.Source, item.Cid, false, true,
							&tombstoneUpdates, &tombstoneCreates)
					}
				}
				if item.ClearFavorite {
					if existing.Favorite != nil {
						existing.Favorite = nil
						needsUpdate = true
					}
					if !favResolved {
						h.recordDelete(delMap, userID, item.Source, item.Cid, true, false,
							&tombstoneUpdates, &tombstoneCreates)
					}
				}

				if needsUpdate {
					// gen 的 Save(...) 走 ON CONFLICT(主键) UPDATE ALL
					comicsToUpdate = append(comicsToUpdate, existing)
				} else {
					skipped++
				}
				continue
			}

			// 3) 新建分支
			if item.Favorite == nil && item.History == nil {
				if item.ClearHistory && !hisResolved {
					h.recordDelete(delMap, userID, item.Source, item.Cid, false, true,
						&tombstoneUpdates, &tombstoneCreates)
				}
				if item.ClearFavorite && !favResolved {
					h.recordDelete(delMap, userID, item.Source, item.Cid, true, false,
						&tombstoneUpdates, &tombstoneCreates)
				}
				skipped++
				continue
			}

			newComic := &models.Comic{
				UserID:       userID,
				Source:       item.Source,
				Cid:          item.Cid,
				Title:        item.Title,
				Cover:        item.Cover,
				Update:       item.Update,
				Finish:       item.Finish,
				Favorite:     item.Favorite,
				History:      item.History,
				Last:         item.Last,
				Page:         item.Page,
				Chapter:      item.Chapter,
				ChapterCount: item.ChapterCount,
			}
			comicsToCreate = append(comicsToCreate, newComic)
			newComics = append(newComics, newComic)
		}
		// tLoop := time.Since(tLoopStart)

		// 4) 批量执行：分块避免 SQLite 单语句变量数上限（默认 998，
		// 每行 ~15 列 → 每块 ≤ 60 行），MySQL/PG 同样安全。
		const batchSize = 50
		// tBulkStart := time.Now()
		if len(comicsToUpdate) > 0 {
			for i := 0; i < len(comicsToUpdate); i += batchSize {
				end := i + batchSize
				if end > len(comicsToUpdate) {
					end = len(comicsToUpdate)
				}
				batch := comicsToUpdate[i:end]
				if err := cm.Save(batch...); err != nil {
					log.Printf("批量更新漫画失败 (user_id=%d, batch %d-%d): %v", userID, i, end, err)
				} else {
					synced += len(batch)
				}
			}
		}
		if len(comicsToCreate) > 0 {
			if err := cm.CreateInBatches(comicsToCreate, batchSize); err != nil {
				log.Printf("批量创建漫画失败 (user_id=%d, n=%d): %v", userID, len(comicsToCreate), err)
			} else {
				synced += len(comicsToCreate)
			}
		}
		if len(tombstoneUpdates) > 0 {
			for i := 0; i < len(tombstoneUpdates); i += batchSize {
				end := i + batchSize
				if end > len(tombstoneUpdates) {
					end = len(tombstoneUpdates)
				}
				batch := tombstoneUpdates[i:end]
				if err := cd.Save(batch...); err != nil {
					log.Printf("批量更新墓碑失败 (user_id=%d, batch %d-%d): %v", userID, i, end, err)
				}
			}
		}
		if len(tombstoneCreates) > 0 {
			if err := cd.CreateInBatches(tombstoneCreates, batchSize); err != nil {
				log.Printf("批量创建墓碑失败 (user_id=%d, n=%d): %v", userID, len(tombstoneCreates), err)
			}
		}
		if len(tombstoneIDsToDelete) > 0 {
			if _, err := cd.Where(cd.ID.In(tombstoneIDsToDelete...)).Delete(); err != nil {
				log.Printf("批量删除墓碑失败 (user_id=%d, n=%d): %v", userID, len(tombstoneIDsToDelete), err)
			}
		}
		// tBulk := time.Since(tBulkStart)

		// log.Printf("[comics/sync timing] user=%d items=%d body=%dKB parse=%v "+
		// 	"preload=%v loop=%v bulk=%v (up=%d cr=%d tu=%d tc=%d td=%d skipped=%d)",
		// 	userID, len(req.Comics), bodyKB, tParse,
		// 	tPreload, tLoop, tBulk,
		// 	len(comicsToUpdate), len(comicsToCreate), len(tombstoneUpdates),
		// 	len(tombstoneCreates), len(tombstoneIDsToDelete), skipped)
		return nil
	})
	if err != nil {
		log.Printf("漫画同步事务失败 (user_id=%d): %v", userID, err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "同步失败"})
		return
	}

	serverTime := time.Now().UnixMilli()
	if req.PushOnly {
		c.JSON(http.StatusOK, models.ComicSyncResponse{
			Synced:     synced,
			Skipped:    skipped,
			Message:    "同步完成",
			ServerTime: serverTime,
		})
		// log.Printf("[comics/sync timing] user=%d total=%v (pushOnly, tx=%v)",
		// 	userID, time.Since(t0), time.Since(tTxStart))
		return
	}

	// 基于事务内的内存快照构建响应。comics 与 deletes 是两段独立的 CPU 工作
	// （拷贝 + 排序），放到两个协程里并行执行；事务已提交，map 不再被写，
	// 只读访问零竞争。SQLite 单连接下也完全适用——这段不碰数据库。
	var (
		comics  []models.Comic
		deletes []models.ComicDeleteItem
	)
	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		comics = make([]models.Comic, 0, len(existingMap)+len(newComics))
		for _, cc := range existingMap {
			if cc != nil {
				comics = append(comics, *cc)
			}
		}
		for _, cc := range newComics {
			if cc != nil {
				comics = append(comics, *cc)
			}
		}
		// 保持与原 SQL ORDER BY updated_at DESC 一致的返回顺序
		sort.Slice(comics, func(i, j int) bool {
			return comics[i].UpdatedAt.After(comics[j].UpdatedAt)
		})
	}()
	go func() {
		defer wg.Done()
		deletes = make([]models.ComicDeleteItem, 0, len(delMap))
		for _, d := range delMap {
			if d == nil {
				continue
			}
			deletes = append(deletes, models.ComicDeleteItem{
				Source:    d.Source,
				Cid:       d.Cid,
				DeleteFav: d.DeleteFav,
				DeleteHis: d.DeleteHis,
			})
		}
	}()
	wg.Wait()

	c.JSON(http.StatusOK, models.ComicSyncResponse{
		Synced:     synced,
		Skipped:    skipped,
		Message:    "同步完成",
		ServerTime: serverTime,
		Comics:     comics,
		Deletes:    deletes,
	})
	// log.Printf("[comics/sync timing] user=%d total=%v (tx=%v, build+marshal=%v)",
	// 	userID, time.Since(t0), time.Since(tTxStart), time.Since(t0)-time.Since(tTxStart))
}

// recordDelete 按 (user_id, source, cid) 更新或创建 ComicDelete 墓碑。
//
// 直接复用事务内预载的 delMap 做查找，避免在 N 条漫画的循环里再发 N 次
// Take 查询（典型的 N+1）。本函数只收集写入目标（追加到 updates/creates），
// 不执行任何 SQL——实际写入由调用方在循环结束后一次性批量执行，
// 把 N 条墓碑写语句压成 1~2 条批量 SQL。
//
// 新建的墓碑也会回写到 delMap，保证后续逻辑（以及事务结束后的响应构建）
// 能立即见到最新状态。
func (h *ComicHandler) recordDelete(
	delMap map[string]*models.ComicDelete,
	userID uint,
	source int,
	cid string,
	deleteFav, deleteHis bool,
	updates *[]*models.ComicDelete,
	creates *[]*models.ComicDelete,
) {
	key := comicKey(source, cid)
	existing := delMap[key]
	if existing != nil {
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
			*updates = append(*updates, existing)
		}
		return
	}
	// 不存在 → 创建新墓碑并回写 map，后续循环 / 响应构建可直接复用
	newDel := &models.ComicDelete{
		UserID:    userID,
		Source:    source,
		Cid:       cid,
		DeleteFav: deleteFav,
		DeleteHis: deleteHis,
	}
	*creates = append(*creates, newDel)
	delMap[key] = newDel
}

// Delete 按漫画主键删除（仅允许本人）。
func (h *ComicHandler) Delete(c *gin.Context) {
	userID := c.GetUint("user_id")
	idStr := c.Param("id")
	id, err := strconv.ParseUint(idStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "无效的ID"})
		return
	}
	cm := query.Comic
	res, err := cm.Where(cm.ID.Eq(uint(id)), cm.UserID.Eq(userID)).Delete()
	if err != nil {
		log.Printf("删除漫画失败 (id=%s): %v", idStr, err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "删除失败"})
		return
	}
	if res.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "记录不存在"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"message": "删除成功"})
}

// SyncStatus 轻量接口：返回服务器时间与用户漫画总数。
func (h *ComicHandler) SyncStatus(c *gin.Context) {
	userID := c.GetUint("user_id")

	count, err := query.Comic.Where(query.Comic.UserID.Eq(userID)).Count()
	if err != nil {
		log.Printf("统计漫画数失败 (user_id=%d): %v", userID, err)
		count = 0
	}

	c.JSON(http.StatusOK, models.SyncStatus{
		ServerTime: time.Now().UnixMilli(),
		ComicCount: int(count),
	})
}

// isNotFound：判断查询是否为「无记录」（gorm.ErrRecordNotFound），
// 用 errors.Is 兼容被包装的错误；全项目统一经由此函数判断。
func isNotFound(err error) bool {
	return err != nil && errors.Is(err, gorm.ErrRecordNotFound)
}
