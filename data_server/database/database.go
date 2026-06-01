package database

import (
	"crypto/rand"
	"log"
	"math/big"
	"os"
	"path/filepath"
	"strings"

	"xcimoc-data-server/config"
	"xcimoc-data-server/models"
	"xcimoc-data-server/utils"

	"github.com/glebarez/sqlite"
	"gorm.io/driver/mysql"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

var DB *gorm.DB

const DefaultAdminUsername = "admin"

func Init(cfg *config.Config) {
	var dialector gorm.Dialector

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
		dialector = mysql.Open(cfg.DBDSN)

	case "postgres", "pgsql":
		if cfg.DBDSN == "" {
			log.Fatalf("使用 PostgreSQL 时必须设置 --dbdsn 或 DB_DSN，例如: host=localhost user=user password=pass dbname=cimoc port=5432 sslmode=disable")
		}
		dialector = postgres.Open(cfg.DBDSN)

	default: // sqlite
		dbDir := filepath.Dir(cfg.DBPath)
		if err := os.MkdirAll(dbDir, 0755); err != nil {
			log.Fatalf("failed to create database directory %s: %v", dbDir, err)
		}
		dialector = sqlite.Open(cfg.DBPath)
	}

	var err error
	DB, err = gorm.Open(dialector, &gorm.Config{
		Logger: logger.Default.LogMode(logger.Warn),
	})
	if err != nil {
		log.Fatalf("failed to connect database: %v", err)
	}

	// MySQL 需要在迁移前关闭外键检查（TagRef 使用多表关联）
	if cfg.DBType == "mysql" {
		DB.Exec("SET FOREIGN_KEY_CHECKS = 0")
	}

	err = DB.AutoMigrate(
		&models.User{},
		&models.Comic{},
		&models.Setting{},
		&models.Tag{},
		&models.TagRef{},
	)
	if err != nil {
		log.Fatalf("failed to migrate database: %v", err)
	}

	if cfg.DBType == "mysql" {
		DB.Exec("SET FOREIGN_KEY_CHECKS = 1")
	}

	// 首次启动自动创建默认管理员账户
	ensureAdminExists()

	log.Printf("database initialized successfully (type: %s)", cfg.DBType)
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
	var count int64
	DB.Model(&models.User{}).Where("is_admin = ?", true).Count(&count)
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

	admin := models.User{
		Username: DefaultAdminUsername,
		Password: utils.HashPassword(adminPassword, salt),
		Salt:     salt,
		IsAdmin:  true,
	}

	if result := DB.Create(&admin); result.Error != nil {
		log.Fatalf("failed to create default admin: %v", result.Error)
	}

	log.Printf("========================================")
	log.Printf("  首次启动，默认管理员已创建")
	log.Printf("  用户名: %s", DefaultAdminUsername)
	log.Printf("  密码: %s", adminPassword)
	log.Printf("  请立即登录管理后台修改密码！")
	log.Printf("  后台地址: http://<server>:<port>/admin")
	log.Printf("========================================")
}
