package config

import (
	"os"
)

type Config struct {
	DBPath     string
	JWTSecret  string
	ServerPort string
}

func Load() *Config {
	return &Config{
		DBPath:     getEnv("DB_PATH", "./data/cimoc.db"),
		JWTSecret:  getEnv("JWT_SECRET", "xcimoc-data-server-secret-key-2024"),
		ServerPort: getEnv("SERVER_PORT", "8080"),
	}
}

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}
