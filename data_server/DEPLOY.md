# Cimoc 数据同步服务器部署文档

## 概述

Cimoc 数据同步服务器是 XCimoc 漫画阅读器的配套服务端程序，提供漫画收藏、阅读记录、应用设置的跨设备云同步功能。

- **语言**: Go 1.25+
- **框架**: Gin + GORM + JWT
- **数据库**: SQLite（默认）/ MySQL / PostgreSQL
- **内置管理后台**: Vue 3 SPA（嵌入二进制中，无需单独部署）

---

## 快速开始（5 分钟部署）

### 1. 下载或编译

#### 方式 A：直接下载编译好的二进制

从项目的 Release 页面下载 `xcimoc-data-server`（或 `xcimoc-data-server.exe`）。

#### 方式 B：自行编译

```bash
# 进入服务端目录
cd data_server

# 设置国内代理（可选，中国大陆推荐）
export GOPROXY=https://goproxy.cn,direct
# 或 Windows PowerShell:
# $env:GOPROXY="https://goproxy.cn,direct"

# 编译
go build -o xcimoc-data-server.exe .
```

### 2. 运行

```bash
# Windows
xcimoc-data-server.exe

# Linux / macOS
./xcimoc-data-server
```

首次启动会自动创建 `./data/` 目录和 SQLite 数据库，并 **随机生成管理员密码**（打印在控制台）。

**输出示例：**

```
========================================
  首次启动，默认管理员已创建
  用户名: admin
  密码: aB3xK9mP2rFz
  请立即登录管理后台修改密码！
  后台地址: http://localhost:8080/admin
========================================
database initialized successfully (type: sqlite)
Cimoc Data Sync Server starting on :8080
Admin panel: http://localhost:8080/admin
```

> ⚠️ **务必保存控制台打印的管理员密码**。如果丢失，可通过命令行重置（见下文）。

### 3. 打开管理后台

在浏览器中打开 `http://<服务器IP>:8080/admin`，使用 `admin` 和刚才的随机密码登录。

进入后台后可：
- 修改管理员密码
- 创建普通用户（供 Android 客户端登录使用）
- 查看用户列表

---

## 配置方式

服务器支持三种配置方式，**优先级：CLI 参数 > 环境变量 > 配置文件 > 默认值**。

### 默认配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 监听端口 | `8080` | |
| 数据库类型 | `sqlite` | sqlite / mysql / postgres |
| 数据库路径 | `./data/cimoc.db` | SQLite 专用 |
| JWT 密钥 | 自动生成 | 重启后会变化，建议固定 |

### 方式一：YAML 配置文件

创建 `config.yaml`：

```yaml
server:
  port: "8080"

database:
  type: "sqlite"          # sqlite / mysql / postgres
  path: "./data/cimoc.db"  # SQLite 文件路径
  # dsn: "user:pass@tcp(127.0.0.1:3306)/cimoc?charset=utf8mb4&parseTime=True"  # MySQL
  # dsn: "host=localhost user=user password=pass dbname=cimoc port=5432 sslmode=disable"  # PostgreSQL

jwt:
  secret: ""  # 留空则首次启动自动生成
```

使用方式：

```bash
xcimoc-data-server.exe --config config.yaml
```

项目自带了 `config.example.yaml` 作为参考模板。

### 方式二：环境变量

| 环境变量 | 对应配置 | 示例 |
|----------|----------|------|
| `SERVER_PORT` | 监听端口 | `8080` |
| `DB_TYPE` | 数据库类型 | `sqlite` / `mysql` / `postgres` |
| `DB_PATH` | SQLite 路径 | `./data/cimoc.db` |
| `DB_DSN` | MySQL/PostgreSQL 连接串 | `user:pass@tcp(...)` |
| `JWT_SECRET` | JWT 签名密钥 | `my-secret-key` |

