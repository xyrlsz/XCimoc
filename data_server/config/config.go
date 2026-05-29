package config

import (
	"crypto/rand"
	"encoding/hex"
	"flag"
	"fmt"
	"os"

	"gopkg.in/yaml.v3"
)

// ConfigFile 对应 YAML 配置文件结构
type ConfigFile struct {
	Server struct {
		Port string `yaml:"port"`
	} `yaml:"server"`
	Database struct {
		Type string `yaml:"type"` // sqlite / mysql / postgres
		Path string `yaml:"path"` // SQLite 文件路径
		DSN  string `yaml:"dsn"`  // MySQL/PostgreSQL 连接串
	} `yaml:"database"`
	JWT struct {
		Secret string `yaml:"secret"`
	} `yaml:"jwt"`
}

type Config struct {
	DBType     string
	DBPath     string
	DBDSN      string
	JWTSecret  string
	ServerPort string
}

func Load() *Config {
	// 先定义 CLI 参数
	configPath := flag.String("config", "", "配置文件路径 (YAML 格式)")
	dbtype := flag.String("dbtype", "", "数据库类型: sqlite / mysql / postgres")
	data := flag.String("data", "", "SQLite 数据库路径")
	dbdsn := flag.String("dbdsn", "", "MySQL/PostgreSQL 连接串")
	jwt := flag.String("jwtsecret", "", "JWT 签名密钥")
	port := flag.String("port", "", "监听端口")
	flag.Parse()

	// ====== 1. 默认值 ======
	cfg := &Config{
		DBType:     "sqlite",
		DBPath:     "./data/cimoc.db",
		DBDSN:      "",
		JWTSecret:  "",
		ServerPort: "8080",
	}

	// ====== 2. 从 YAML 配置文件加载 ======
	if *configPath != "" {
		cfg.applyConfigFile(*configPath)
	}

	// ====== 3. 环境变量覆盖 ======
	if v := os.Getenv("DB_TYPE"); v != "" {
		cfg.DBType = v
	}
	if v := os.Getenv("DB_PATH"); v != "" {
		cfg.DBPath = v
	}
	if v := os.Getenv("DB_DSN"); v != "" {
		cfg.DBDSN = v
	}
	if v := os.Getenv("JWT_SECRET"); v != "" {
		cfg.JWTSecret = v
	}
	if v := os.Getenv("SERVER_PORT"); v != "" {
		cfg.ServerPort = v
	}

	// ====== 4. CLI 参数覆盖（优先级最高） ======
	if *dbtype != "" {
		cfg.DBType = *dbtype
	}
	if *data != "" {
		cfg.DBPath = *data
	}
	if *dbdsn != "" {
		cfg.DBDSN = *dbdsn
	}
	if *jwt != "" {
		cfg.JWTSecret = *jwt
	}
	if *port != "" {
		cfg.ServerPort = *port
	}

	// ====== 最后：未设置 JWT_SECRET 时自动生成 ======
	if cfg.JWTSecret == "" {
		cfg.JWTSecret = generateRandomKey(32)
		fmt.Printf("[提示] 未设置 JWT_SECRET，已自动生成: %s\n", cfg.JWTSecret)
		fmt.Println("[提示] 建议通过配置文件、环境变量或 --jwtsecret 固定密钥，以免重启后旧 token 失效")
	}

	return cfg
}

// applyConfigFile 从 YAML 文件加载配置
func (cfg *Config) applyConfigFile(path string) {
	data, err := os.ReadFile(path)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[警告] 读取配置文件失败 %s: %v\n", path, err)
		return
	}

	var cf ConfigFile
	if err := yaml.Unmarshal(data, &cf); err != nil {
		fmt.Fprintf(os.Stderr, "[警告] 解析配置文件失败 %s: %v\n", path, err)
		return
	}

	if cf.Server.Port != "" {
		cfg.ServerPort = cf.Server.Port
	}
	if cf.Database.Type != "" {
		cfg.DBType = cf.Database.Type
	}
	if cf.Database.Path != "" {
		cfg.DBPath = cf.Database.Path
	}
	if cf.Database.DSN != "" {
		cfg.DBDSN = cf.Database.DSN
	}
	if cf.JWT.Secret != "" {
		cfg.JWTSecret = cf.JWT.Secret
	}

	fmt.Printf("[配置] 已加载配置文件: %s\n", path)
}

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}

// generateRandomKey 生成随机十六进制密钥
func generateRandomKey(length int) string {
	b := make([]byte, length)
	if _, err := rand.Read(b); err != nil {
		return fmt.Sprintf("fallback-key-%d-%d", os.Getpid(), os.Getpid()*length)
	}
	return hex.EncodeToString(b)
}
