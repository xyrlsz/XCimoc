package middleware

import (
	"net/http"
	"strings"
	"time"

	"xcimoc-data-server/config"
	"xcimoc-data-server/models"
	"xcimoc-data-server/query"

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
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(7 * 24 * time.Hour)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString([]byte(cfg.JWTSecret))
}

// parseToken 解析 token；用户存在性与 token_version 校验改用生成 query 的类型安全查询，
// 避免手写 `WHERE id = ?`。
func parseToken(tokenStr string, cfg *config.Config, leeway time.Duration) (*Claims, error) {
	claims := &Claims{}
	opts := []jwt.ParserOption{
		jwt.WithLeeway(leeway),
		// 仅接受 HS256：防御签名算法混淆类攻击（如伪造 alg=none / 改用其它算法
		// 但复用同一密钥解析），生成端 GenerateToken 固定使用 HS256。
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
	}
	token, err := jwt.ParseWithClaims(tokenStr, claims, func(token *jwt.Token) (interface{}, error) {
		return []byte(cfg.JWTSecret), nil
	}, opts...)
	if err != nil || !token.Valid {
		return nil, err
	}

	user, findErr := query.User.Where(query.User.ID.Eq(claims.UserID)).Take()
	if findErr != nil || user == nil {
		return nil, jwt.ErrSignatureInvalid
	}
	if user.TokenVersion != claims.TokenVersion {
		return nil, jwt.ErrSignatureInvalid
	}

	return claims, nil
}

func AuthRequired(cfg *config.Config) gin.HandlerFunc {
	return authMiddleware(cfg, false, 30*time.Second)
}

func AdminRequired(cfg *config.Config) gin.HandlerFunc {
	return authMiddleware(cfg, true, 30*time.Second)
}

func AuthRefresh(cfg *config.Config) gin.HandlerFunc {
	return authMiddleware(cfg, false, 7*24*time.Hour)
}

func authMiddleware(cfg *config.Config, requireAdmin bool, leeway time.Duration) gin.HandlerFunc {
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

		claims, err := parseToken(parts[1], cfg, leeway)
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
