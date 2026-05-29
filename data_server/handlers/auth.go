package handlers

import (
	"net/http"

	"xcimoc-data-server/config"
	"xcimoc-data-server/database"
	"xcimoc-data-server/middleware"
	"xcimoc-data-server/models"
	"xcimoc-data-server/utils"

	"github.com/gin-gonic/gin"
)

type AuthHandler struct {
	Config *config.Config
}

func NewAuthHandler(cfg *config.Config) *AuthHandler {
	return &AuthHandler{Config: cfg}
}

// Login authenticates a user and returns a JWT token.
func (h *AuthHandler) Login(c *gin.Context) {
	var req models.LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "请求参数无效"})
		return
	}

	var user models.User
	if result := database.DB.Where("username = ?", req.Username).Limit(1).Find(&user); result.RowsAffected == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "用户名或密码错误"})
		return
	}

	if !utils.VerifyPassword(req.Password, user.Salt, user.Password) {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "用户名或密码错误"})
		return
	}

	token, err := middleware.GenerateToken(&user, h.Config)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "生成令牌失败"})
		return
	}

	c.JSON(http.StatusOK, models.LoginResponse{
		Token: token,
		User:  user,
	})
}

// RefreshToken 刷新 token（延长有效期）
func (h *AuthHandler) RefreshToken(c *gin.Context) {
	userID := c.GetUint("user_id")

	var user models.User
	if result := database.DB.First(&user, userID); result.Error != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "用户不存在"})
		return
	}

	token, err := middleware.GenerateToken(&user, h.Config)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "生成令牌失败"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"token": token})
}

// ==================== 管理员用户管理 ====================

// ListUsers 获取所有用户列表（管理员专用）
func (h *AuthHandler) ListUsers(c *gin.Context) {
	var users []models.User
	database.DB.Select("id, username, is_admin, created_at, updated_at").Find(&users)
	c.JSON(http.StatusOK, gin.H{"users": users})
}

// CreateUser 管理员创建普通用户
func (h *AuthHandler) CreateUser(c *gin.Context) {
	var req models.CreateUserRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "请求参数无效: " + err.Error()})
		return
	}

	var existing models.User
	if result := database.DB.Where("username = ?", req.Username).Limit(1).Find(&existing); result.RowsAffected > 0 {
		c.JSON(http.StatusConflict, gin.H{"error": "用户名已存在"})
		return
	}

	salt, err := utils.GenerateSalt()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "密码加密失败"})
		return
	}

	user := models.User{
		Username: req.Username,
		Password: utils.HashPassword(req.Password, salt),
		Salt:     salt,
		IsAdmin:  false,
	}

	if result := database.DB.Create(&user); result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "创建用户失败"})
		return
	}

	c.JSON(http.StatusCreated, gin.H{"message": "用户创建成功", "user": user})
}

// ChangePassword 管理员修改任意用户密码
func (h *AuthHandler) ChangePassword(c *gin.Context) {
	var req models.ChangePasswordRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "请求参数无效: " + err.Error()})
		return
	}

	var user models.User
	if result := database.DB.Where("username = ?", req.Username).Limit(1).Find(&user); result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "用户不存在"})
		return
	}

	salt, err := utils.GenerateSalt()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "密码加密失败"})
		return
	}

	user.Password = utils.HashPassword(req.NewPassword, salt)
	user.Salt = salt
	database.DB.Save(&user)

	c.JSON(http.StatusOK, gin.H{"message": "密码修改成功"})
}