```bash
# Windows PowerShell
$env:SERVER_PORT="8080"
$env:DB_PATH="./data/cimoc.db"
xcimoc-data-server.exe

# Linux / macOS
export SERVER_PORT=8080
export DB_PATH="./data/cimoc.db"
./xcimoc-data-server
```

### 方式三：CLI 参数

```bash
xcimoc-data-server.exe \
  --port 8080 \
  --dbtype sqlite \
  --data ./data/cimoc.db \
  --jwtsecret my-secret-key
```

---

## 数据库配置

### SQLite（默认）

开箱即用，无需额外配置。数据库文件默认生成在 `./data/cimoc.db`。

```bash
# 指定路径
xcimoc-data-server.exe --dbtype sqlite --data /自定义路径/cimoc.db
```

### MySQL

```bash
xcimoc-data-server.exe \
  --dbtype mysql \
  --dbdsn "user:password@tcp(127.0.0.1:3306)/cimoc?charset=utf8mb4&parseTime=True"
```

### PostgreSQL

```bash
xcimoc-data-server.exe \
  --dbtype postgres \
  --dbdsn "host=localhost user=user password=pass dbname=cimoc port=5432 sslmode=disable"
```

---

## 管理员操作

### 首次启动获取密码

首次启动时，管理员密码会打印在控制台日志中，格式为：

```
  用户名: admin
  密码: aB3xK9mP2rFz
```

### 通过命令行重置管理员密码

如果忘记密码，可使用 `set admin` 命令重置：

```bash
# 基本用法
xcimoc-data-server.exe set admin <新密码>

# 如果使用了配置文件
xcimoc-data-server.exe --config config.yaml set admin <新密码>
```

要求：新密码长度至少 6 位。

> ⚠️ 重置密码会同时使所有已登录用户的 token 失效，需要重新登录。

### 通过管理后台管理用户

打开 `http://<服务器IP>:8080/admin`，使用管理员账号登录后可以：
- **修改密码**：修改当前管理员的登录密码
- **创建用户**：为 Android 客户端创建普通用户账号
- **查看用户**：查看所有已注册用户列表

---

## 生产环境部署建议

### 1. 固定 JWT 密钥

务必通过配置文件、环境变量或 CLI 参数固定 `JWT_SECRET`，否则重启服务器后所有用户的 token 将失效。

```bash
xcimoc-data-server.exe --jwtsecret "your-strong-secret-key"
```

### 2. 使用反向代理（Nginx / Caddy）

建议在生产环境中使用反向代理提供 HTTPS 支持和域名绑定。

**Nginx 配置示例：**

```nginx
server {
    listen 443 ssl;
    server_name sync.yourdomain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 3. 使用 systemd 管理（Linux）

创建 `/etc/systemd/system/cimoc-sync.service`：

```ini
[Unit]
Description=Cimoc Data Sync Server
After=network.target

[Service]
Type=simple
User=cimoc
WorkingDirectory=/opt/cimoc-sync
ExecStart=/opt/cimoc-sync/xcimoc-data-server --config /opt/cimoc-sync/config.yaml
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now cimoc-sync
```

### 4. 使用 MySQL / PostgreSQL

生产环境建议使用 MySQL 或 PostgreSQL 替代 SQLite，以获得更好的并发性能和数据可靠性。

---

## API 参考

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/health` | 健康检查 | 无 |
| POST | `/api/auth/login` | 用户登录 | 无 |
| POST | `/api/auth/refresh` | 刷新 Token | 过期 Token（7天内） |
| GET | `/api/comics` | 获取已同步漫画 | Bearer Token |
| POST | `/api/comics/sync` | 上传/合并漫画 | Bearer Token |
| DELETE | `/api/comics/:id` | 删除漫画同步记录 | Bearer Token |
| GET | `/api/settings` | 获取同步的设置 | Bearer Token |
| POST | `/api/settings/sync` | 上传/合并设置 | Bearer Token |
| GET | `/api/admin/users` | 用户列表 | Admin Token |
| POST | `/api/admin/users` | 创建用户 | Admin Token |
| POST | `/api/admin/password` | 修改密码 | Admin Token |
| GET | `/admin` | 管理后台 SPA | - |
| GET | `/` | 重定向到 /admin | - |

