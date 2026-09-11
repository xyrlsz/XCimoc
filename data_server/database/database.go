package database

import (
	"crypto/rand"
	"fmt"
	"log"
	"math/big"
	"os"
	"path/filepath"
	"strings"
	"time"

	"xcimoc-data-server/config"
	"xcimoc-data-server/models"
	"xcimoc-data-server/query"
	"xcimoc-data-server/utils"

	"gorm.io/driver/mysql"
	"gorm.io/driver/postgres"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
	"gorm.io/gorm/logger"
)

var DB *gorm.DB

const DefaultAdminUsername = "admin"

func Init(cfg *config.Config) {
	switch cfg.DBType {
	case "mysql":
		if cfg.DBDSN == "" {
			log.Fatalf("使用 MySQL 时必须设置 --dbdsn 或 DB_DSN，例如: user:pass@tcp(127.0.0.1:3306)/cimoc?charset=utf8mb4&parseTime=True")
		}
		// 自动补充 parseTime=True（GORM 读写 time.Time 必需）
		if !strings.Contains(cfg.DBDSN, "parseTime=") {
			if strings.Contains(cfg.DBDSN, "?") {
				cfg.DBDSN += "&parseTime=True"
			} else {
				cfg.DBDSN += "?parseTime=True"
			}
		}
		openGorm(cfg, mysql.Open(cfg.DBDSN))

	case "postgres", "pgsql":
		if cfg.DBDSN == "" {
			log.Fatalf("使用 PostgreSQL 时必须设置 --dbdsn 或 DB_DSN，例如: host=localhost user=user password=pass dbname=cimoc port=5432 sslmode=disable")
		}
		openGorm(cfg, postgres.Open(cfg.DBDSN))

	default: // sqlite
		dbDir := filepath.Dir(cfg.DBPath)
		if err := os.MkdirAll(dbDir, 0755); err != nil {
			log.Fatalf("failed to create database directory %s: %v", dbDir, err)
		}
		// mattn/go-sqlite3 DSN 参数（数据量大时性能提升最明显）：
		//   _journal_mode=WAL   —— 读写不再互斥，读请求不会被写事务阻塞；WAL 模式持久化到
		//                          数据库文件头，重启后依然生效
		//   _busy_timeout=5000  —— 写锁竞争时最多等待 5 秒，而不是立刻报 "database is locked"
		//   _synchronous=NORMAL —— WAL 模式下把同步级别降为 NORMAL，大幅减少 fsync，
		//                          仍保持持久安全（WAL 自身 crash-safe 兜底）
		//   _cache_size=-20000  —— 页缓存 20MB（负数表示 KB 单位），减少磁盘读
		dsn := cfg.DBPath + "?_journal_mode=WAL&_busy_timeout=5000&_synchronous=NORMAL&_cache_size=-20000"
		openGorm(cfg, sqlite.Open(dsn))
	}
}

