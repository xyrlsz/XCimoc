package main

import (
	"embed"
	"fmt"
	"log"
	"net/http"
	"os"
	"strings"

	"xcimoc-data-server/config"
	"xcimoc-data-server/database"
	"xcimoc-data-server/handlers"
	"xcimoc-data-server/middleware"
	"xcimoc-data-server/models"
	"xcimoc-data-server/utils"

	"github.com/gin-gonic/gin"
)

//go:embed admin/index.html
var adminHTML embed.FS

func main() {
	// 命令行修改管理员密码
	if len(os.Args) >= 2 && strings.ToLower(os.Args[1]) == "set" {
		if len(os.Args) < 4 || strings.ToLower(os.Args[2]) != "admin" {
			fmt.Println("用法: xcimoc-data-server.exe set admin \"新密码\"")
			fmt.Println("示例: xcimoc-data-server.exe set admin myNewPwd123")
			os.Exit(1)
		}

		newPassword := os.Args[3]
		if len(newPassword) < 6 {
			log.Fatalf("密码长度至少 6 位")
		}

		cfg := config.Load()
		database.Init(cfg)

		salt, err := utils.GenerateSalt()
		if err != nil {
			log.Fatalf("生成盐值失败: %v", err)
		}

		result := database.DB.Model(&models.User{}).
			Where("is_admin = ?", true).
			Updates(map[string]interface{}{
				"password": utils.HashPassword(newPassword, salt),
				"salt":     salt,
			})
		if result.RowsAffected == 0 {
			log.Fatalf("未找到管理员账户，请先启动服务器以初始化数据库")
		}

		fmt.Println("管理员密码已更新")
		os.Exit(0)
	}

	cfg := config.Load()

	// Initialize database
	database.Init(cfg)

	// Initialize handlers
	authHandler := handlers.NewAuthHandler(cfg)
	comicHandler := handlers.NewComicHandler()
	settingHandler := handlers.NewSettingHandler()
	tagHandler := handlers.NewTagHandler()

	// Setup router
	r := gin.Default()

	// CORS middleware
	r.Use(func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Authorization")
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	})

	// Health check
	r.GET("/api/health", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "ok", "service": "Cimoc Data Sync Server"})
	})

	// Auth routes (no token required)
	auth := r.Group("/api/auth")
	{
		auth.POST("/login", authHandler.Login)
	}

	// Token refresh (接受 7 天内过期的 token)
	refresh := r.Group("/api/auth")
	refresh.Use(middleware.AuthRefresh(cfg))
	{
		refresh.POST("/refresh", authHandler.RefreshToken)
	}

	// Protected routes (token required)
	api := r.Group("/api")
	api.Use(middleware.AuthRequired(cfg))
	{
		// Comics (history + favorites)
		api.GET("/comics", comicHandler.List)
		api.POST("/comics/sync", comicHandler.Sync)
		api.DELETE("/comics/:id", comicHandler.Delete)

		// Settings
		api.GET("/settings", settingHandler.List)
		api.POST("/settings/sync", settingHandler.Sync)

		// Tags
		api.GET("/tags", tagHandler.List)
		api.POST("/tags/sync", tagHandler.Sync)
	}

	// Admin routes (admin token required)
	admin := r.Group("/api/admin")
	admin.Use(middleware.AdminRequired(cfg))
	{
		admin.GET("/users", authHandler.ListUsers)
		admin.POST("/users", authHandler.CreateUser)
		admin.POST("/password", authHandler.ChangePassword)
	}

	// Serve Vue admin frontend (embedded)
	r.GET("/admin", serveAdmin)
	r.GET("/admin/*filepath", func(c *gin.Context) {
		// 所有 /admin/* 子路径都返回同一份 HTML（SPA fallback）
		serveAdmin(c)
	})

	// 根路径和 /login 重定向到管理后台
	r.GET("/", func(c *gin.Context) {
		c.Redirect(http.StatusFound, "/admin")
	})
	r.GET("/login", func(c *gin.Context) {
		c.Redirect(http.StatusFound, "/admin")
	})

	r.GET("/favicon.ico", func(c *gin.Context) {
		c.Status(http.StatusNoContent)
	})

	// Start server
	addr := ":" + cfg.ServerPort
	log.Printf("Cimoc Data Sync Server starting on %s", addr)
	log.Printf("Admin panel: http://localhost%s/admin", addr)
	if err := r.Run(addr); err != nil {
		log.Fatalf("failed to start server: %v", err)
	}
}

func serveAdmin(c *gin.Context) {
	data, err := adminHTML.ReadFile("admin/index.html")
	if err != nil {
		c.String(http.StatusNotFound, "admin page not found")
		return
	}
	c.Data(http.StatusOK, "text/html; charset=utf-8", data)
}
