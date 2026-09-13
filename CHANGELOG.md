> [!TIP]
> `v1.10.9及以下的版本更新到v1.11.0以及以上的版本`
>
> 1. 由于更新了数据库框架，原有的数据需要迁移。
> 2. 请使用软件的备份恢复功能进行数据迁移。
> 3. 已下载漫画可以在设置里找到扫描选项

## v1.15.0

### feat

- 漫画源重构为 JS 脚本配置：移除内置 Java 源，支持在线更新源与自定义源仓库；新增 JS 源管理/设置界面（登录、设置项按脚本声明自动生成）
- 阅读器新增电量图标，充电/低电量变色

### fix

- 修复界面间大数据传输导致的闪退（章节列表改静态缓存传递）
- 修复章节切换误加载相邻章节、进度条不同步及滑块设置无法保存的问题
- 修复任务列表下拉刷新、下载服务有时闪退的问题
- 修复对话框被软键盘遮挡；兼容源脚本 getHeader() 返回 null
- 修改拖动条UI
- 数据同步服务器：登录失败限流、事件推送幂等去重

### perf

- 漫画源加载提速（并行预热解析器、分类页后台筛选）；本地漫画读取消除 N+1 查询；Fresco 磁盘缓存提升至 2GB

### build

- 更新依赖；新增预览版 tag 构建工作流

### docs

- README 更新

---

## v1.14.0

### feat

- 集成 QuickJS JavaScript 引擎（quickjs-ng），添加 JNI 封装、兼容层及 UTF-8/UTF-16 Native 转换，支持自定义 JS 脚本执行
- 添加基于 WebView 的 JS 执行工具（WebViewJsExecutor），支持在真实浏览器环境中执行脚本
- 新增漫画源：漫画之家（ManHuaZhiJia）、动漫嗨（DongManHi）
- 添加 Cloudflare 认证处理功能，支持自动等待和交互式验证
- 新增搜索关键词历史记录管理，支持自动补全与持久化
- 详情界面和结果界面新增下拉刷新功能；OkHttp 缓存调整为 5 分钟 + 内存缓存 + 超时降级展示
- 深色模式切换不再需要重启应用即可生效
- 阅读界面全面现代化改造，优化快捷设置面板描边按钮边框色，支持明暗模式自适应；新增阅读器加载图标
- 切换默认阅读模式时同步保存，下次打开自动生效
- 集成 dav4jvm WebDAV 客户端替换 sardine-android；WebDavClient 新增获取资源最后修改时间的能力，WebDav 文档文件处理更完整
- 添加 Base64Utils 工具类，统一替换各处 Base64 解码逻辑
- 数据同步引入事件驱动（event-based）机制，实现漫画与标签的增量同步
- 事件推送 / 拉取接口新增最新事件流尾部返回（latest_id），配合 since_id 参数推进同步游标
- 优化 CORS 配置，允许空数组上传，增强跨域兼容性

### fix

- 修复 QuickJS JNI 构建与字符转换等兼容性问题
- 修复 build.gradle 语法、Gradle JVM 参数，移除不必要的 Kotlin 插件/依赖
- 修复腾讯漫画、G社漫画（GFMH）、TTKMH、YKMH、古风漫画、MYCOMIC 等多个漫画源；优化 Cloudflare 提示与配置
- 修复取消收藏后同步到服务端的逻辑错误
- 修复导出漫画出现重复章节、下载漫画的潜在错误
- 修复章节数据更新、数据修复、数据加载等流程的 bug
- 修复结果界面 / 其他界面被系统返回键或底部控件遮挡的问题
- 修复闪退 bug：多处空值处理、序列化、反射调用、WebParser 初始化
- 修复 OkHttp 缓存：清理策略改为自动清理、仅缓存文本，统一缓存时长并修复错误缓存 / 一直加载旧缓存的问题
- 自定义 Snackbar 重构：底部居中布局、文字颜色修复，切换到 Material 主题
- 修复搜索界面关键词自动补全、布局样式
- 简化下载通知更新逻辑
- 漫画更新检查：增加超时时间、优化并发数、失败不中断整体进度；优化 Manga 类解析逻辑与更新检查流程
- 修复 Headers / ImageUrl 转换器，移除不必要的转换器，加强空值处理
- 修复获取稳定标识的逻辑，改进空值处理和反射调用
- 修复 OkHttpNetworkFetcher 构造函数 headers 传递，改为实时获取 HttpClient 以支持运行时网络设置切换
- WebParser：新增 Wi-Fi 网络检查（仅 Wi-Fi 下加载 WebView）；优化滚动等待时间、JS 桥接接口与异常处理，增加详细错误信息
- 优化 ReaderActivity 中系统栏可见性处理，移除不必要代码
- 优化 DecryptionUtils 字符集处理，统一为 UTF-8
- 修复 R8 混淆前的 Keep 规则检查，更新 ProGuard 规则
- 修复文件目录读取检查，确保源目录存在且可读
- 修复数据同步入口在设置页的位置、属性和文档说明
- 修复自动登录数据同步服务器的流程
- 修复 Gson 模型缺少 @SerializedName 的检测（SerializationDetector），支持 TypeToken / toJson
- 文件处理类切换到官方 DocumentFile，增强兼容性和稳定性
- SeekBar 组件重写并加固边界值处理，避免崩溃；ReaderPresenter 空列表从页码 1 开始
- 增强 JWT 密钥管理，持久化存储避免重启失效