// openGorm 封装 GORM 初始化 + 迁移 + 连接池 + 默认管理员
func openGorm(cfg *config.Config, dialector gorm.Dialector) {
	var err error
	DB, err = gorm.Open(dialector, &gorm.Config{
		// 迁移时不创建外键约束：本项目引用完整性由业务代码（事务 + 墓碑/幂等逻辑）维护，
		// 且连接池下「迁移前执行 SET FOREIGN_KEY_CHECKS=0」只作用于单条连接、不可靠
		// （AutoMigrate 可能使用池中其它连接）——直接不建外键是跨库（SQLite/MySQL/PG）
		// 最稳妥的做法。
		DisableForeignKeyConstraintWhenMigrating: true,
		// gorm Warn 级别默认会把“查询无结果”(record not found) 也打印成日志。
		// 全量同步时每个漫画都会查一次墓碑表，绝大多数没有墓碑，
		// 导致每次同步刷大量 record not found（正常现象、非错误）。
		// IgnoreRecordNotFoundError 只在日志层面忽略该场景，
		// 不影响任何查询返回的 error 值或业务错误处理。
		Logger: logger.New(log.New(os.Stdout, "\r\n", log.LstdFlags), logger.Config{
			SlowThreshold:             time.Second,
			LogLevel:                  logger.Warn,
			IgnoreRecordNotFoundError: true,
			Colorful:                  false,
		}),
	})
	if err != nil {
		log.Fatalf("failed to connect database: %v", err)
	}

	// 配置数据库连接池
	sqlDB, err := DB.DB()
	if err == nil {
		// SQLite 只支持单写入者，限制连接数避免 "database is locked"
		if cfg.DBType == "sqlite" {
			sqlDB.SetMaxOpenConns(1)
			sqlDB.SetMaxIdleConns(1)
		} else {
			sqlDB.SetMaxOpenConns(25)
			sqlDB.SetMaxIdleConns(10)
		}
		sqlDB.SetConnMaxLifetime(5 * time.Minute)
	}

	err = DB.AutoMigrate(
		&models.User{},
		&models.Comic{},
		&models.ComicDelete{},
		&models.SyncEvent{},
		&models.Setting{},
		&models.Tag{},
		&models.TagRef{},
	)
	if err != nil {
		log.Fatalf("failed to migrate database: %v", err)
	}

	// SyncEvent 幂等唯一索引 (user_id, client_id, type, payload_hash) 的建列/回填/去重/建索引。
	// payload_hash 字段声明为 -:migration，AutoMigrate 不管理它，全部在这里手工处理
	// （原因见 models.SyncEvent 注释：gorm SQLite 的列变更重建会清空该列数据）。
	ensureSyncEventDedupe()

	// 将已初始化的 *gorm.DB 绑定到 gorm.io/gen 生成的 query 单例。
	// 之后所有 handler/middleware/maintenance 都可以用 query.Q（即 query.User /
	// query.Comic / query.SyncEvent 等包级快捷）来进行类型安全的查询与写入，
	// 不必再手写 database.DB.Where("user_id = ? AND id > ?", ...) 这种字符串 Where，
	// 从编译期避免字段名拼写错误、重构不一致与潜在的拼接式 SQL 注入问题。
	// 原 database.DB 仍保留可用，作为复杂原生 SQL / 事务兜底。
	query.SetDefault(DB)

	// 首次启动自动创建默认管理员账户
	ensureAdminExists()

	log.Printf("database initialized successfully (type: %s)", cfg.DBType)
}

