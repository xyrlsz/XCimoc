package config

import (
	"crypto/rand"
	"encoding/hex"
	"flag"
	"fmt"
	"os"
)

type Config struct {
	DBPath     string
	JWTSecret  string
	ServerPort string
}

func Load() *Config {
	// 先解析命令行参数（支持 --data= / --jwtsecret= / --port=）
	data := flag.String("data", "", "数据库路径 (默认 ./data/cimoc.db，也可用环境变量 DB_PATH)")
	jwt := flag.String("jwtsecret", "", "JWT 签名密钥 (默认随机生成，也可用环境变量 JWT_SECRET)")
	port := flag.String("port", "", "监听端口 (默认 8080，也可用环境变量 SERVER_PORT)")
	flag.Parse()

	// 命令行参数 > 环境变量 > 默认值
	dbPath := "./data/cimoc.db"
	jwtSecret := ""
	serverPort := "8080"

	if *data != "" {
		dbPath = *data
	} else if v := os.Getenv("DB_PATH"); v != "" {
		dbPath = v
	}

	if *jwt != "" {
		jwtSecret = *jwt
	} else if v := os.Getenv("JWT_SECRET"); v != "" {
		jwtSecret = v
	}

	if *port != "" {
		serverPort = *port
	} else if v := os.Getenv("SERVER_PORT"); v != "" {
		serverPort = v
	}

	// 如果没有设置 JWT_SECRET，自动生成一个并提示
	if jwtSecret == "" {
		jwtSecret = generateRandomKey(32)
		fmt.Printf("[提示] 未设置 JWT_SECRET，已自动生成: %s\n", jwtSecret)
		fmt.Println("[提示] 建议通过环境变量或 --jwtsecret 固定密钥，以免重启后旧 token 失效")
	}

	return &Config{
		DBPath:     dbPath,
		JWTSecret:  jwtSecret,
		ServerPort: serverPort,
	}
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
		// 降级：使用时间+pid 组合
		return fmt.Sprintf("fallback-key-%d-%d", os.Getpid(), os.Getpid()*length)
	}
	return hex.EncodeToString(b)
}
