<img src="./screenshot/icon02.png" width="180">

# XCimoc

# 应用简介

Android 平台在线漫画阅读器\
Online manga reader based on Android

forked from https://github.com/Haleydu/Cimoc

# 下载

可以前往项目的[Release](https://github.com/xyrlsz/Cimoc/releases)处或者[Action](https://github.com/xyrlsz/Cimoc/actions)处下载（不建议从Action下载）。

# 漫画源

<!-- > 漫画源工作情况可以在[project](https://github.com/xyrlsz/Cimoc/projects/1)中进行查看，请尽量不要重复issues -->
> - 各位大佬们提交漫画源相关issue请按照[模板](.github/ISSUE_TEMPLATE/comic-source-issues.md)填写，方便检查问题。
> - 部分漫画源可能中国大陆无法访问，需要使用代理，具体请自行搜索。
# 功能简介

- 翻页阅读（Page Reader）
- 卷纸阅读（Scroll Reader）
- 检查漫画更新（Check Manga For Update）
- 下载漫画（Download Manga）
- 本地漫画（Local Reader）
- 本地备份恢复（Local Backup）
- WebDav云备份功能(WebDav Backup)
- 数据同步服务器（Data Sync Server）- 跨设备同步收藏、阅读记录、应用设置
- 下载漫画后导出漫画（Export Manga）

# 软件使用说明

- 安装完成后，直接点击右上角的搜索，即可搜索到漫画，需要手机安装[WebView组件](https://play.google.com/store/apps/details?id=com.google.android.webview)
- 报毒问题已解决

# 感谢以下的开源项目及作者

- [Android Open Source Project](http://source.android.com/)
- [ButterKnife](https://github.com/JakeWharton/butterknife)
- [ObjectBox](https://objectbox.io/)
- [OkHttp](https://github.com/square/okhttp)
- [Fresco](https://github.com/facebook/fresco)
- [Jsoup](https://github.com/jhy/jsoup)
- [DiscreteSeekBar](https://github.com/AnderWeb/discreteSeekBar)
- [RxJava](https://github.com/ReactiveX/RxJava)
- [RxAndroid](https://github.com/ReactiveX/RxAndroid)
- [Rhino](https://github.com/mozilla/rhino)
- [AppUpdater](https://gitee.com/jenly1314/AppUpdater)
- [android-opencc](https://github.com/xyrlsz/android-opencc)
- [sardine-android](https://github.com/thegrizzlylabs/sardine-android)

# 应用截图

| 收藏页 |  
| :---: | 
| <img src="./screenshot/03.png" width="260">  |

# 增加图源（欢迎pr）

- 继承 MangaParser 类，参照 Parser 接口的注释

> 在app/src/main/java/com/xyrlsz/xcimoc/source目录里面随便找一个复制一下
> 注释是这个：app/src/main/java/com/xyrlsz/xcimoc/parser/MangaParser.java

<!-- - （可选）继承 MangaCategory 类，参照 Category 接口的注释
> 这个没什么大用的感觉，个人不常用，直接删掉不会有什么影响 -->

- 在 SourceManger 的 getParser() 方法中加入相应分支

> case 里面无脑添加

<!-- - 在 UpdateHelper 的 initSource() 方法中初始化图源，以及修改initComicSourceTable()方法 -->
- 在 UpdateHelper 的 initComicSourceTable() 方法中初始化图源
- 修改"app/src/main/java/com/xyrlsz/xcimoc/ui/activity/BrowserFilter.java"的registUrlListener()方法

# 软件更新方向：

- 能正常搜索解析网络上大部分免费的漫画
- 界面简洁为主
- 解决apk影响体验的问题

# 数据同步服务器（可选）

本软件支持自建数据同步服务器，实现收藏、阅读记录、应用设置的多设备同步。

- **服务端程序**：位于 `data_server/` 目录，Go 语言编写
- **部署文档**：详见 [data_server/DEPLOY.md](data_server/DEPLOY.md)
- **功能**：跨设备同步漫画收藏、阅读进度、应用设置
- **内置管理后台**：Web 界面管理用户和密码

> 此功能为可选项，不配置服务器不影响单机使用。

# 漫画源测试

本项目包含一套自动化的漫画源测试工具，用于验证所有内置漫画源网站的解析功能。

## ✨ 特点

- **直接复用 Java 源代码** — 编译运行真实的 Android 漫画源解析器
- **零 Android 依赖** — 内置 30+ Android 桩代码和 ObjectBox 桩代码
- **完整的测试流程** — 搜索→提取CID→详情解析→章节列表→图片列表
- **自动发现代码 bug** — 发现 JSON 类型不匹配、API 变更等源码级别问题

## 工具概览

| 组件 | 说明 |
|------|------|
| `sourcetester/` | Java 测试模块，直接编译运行漫画源代码 |
| `.github/workflows/test-sources.yml` | GitHub Actions 工作流，定时运行测试并生成报告 |
| `docs/` | GitHub Pages 静态页面，展示最新测试报告 |

## 在线测试报告

测试结果通过 GitHub Pages 在线展示：  
👉 **[查看最新测试报告](https://xyrlsz.github.io/XCimoc/)**

报告包含：
- 所有漫画源的通过/失败状态
- 每个测试项的详细耗时和错误信息
- 通过率统计和进度条
- 可展开查看每个源的详细测试结果

## 测试内容

对每个漫画源执行以下测试：

1. **基础连通性** — 源网站是否可访问
2. **搜索+提取CID** — 用 a-z 轮换关键词搜索，从结果中提取漫画 ID
3. **详情页** — 访问漫画详情页，解析标题、作者、封面等信息
4. **章节列表** — 提取章节列表，获取首个章节路径
5. **分类浏览** — 分类页面是否可访问（如适用）

## 本地运行

```bash
# 测试所有源
./gradlew :sourcetester:run

# 测试指定源（按 TYPE）
./gradlew :sourcetester:run --args="--source 0,5,26"

# 生成报告到指定目录
./gradlew :sourcetester:run --args="--output-dir ./reports"

# 详细输出
./gradlew :sourcetester:run --args="--verbose"
```

## GitHub Actions

工作流 `test-sources.yml` 支持：

- **定时触发** — 每天 UTC 02:00 自动运行测试
- **手动触发** — 通过 GitHub Actions 页面手动运行，可指定要测试的源
- **代码推送触发** — 当漫画源代码变更时自动运行
- **自动部署** — 测试完成后自动将报告部署到 GitHub Pages

### 部署到你的 GitHub Pages

1. 将代码推送到 GitHub 仓库
2. 在仓库 Settings → Pages 中，选择 "GitHub Actions" 作为 Source
3. 确保 `docs/` 目录包含 `index.html` 和 `report.html`
4. 首次运行 workflow 后，报告将自动部署到 GitHub Pages

# 关于淘宝售卖和会员破解

- 本程序没有任何破解网站VIP的功能，仅仅作为网页浏览器显示网站免费浏览部分，淘宝卖家自行添加的破解或其他功能与本程序无任何关系。

# 免责声明：

- 本软件仅用于学习交流，不得用于商业用途
- 本软件不对因使用本软件而导致的任何损失或损害承担责任
- 所有漫画均来自用户在第三方网站上的手动搜索