### refactor

- 重构 WebParser：每个实例独立 WebView，移除静态实例管理；清理缓存与请求头逻辑、精简配置参数
- 重构适应 AGP 10（Android Gradle Plugin 10.x），移除旧的强制解析策略，Gradle/Lint 依赖升级
- 重构请求头管理：移除 ComicFrescoHeaders 类，支持更灵活的调用上下文
- 精简 CategoryActivity 与布局，移除不必要的 Spinner 逻辑
- 数据服务器全面迁移到 gorm.io/gen 类型安全 SQL 代码，移除手写字符串 WHERE 条件
- 移除 ObjectBox-go 相关代码，数据服务器存储全部切换到 CGO+GORM+SQLite
- 代码清理与样式统一（style：清理代码、翻译资源、工具命名空间修正）
- UI：优化 item_source 布局，简化结构并调整属性

### build

- 数据服务器：build.ps1 / build.sh 每次编译前自动重新运行 `go run ./gen`，支持 --gen-config 参数；调整 CGO 检查顺序
- 数据服务器：移除 ObjectBox 安装与 build tag，改为纯 CGO + gorm.io/driver/sqlite 构建
- 数据服务器：升级 Gin 到 v1.12.0 并启用 gin-contrib/gzip 压缩中间件，防止 Vary 头被覆盖
- 更新 QuickJS 子模块到 quickjs-ng 版本；更新 quickjs 子项目
- 多轮依赖更新（Gradle 插件、Lint、OkHttp、网络库、QuickJS 等）
- 多轮编译脚本修改（PowerShell / Bash）与 GitHub Actions 工作流调整
- compileSdk / targetSdk 升级到 37，新增 ACCESS_LOCAL_NETWORK 权限
- minSdkVersion 降低到 21，覆盖更多设备
- ProGuard 规则更新，添加反射检测器；新增自定义 Lint 检查以验证 R8 Keep 规则

### perf

- 漫画同步逻辑改为批量处理，减少数据库查询次数；更新数据模型索引提升查询效率
- 网络请求与下载管理引入专用线程池，提升并发性能
- 注释掉 Sync 方法中的性能计时逻辑，精简运行时开销
- 同步游标失效自愈：客户端在服务端数据重置时自动恢复（since_id 不再卡死）
- 新增去重合并功能，优化漫画恢复时的显示逻辑

### docs

- README.md 多项更新说明
- 更新数据同步服务器文档 / 部署说明

---

## v1.13.3

### fix

- 修复路径处理
- 修复Progress对话框显示
  
## v1.13.2

### fix

- 修复Komiic漫画

## v1.13.1

### fix

- 修复数据服务器token刷新

---

## v1.13.0

### feat

- 添加数据同步服务（Go后端），支持跨设备数据同步
- 添加数据同步界面，整合数据同步功能
- 添加 MySQL 和 PostgreSQL 数据库支持
- 添加管理后台及部署文档
- 新增漫画源：拷贝漫画Web集成
- Komiic 漫画源：显示剩余章节、自动切换线路、登录失败自动重试
- 再漫画源：自动登录
- 增加后台探测可用域名功能，避免主线程阻塞

### fix

- 修复漫画同步bug，优化同步逻辑避免重复通知UI
- 修复拷贝漫画源
- 修复漫画源问题
- 修复 Komiic 自动登录问题
- 优化历史记录清除逻辑，确保正确标记需清除的漫画历史
- 优化设置同步逻辑，添加错误处理提高稳定性
- 修复加载对话框
- 修复 decrypt 方法异常处理
- 更新数据库模型，添加唯一索引优化数据完整性和查询性能
- 修复 MySQL 外键约束处理逻辑
- 性能优化
- 移除不必要的 URLEncoder 导入，简化请求体创建逻辑

### refactor

- 拷贝漫画源重构，拆分 CopyMHBase 基础类
- 数据同步逻辑重构，移除标签同步和高亮字段支持

### build

- 更新 Gradle 插件和包装器版本以提高构建稳定性
- 更新依赖
- 更新 GitHub Actions 工作流

### docs

- 添加数据服务器部署文档（DEPLOY.md）
- 更新 README.md

---

## v1.12.0

### feat

- 添加导出和检查更新服务，优化用户体验
- 添加权限管理和提示信息，优化用户体验
- 优化章节删除和文件复制操作，提升性能和并发处理能力

### fix

- 修复闪退和数据重复bug
- 修复简介清空bug和漫画源界面bug
- 修复 WebParser 永久阻塞 RxJava 线程的问题
- 修复读漫屋App
- 修复布卡漫画
- 修复detail对话框颜色不对的问题
- 修复章节按钮颜色不对
- 修复颜色引用问题
- 优化状态保存与恢复，增强配置变化后的用户体验
- 使用 setData 替换数据以避免重复加载
- 删除拷贝漫画cdn配置
- 更新 Android Gradle 插件版本至 9.1.1

### refactor

- 迁移到RxJava3
- 迁移到View Binding

### build

- 更新 GitHub Actions 工作流，添加 Gitee 同步和发布步骤
- 更新依赖

---

## v1.11.0

### fix

- 修复大量Bug
- 修复拷贝漫画

### feat

- 新增漫画源：拷贝漫画Web

### build

- 更新依赖

<!-- ### ui -->

### refactor

- 更新数据库框架为ObjectBox

 
 