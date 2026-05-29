package utils

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
)

const saltLength = 32

// GenerateSalt 生成一个随机盐值（十六进制字符串）
func GenerateSalt() (string, error) {
	salt := make([]byte, saltLength)
	if _, err := rand.Read(salt); err != nil {
		return "", fmt.Errorf("failed to generate salt: %w", err)
	}
	return hex.EncodeToString(salt), nil
}

// HashPassword 使用盐+SHA256 对密码进行哈希
func HashPassword(password, salt string) string {
	hash := sha256.Sum256([]byte(salt + password))
	return hex.EncodeToString(hash[:])
}

// VerifyPassword 验证密码是否匹配
func VerifyPassword(password, salt, hash string) bool {
	return HashPassword(password, salt) == hash
}