### 登录请求示例

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "your-password"}'
```

### 同步漫画示例

```bash
curl -X POST http://localhost:8080/api/comics/sync \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"comics": [{"source": 1, "cid": "123", "title": "漫画名", "history": 1700000000000}]}'
```

---

## Android 客户端设置

部署好服务器后，在 XCimoc Android 客户端中按以下步骤连接并使用数据同步功能。

### 1️⃣ 打开数据同步界面

打开 App → **备份** → **服务器数据同步**。

### 2️⃣ 配置服务器地址

点击 **⚙️ 配置服务器**，输入你的服务器地址：

```
http://服务器IP:8080
```

> - 如果服务器与手机在同一局域网，可用内网 IP 如 `http://192.168.1.100:8080`
> - 如果使用公网服务器，填写域名或公网 IP，如 `https://sync.example.com`
> - 地址格式：`http://<IP>:<端口>`，末尾不要带斜杠

### 3️⃣ 登录

点击 **🔑 登录**，在弹出的对话框中输入 **用户名** 和 **密码**：

- **首次使用**：用控制台打印的管理员账号 `admin` + 随机密码登录
- **推荐做法**：登录管理后台 `http://服务器IP:8080/admin` 创建一个专用普通用户供客户端使用
- 登录成功后，token 会保存在本地，后续无需重复登录

### 4️⃣ 手动同步

登录后即可使用同步按钮：

| 操作 | 说明 |
|------|------|
| **☝️ 上传漫画** | 将本地收藏和阅读记录同步到服务器 |
| **☝️ 上传设置** | 将 App 设置同步到服务器 |
| **☝️ 全部上传** | 同时上传漫画 + 设置 |
| **👇 下载漫画** | 从服务器恢复收藏和阅读记录到本地 |
| **👇 下载设置** | 从服务器恢复设置到本地 |
| **👇 全部下载** | 同时下载漫画 + 设置 |

### 5️⃣ 开启自动同步（推荐）

打开 **🔄 自动同步** 开关，之后：

- **App 启动或回到前台时** → 自动全量同步（间隔不小于 5 分钟）
- **收藏或取消收藏漫画时** → 3 秒防抖后自动同步漫画数据
- **更新阅读记录时** → 3 秒防抖后自动同步漫画数据
- 同步失败不会弹提示，避免干扰使用

### 6️⃣ 退出登录

点击 **🚪 退出登录** 即可清除本地 token，停止同步。

---

## 常见问题

---

## 常见问题

### Q: 启动后提示端口被占用

使用其他端口：

```bash
xcimoc-data-server.exe --port 9090
```

### Q: 如何查看当前版本？

```bash
# 目前通过编译时间判断
go build -o xcimoc-data-server.exe -ldflags "-X main.buildTime=$(date)" .
```

### Q: 数据库文件在哪里？

默认位置为运行目录下的 `./data/cimoc.db`（SQLite）。可通过 `--data` 参数或 `DB_PATH` 环境变量修改。

### Q: 如何备份数据？

SQLite 数据库是单个文件，直接复制 `cimoc.db` 即可备份。MySQL/PostgreSQL 使用各自的备份工具。

### Q: 客户端连接不上？

1. 确认服务器已启动且端口可访问
2. 检查防火墙是否放行了对应端口
3. 检查 Android 客户端中输入的服务器地址是否正确（格式：`http://IP:端口`）
4. 如果是 HTTPS 环境，请使用 `https://` 前缀
5. 尝试在浏览器中访问 `http://服务器IP:端口/api/health` 确认服务运行正常
