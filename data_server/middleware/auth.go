package middleware

import (
	"net/http"
	"strings"
	"time"

	"xcimoc-data-server/config"
	"xcimoc-data-server/database"
	"xcimoc-data-server/models"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

type Claims struct {
	UserID       uint   `json:"user_id"`
	Username     string `json:"username"`
	IsAdmin      bool   `json:"is_admin"`
	TokenVersion int    `json:"token_version"`
	jwt.RegisteredClaims
}

func GenerateToken(user *models.User, cfg *config.Config) (string, error) {
	claims := Claims{
		UserID:       user.ID,
		Username:     user.Username,
		IsAdmin:      user.IsAdmin,
		TokenVersion: user.TokenVersion,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(7 * 24 * time.Hour)), // 7 天
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString([]byte(cfg.JWTSecret))
}

// parseToken 解析 token 并校验 token_version 与数据库一致
func parseToken(tokenStr string, cfg *config.Config, allowExpired bool) (*Claims, error) {
	claims := &Claims{}
	var opts []jwt.ParserOption
	if allowExpired {
		opts = append(opts, jwt.WithLeeway(7*24*time.Hour))
	}
	token, err := jwt.ParseWithClaims(tokenStr, claims, func(token *jwt.Token) (interface{}, error) {
		return []byte(cfg.JWTSecret), nil
	}, opts...)
	if err != nil || !token.Valid {
		return nil, err
	}

	// 校验用户是否存在以及 token_version 是否匹配
	var user models.User
	if result := database.DB.First(&user, claims.UserID); result.Error != nil {
		return nil, jwt.ErrSignatureInvalid // 用户不存在
	}
	if user.TokenVersion != claims.TokenVersion {
		return nil, jwt.ErrSignatureInvalid // 密码已修改，旧 token 失效
	}

	return claims, nil
}

// AuthRequired 普通用户认证
func AuthRequired(cfg *config.Config) gin.HandlerFunc {
	return authMiddleware(cfg, false, false)
}

// AdminRequired 管理员认证
func AdminRequired(cfg *config.Config) gin.HandlerFunc {
	return authMiddleware(cfg, true, false)
}

// AuthRefresh 用于 refresh 端点，允许 token 已过期但在 7 天内仍可刷新
func AuthRefresh(cfg *config.Config) gin.HandlerFunc {
	return authMiddleware(cfg, false, true)
}

func authMiddleware(cfg *config.Config, requireAdmin, allowExpired bool) gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "缺少认证令牌"})
			c.Abort()
			return
		}

		parts := strings.SplitN(authHeader, " ", 2)
		if len(parts) != 2 || strings.ToLower(parts[0]) != "bearer" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "认证格式错误，使用: Bearer <token>"})
			c.Abort()
			return
		}

		claims, err := parseToken(parts[1], cfg, allowExpired)
		if err != nil {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "认证令牌无效或已过期"})
			c.Abort()
			return
		}

		if requireAdmin && !claims.IsAdmin {
			c.JSON(http.StatusForbidden, gin.H{"error": "需要管理员权限"})
			c.Abort()
			return
		}

		c.Set("user_id", claims.UserID)
		c.Set("username", claims.Username)
		c.Set("is_admin", claims.IsAdmin)
		c.Next()
	}
}
