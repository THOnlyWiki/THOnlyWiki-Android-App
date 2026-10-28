# 东方Project线下活动日程 · Android

[thonly.cc](https://thonly.cc)（东方Project线下活动维基）的**官方安卓客户端**，与站点同源维护。

原生界面，数据全部走 `/api/thonly-activity` 接口，**不加载网页、不打包浏览器内核**，安装包 0.3 MB 量级。

## 功能

- 速查表：按年份、类型筛选，支持按活动名 / 地区 / 状态搜索
- 年份列表由服务端返回的年份 + 手机当前年份起两年自动合并，跨年后不会断档
- 日历：月历视图，有活动的日期带圆点，点日期看当天活动
- 点「＋日历」直接拉起系统日历的「创建日程」界面，标题、全天、日期、备注都已填好，
  确认保存即可（不需要日历权限）；设备上没有日历应用时回退为下载 `.ics`
- 详情弹窗可复制 QQ 群号
- 离线可用：接口数据落盘到应用私有目录，断网 / 服务端宕机时照样能看
- 导航用右侧抽屉，内容可由站点页面 `WikiKernel:Sidebar-App` 下发：
  每条可指定走原生页、App 内网页或系统浏览器，改导航不用发版
- 设置页：版本信息、自动检查更新开关、前往官网下载、清除离线存档
- 启动时检查更新，有新版本弹窗引导下载，可勾选「不再提醒该版本」
- 主站：在 App 内直接打开站点首页，站内跳转自动带 `open_reference=app`，站外链接交给系统浏览器
- 登录 / 注册：走站点同一套 `/api/user/*` 接口，登录后 Cookie 同步给主站 WebView，主站即是登录态；
  抽屉顶栏显示当前账号，登录后「登录 / 注册」自动换成「退出登录」

## 离线策略

面向「住校、可能偷偷带手机、经常没网」的使用场景，取数逻辑是：

1. 本地存档未过期（6 小时内）→ 直接用存档，秒开
2. 存档过期或不存在 → 请求网络，成功后立刻落盘
3. 网络失败 → 退回任意一份存档（哪怕已过期），保证有内容可看
4. 网络失败且完全没存档 → 才显示加载失败

存档写在 `filesDir/activity/activity-<年份>.json`，**不是** `cacheDir`——
系统在空间紧张时会清理 `cacheDir`，对没网的机器来说等于把唯一的数据源删了。

存档超过 1 小时且这次没连上网络时，顶部会出现一条提示：
「本地存档 · 更新于 10-28 20:38 · 点此刷新」，避免用户把旧数据当成最新的。

## 版本更新机制

最新版本号放在站点上，纯文本即可：

```
https://thonly.cc/index.php?title=WikiKernel%3AAppVersion&action=raw
→ 1.2.0
```

发版时把 `WikiKernel:AppVersion` 页面改成新版本号，客户端启动时比对
`versionName`，高于当前版本才提示。用户点「以后再说」下次仍会提醒，
勾选「不再提醒该版本」后该版本不再弹窗（服务端再发更高版本仍会提醒）。
设置页可以把「自动检查更新」整个关掉。

## 目录结构

单模块工程，模块就是仓库根目录，没有 `app/` 这一层：

```
thonlywiki-app/
├── build.gradle              模块配置（AGP、版本号、签名、混淆）
├── settings.gradle           rootProject.name，无 include
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/cc/thonly/app/
│   │   ├── AppConfig.java          服务器地址、超时、UA
│   │   ├── data/
│   │   │   ├── Event / EventData / EventDate / EventCategory / EventStatus
│   │   │   ├── EventRepository / RemoteEventRepository / LoadResult
│   │   │   ├── EventStore                    异步加载 + 观察者
│   │   │   ├── AppSidebar / SidebarRepository 抽屉配置下发
│   │   │   ├── AuthApi / UserSession         登录注册与登录态
│   │   │   ├── AppVersion                    版本号解析与比较
│   │   │   ├── SettingsStore                 本地设置
│   │   │   └── UpdateChecker                 检查更新
│   │   ├── ui/
│   │   │   ├── MainActivity                  装配：抽屉 + 页面路由
│   │   │   ├── ListController / CalendarController
│   │   │   ├── WebController                 主站 WebView + CSS 注入
│   │   │   ├── LoginController / RegisterController
│   │   │   ├── SettingsController
│   │   │   ├── EventAdapter / EventActions / EventInteraction / ChipFactory
│   │   │   ├── MonthView                     自绘月历
│   │   │   └── UpdateDialog / AuthHost
│   │   └── util/
│   │       ├── HttpClient / FileCache
│   │       ├── AppUrls                       URL 补全与 open_reference
│   │       ├── CookieBridge                  Cookie 注入 WebView
│   │       ├── CalendarInserter              拉起系统日历
│   │       └── IcsDownloader                 .ics 下载兜底
│   └── res/
└── tools/make_icons.py       图标生成脚本
```

分层原则：`ui` 不碰网络，`data` 不碰 Android UI，`util` 不依赖业务。
两个页面共用一个 `EventStore`，切换年份只发一次请求。

## 图标

`src/main/res/mipmap-*` 由 `D:\wwwroot\Touhou_Activities_Wiki_Logo.svg` 渲染而来：
无头 Chromium 转 PNG 后，用 ImageMagick 生成各密度图标与自适应图标前景。
API 26+ 走 `mipmap-anydpi-v26/ic_launcher.xml` 的自适应图标，低版本用方形 PNG 兜底。

## 构建

**Android Studio**：直接打开本目录，等待 Gradle 同步完成即可。

**命令行**：

```bash
export JAVA_HOME=/path/to/jdk-17-or-21
./gradlew assembleRelease
# 产物：build/outputs/apk/release/thonlywiki-app-release.apk
```

> Gradle 8.9 不支持 JDK 25，命令行构建请用 JDK 17 或 21（Android Studio 自带的 JBR 也可以）。

## 签名

默认用 debug 签名，编出来就能直接装、直接发群里。长期维护请换成自己的密钥，否则换台机器打包会导致无法覆盖安装：

```bash
keytool -genkeypair -v -keystore release.jks -alias thonly \
  -keyalg RSA -keysize 2048 -validity 10000
```

然后把 `build.gradle` 里的 `signingConfig signingConfigs.debug` 换成你自己的配置。

## 改服务器地址

只改 `AppConfig.BASE_URL` 一处即可。

## 接口文档

见 [docs/API.md](docs/API.md)。

## 体积说明

刻意不引入任何第三方依赖：没有 AndroidX、没有 Kotlin stdlib、没有 Retrofit/OkHttp。
JSON 用系统自带的 `org.json`，网络用 `HttpURLConnection`，日历控件自己画。
加依赖前请先评估体积代价——这个项目的核心卖点就是小。

## 已知取舍

- 编辑能力只在主站网页里做，App 不重复实现编辑器；登录/注册是原生表单，登录后主站即为登录态
- 服务端 `date` 是自由文本，只有能解析到具体某天的活动才会出现在月历里；`暂无`、`2026年末` 这类只在列表中展示原文
- 未做深色模式，站点本身是浅色皮肤