// ensureSyncEventDedupe 手工管理 SyncEvent 的幂等去重列与唯一索引。
// payload_hash 声明为 `-:migration`（gorm 迁移完全跳过），原因是 gorm 的 SQLite
// MigrateColumn 发现列定义差异时会触发 recreateTable 表重建，而重建的 INSERT
// 不拷贝「被修改的列」（数据被置回默认值）——实测会把已回填的哈希全部清空，
// 随后唯一索引创建失败（UNIQUE constraint failed）。
//
// 流程（可重复执行，幂等）：
//  1. 补列：旧库/新库都确保 payload_hash 存在。注意不能调用 migrator.AddColumn ——
//     gorm 核心及 mysql/pg/sqlite 三个驱动的 AddColumn 对 `-:migration` 字段一律
//     静默跳过（直接 return nil），列永远不会被创建；这里按与 gorm AddColumn
//     完全相同的 DDL（ALTER TABLE ... ADD COLUMN ... <类型> NOT NULL DEFAULT ”）手工补列；
//  2. 快速路径：唯一索引已存在且无空哈希 → 直接返回；
//  3. 回填：payload_hash = SHA-256(payload)（与服务端写入端同一算法 utils.SHA256Hex）；
//  4. 去重：按 (user_id, client_id, type, payload_hash) 保留最小 id，删除历史重复事件；
//  5. 建唯一索引 idx_event_dedupe（跨库：先查存在性再建，MySQL 无 CREATE INDEX IF NOT EXISTS）。
func ensureSyncEventDedupe() {
	migrator := DB.Migrator()
	if !migrator.HasTable(&models.SyncEvent{}) {
		return
	}
	if !migrator.HasColumn(&models.SyncEvent{}, "PayloadHash") {
		stmt := &gorm.Statement{DB: DB}
		if err := stmt.Parse(&models.SyncEvent{}); err != nil {
			log.Printf("SyncEvent 去重列初始化：解析模型失败: %v", err)
			return
		}
		field := stmt.Schema.LookUpField("PayloadHash")
		if field == nil {
			log.Printf("SyncEvent 去重列初始化：模型缺少 PayloadHash 字段")
			return
		}
		if err := DB.Exec("ALTER TABLE ? ADD ? ?",
			clause.Table{Name: stmt.Schema.Table},
			clause.Column{Name: field.DBName},
			migrator.FullDataTypeOf(field),
		).Error; err != nil {
			log.Printf("SyncEvent 去重列初始化：补列 payload_hash 失败: %v", err)
			return
		}
	}

	se := query.Use(DB).SyncEvent
	if migrator.HasIndex(&models.SyncEvent{}, "idx_event_dedupe") {
		// 快速路径：索引已在且无空哈希（唯一索引存在时空哈希不可能重复，空哈希即说明有遗漏）
		empty, err := se.Where(se.PayloadHash.Eq("")).Count()
		if err == nil && empty == 0 {
			return
		}
	}

	rows, err := se.Select(se.ID, se.UserID, se.ClientID, se.Type, se.Payload, se.PayloadHash).
		Order(se.ID).Find()
	if err != nil {
		log.Printf("SyncEvent 去重列初始化：读取事件失败: %v", err)
		return
	}

	type hashFix struct {
		id   uint
		hash string
	}
	seen := make(map[string]struct{}, len(rows))
	var fixes []hashFix
	var dupIDs []uint
	for _, r := range rows {
		if r == nil {
			continue
		}
		hash := utils.SHA256Hex(r.Payload)
		if r.PayloadHash != hash {
			fixes = append(fixes, hashFix{id: r.ID, hash: hash})
		}
		key := fmt.Sprintf("%d|%s|%s|%s", r.UserID, r.ClientID, r.Type, hash)
		if _, exists := seen[key]; exists {
			dupIDs = append(dupIDs, r.ID)
			continue
		}
		seen[key] = struct{}{}
	}

	if len(fixes) > 0 || len(dupIDs) > 0 {
		err = DB.Transaction(func(tx *gorm.DB) error {
			qt := query.Use(tx).SyncEvent
			for _, f := range fixes {
				if _, err := qt.Where(qt.ID.Eq(f.id)).Update(qt.PayloadHash, f.hash); err != nil {
					return err
				}
			}
			for i := 0; i < len(dupIDs); i += 500 {
				end := i + 500
				if end > len(dupIDs) {
					end = len(dupIDs)
				}
				if _, err := qt.Where(qt.ID.In(dupIDs[i:end]...)).Delete(); err != nil {
					return err
				}
			}
			return nil
		})
		if err != nil {
			log.Printf("SyncEvent 去重列初始化：回填/去重失败: %v", err)
			return
		}
		if len(fixes) > 0 || len(dupIDs) > 0 {
			log.Printf("SyncEvent 去重列初始化完成：回填 payload_hash %d 条，删除重复事件 %d 条", len(fixes), len(dupIDs))
		}
	}

	if !migrator.HasIndex(&models.SyncEvent{}, "idx_event_dedupe") {
		if err := DB.Exec("CREATE UNIQUE INDEX idx_event_dedupe ON sync_events (user_id, client_id, type, payload_hash)").Error; err != nil {
			log.Printf("SyncEvent 去重列初始化：创建唯一索引失败: %v", err)
		} else {
			log.Printf("SyncEvent 去重列初始化完成：已创建唯一索引 idx_event_dedupe")
		}
	}
}

// generateRandomPassword 生成 12 位随机密码（字母+数字）
func generateRandomPassword() (string, error) {
	const charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	password := make([]byte, 12)
	for i := range password {
		idx, err := rand.Int(rand.Reader, big.NewInt(int64(len(charset))))
		if err != nil {
			return "", err
		}
		password[i] = charset[idx.Int64()]
	}
	return string(password), nil
}

func ensureAdminExists() {
	// 用生成的 query.User 统计管理员数，避免裸字符串 "is_admin = ?"。
	count, err := query.User.Where(query.User.IsAdmin.Eq(true)).Count()
	if err != nil {
		log.Fatalf("failed to count admin users: %v", err)
	}
	if count > 0 {
		return // 管理员已存在
	}

	salt, err := utils.GenerateSalt()
	if err != nil {
		log.Fatalf("failed to generate salt for admin: %v", err)
	}

	adminPassword, err := generateRandomPassword()
	if err != nil {
		log.Fatalf("failed to generate admin password: %v", err)
	}

	admin := &models.User{
		Username: DefaultAdminUsername,
		Password: utils.HashPassword(adminPassword, salt),
		Salt:     salt,
		IsAdmin:  true,
	}

	// 使用生成 query 的 Create；如果后续 User 表字段结构变了，编译期会直接拦截。
	if err := query.User.Create(admin); err != nil {
		log.Fatalf("failed to create default admin: %v", err)
	}

	log.Printf("========================================")
	log.Printf("  首次启动，默认管理员已创建")
	log.Printf("  用户名: %s", DefaultAdminUsername)
	log.Printf("  密码: %s", adminPassword)
	log.Printf("  请立即登录管理后台修改密码！")
	log.Printf("  后台地址: http://<server>:<port>/admin")
	log.Printf("========================================")
}
