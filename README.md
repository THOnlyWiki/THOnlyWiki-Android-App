# 东方活动速查 · Android

[thonly.cc](https://thonly.cc)（东方Project线下活动维基）的安卓客户端。

原生界面，数据全部走 `/api/thonly-activity` 接口，**不加载网页、不打包浏览器内核**，安装包 0.3 MB 量级。

## 功能

- 速查表：按年份、类型筛选，支持按活动名 / 地区 / 状态搜索
- 日历：月历视图，有活动的日期带圆点，点日期看当天活动
- 一键把单场活动加入系统日历（下载 `.ics`）
- 详情弹窗可复制 QQ 群号
- 断网时展示上一次缓存的数据

## 目录结构

```
app/src/main/java/cc/thonly/app/
├── AppConfig.java              全局配置（服务器地址、超时、UA）
├── data/                       数据层
│   ├── Event.java              活动实体（不可变）
│   ├── EventData.java          某年的活动集合 + 筛选/查询
│   ├── EventDate.java          日期值对象（兼容服务端自由文本）
│   ├── EventCategory.java      类型枚举
│   ├── EventStatus.java        状态枚举（带颜色映射）
│   ├── EventRepository.java    仓库接口
│   ├── RemoteEventRepository.java   HTTP 实现 + 缓存兜底
│   ├── EventStore.java         状态容器：异步加载 + 观察者通知
│   └── EventApiException.java
├── ui/                         界面层
│   ├── MainActivity.java       只做装配，不含业务
│   ├── ListController.java     速查表页逻辑
│   ├── CalendarController.java 日历页逻辑
│   ├── EventAdapter.java       列表适配器（两页共用）
│   ├── EventActions.java       详情弹窗 / 加日历 / 复制群号
│   ├── EventInteraction.java   列表项操作接口
│   ├── ChipFactory.java        筛选标签样式工厂
│   └── MonthView.java          自绘月历
└── util/
    ├── HttpClient.java         极简 GET
    ├── FileCache.java          文件缓存
    └── IcsDownloader.java      .ics 下载
```

分层原则：`ui` 不碰网络，`data` 不碰 Android UI，`util` 不依赖业务。
两个页面共用一个 `EventStore`，切换年份只发一次请求。

## 构建

**Android Studio**：直接打开本目录，等待 Gradle 同步完成即可。

**命令行**：

```bash
export JAVA_HOME=/path/to/jdk-17-or-21
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

> Gradle 8.9 不支持 JDK 25，命令行构建请用 JDK 17 或 21（Android Studio 自带的 JBR 也可以）。

## 签名

默认用 debug 签名，编出来就能直接装、直接发群里。长期维护请换成自己的密钥，否则换台机器打包会导致无法覆盖安装：

```bash
keytool -genkeypair -v -keystore release.jks -alias thonly \
  -keyalg RSA -keysize 2048 -validity 10000
```

然后把 `app/build.gradle` 里的 `signingConfig signingConfigs.debug` 换成你自己的配置。

## 改服务器地址

只改 `AppConfig.BASE_URL` 一处即可。

## 接口文档

见 [docs/API.md](docs/API.md)。

## 体积说明

刻意不引入任何第三方依赖：没有 AndroidX、没有 Kotlin stdlib、没有 Retrofit/OkHttp。
JSON 用系统自带的 `org.json`，网络用 `HttpURLConnection`，日历控件自己画。
加依赖前请先评估体积代价——这个项目的核心卖点就是小。

## 已知取舍

- 只读：不包含登录、编辑、评论等功能，需要这些请用网页版
- 服务端 `date` 是自由文本，只有能解析到具体某天的活动才会出现在月历里；`暂无`、`2026年末` 这类只在列表中展示原文
- 未做深色模式，站点本身是浅色皮肤
