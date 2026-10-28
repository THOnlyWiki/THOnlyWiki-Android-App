# thonly.cc 接口记录

抓取时间：2026-10-28。站点 `generator` 为 `WikiKernel`。

## 一、活动数据接口（App 实际使用）

前缀：`https://thonly.cc/api/thonly-activity`

### GET /years

```json
{
  "years": [2024, 2025, 2026, 2027, 2028],
  "categories": ["tho", "thp", "school", "live"],
  "canEdit": false,
  "loggedIn": false
}
```

### GET /list?year=2026

```json
{
  "year": 2026,
  "years": [2024, 2025, 2026, 2027, 2028],
  "categories": ["tho", "thp", "school", "live"],
  "canEdit": false,
  "loggedIn": false,
  "rows": [
    {
      "id": 199,
      "category": "live",
      "status": "定档",
      "name": "江西THO4.5 Trance DJ Party LUNA in 温汤",
      "region": "江西省",
      "date": "2026/2/13",
      "qq": "1057195011",
      "created_at": "2026-10-17 11:47:14",
      "updated_at": "2026-10-17 11:47:14",
      "icsUrl": "/excel/calendar/2026/199.ics",
      "year": 2026
    }
  ]
}
```

实测数据量：2026 年 218 条，2025 年 207 条，2027 年 4 条。

### POST /save

写接口，需要登录态（`canEdit` 为 true 时才可用）。客户端是只读的，没有接。

### 参数边界（实测）

| 请求 | 结果 |
|---|---|
| `/list`（不带 year） | 默认当前年，正常返回 |
| `/list?year=2025` | 正常 |
| `/list?year=2026&category=tho` | `category` 被忽略，仍返回全部 218 条 |
| `/list?year=all` | 返回 HTML 错误页（非 JSON） |
| `/list?year=1999` | 返回 HTML 错误页（非 JSON） |
| `/list?year=abc` | 返回 HTML 错误页（非 JSON） |

**所以客户端不能假设错误响应是 JSON**，需要先判断 Content-Type 或直接按解析失败处理。

### 响应头

```
Content-Type: application/json
Access-Control-Allow-Origin: *
Cache-Control: no-cache
```

无 gzip（`Content-Encoding` 缺失），56 KB 的 JSON 是裸传。

## 二、字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int | 活动 ID，与 `.ics` 文件名一致 |
| `category` | string | `tho` / `thp` / `school` / `live`，前端文案分别是 THO / THP / 校联 / Live |
| `status` | string | 自由文本，实测值：`计划`(122)、`定档`(88)、`取消`(7)、`延期`(1) |
| `name` | string | 活动名 |
| `region` | string | 省级地区，如 `广东省` |
| `date` | string | **自由文本，见下** |
| `qq` | string | QQ 群号，实测 218 条全部非空 |
| `icsUrl` | string | 站内相对路径，需拼 `https://thonly.cc` |
| `year` | int | 归属年份 |

### date 字段的真实形态

2026 年 218 条数据的分布：

| 形态 | 条数 | 说明 |
|---|---|---|
| `2026/2/13` | 106 | 可解析到日 |
| `2026/3/22` 等个位日 | 61 | 可解析到日 |
| `2026/12/05` 等 | 17 + 16 | 可解析到日 |
| `暂无` | 15 | 无日期 |
| `2026年3月末-4月初（待定）` | 1 | 只能取到年 |
| `2026年末` | 1 | 只能取到年 |
| `2026年秋季学期第十二/十三周周末（待定）` | 1 | 只能取到年 |

客户端必须容错：解析失败就展示原文，不要抛异常。

## 三、.ics 日历文件

`GET https://thonly.cc/excel/calendar/{year}/{id}.ics`

```
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//WikiKernel//thonly-wiki-extensions//CN
CALSCALE:GREGORIAN
BEGIN:VEVENT
UID:thonly-activity-20260213-1684200115@wikikernel
DTSTAMP:20261028T000000Z
DTSTART;VALUE=DATE:20260213
DTEND;VALUE=DATE:20260214
SUMMARY:江西THO4.5 Trance DJ Party LUNA in 温汤
LOCATION:江西省
DESCRIPTION:状态：定档\n地区：江西省\nQQ群：1057195011\n来源：东方Project线下活动速查表
END:VEVENT
END:VCALENDAR
```

公开可访问，不需要登录。App 通过系统 DownloadManager 下载后交给日历应用。

## 四、站点其他特征（供参考）

- 页面是**服务端渲染**的，不是 SPA，DOM 里带 `mw-body`、`mw-portlet` 等 MediaWiki 风格类名
- 皮肤 `kawaii-timeless`，插件体系在 `/plugins/*`（comments、ai-agent、media-viewer 等 20+ 个）
- 前端一次性加载 25 个核心 JS + 25 个插件 JS/CSS，外加 jQuery、axios、Vega 全家桶——所以用 WebView 套壳会很重，直接走 JSON 接口是更合适的选择
- 速查表页面的表格行也带了 `data-id`、`data-date`、`data-region` 等属性，可以作为接口之外的备用数据源
- MediaWiki 兼容 API 可用：`/api.php?action=query&meta=siteinfo`、`list=allpages`、`list=search` 均返回 200
- `/api.php?action=parse&prop=text` 返回的 `text.*` 是空字符串，页面渲染不走这个接口
- `/rest.php` 未启用（404）
- `/.well-known/assetlinks.json` 被 Wiki 路由接管，返回的是维基页面而不是文件——如果要走 TWA 方案，需要先在 nginx/openresty 层把这个路径排除出重写规则
- 首页 `302` 跳转到 `http://thonly.cc/首页`（明文），这是 `$wgServer` 配置成 http 导致的；客户端如果加载网页会踩到 cleartext 限制

## 五、用户接口（登录 / 注册）

App 的登录注册走的就是站点表单同一套接口，表单编码（`application/x-www-form-urlencoded`），
返回 `{code, success, message, data}`。

### POST /api/user/login

| 字段 | 说明 |
|---|---|
| `username` | 用户名 |
| `password` | 密码 |
| `remember` | 可选，传 `1` 时下发 30 天的 `wiki_token` |

成功：

```
HTTP/1.1 200
Content-Type: application/json
Set-Cookie: JSESSIONID=...; Path=/; HttpOnly; SameSite=Lax
Set-Cookie: wiki_token=...; Path=/; Max-Age=2592000; HttpOnly; SameSite=Lax

{"code":200,"success":true,"message":"登录成功",
 "data":{"id":126,"username":"...","avatar":"","email":"","groups":...}}
```

失败：`{"code":500,"success":false,"message":"用户名或密码错误"}`（HTTP 仍是 200，要看 body 的 code）

### POST /api/user/register

| 字段 | 说明 |
|---|---|
| `username` | 用户名 |
| `password` | 密码 |
| `confirmPassword` | 确认密码 |
| `email` | 可选 |
| `timeZone` | 可选，`Asia/Shanghai` 这种 IANA 名称 |

成功后同样下发 Cookie，等于注册即登录。

### 页面地址

- 登录：`/特殊:登录?returnto=...`
- 注册：`/特殊:注册?returnto=...`

站点自己在 `returnto` 里会带上 `open_reference=app`，说明这个参数是站点认可的来源标记。

> 备注：联调时用 `__probe__` 探测注册接口，在站上真实创建了账号（id 126），需要在后台删除。

## 六、App 抽屉配置（WikiKernel:Sidebar-App）

App 的抽屉内容可由服务端下发，来源页面：

```
https://thonly.cc/index.php?title=WikiKernel%3ASidebar-App&action=raw
```

内容是**严格 JSON**（`action=raw` 返回原文，`ctype` 参数无效，Content-Type 恒为 text/plain）。
解析失败、`schema` 过高、或 App 版本低于 `min_app_version` 时，整份配置都不同步，退回内置导航。

### 结构

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schema` | int | 是 | 结构版本，当前 1；服务端给更高版本时客户端整体忽略 |
| `min_app_version` | string | 否 | 最低 App 版本，低于它整份不同步 |
| `update_version` | int | 否 | 内容版本，仅作标识 |
| `mode` | string | 否 | `extend`（默认，内置项 + 云端项）/ `replace`（云端项接管内容区） |
| `sections[].title` | string | 否 | 分组标题 |
| `sections[].items[].label` | string | 是 | 显示文字 |
| `sections[].items[].target` | string | 否 | `app` / `web` / `external`，默认 `web` |
| `sections[].items[].page` | string | target=app 时必填 | `list` `calendar` `web` `login` `register` `settings` |
| `sections[].items[].url` | string | target=web/external 必填 | 站内相对路径或完整 URL；target=app 时作为老版本兜底 |

### 路由规则

- `app` → 打开原生页面；`page` 不认识时退化成用 `url` 开网页，入口不丢
- `web` → App 内 WebView 打开，自动补 `open_reference=app`
- `external` → 交给系统浏览器

### 分区约定

抽屉分两区：**内容区**（速查表 / 日历 / 主站，可被 `replace` 接管）和
**系统区**（登录 / 注册 / 退出 / 设置）。系统区永远保留，避免配置写坏后用户连退出都找不到。

### App 内隐藏的站点元素

App 打开站内页面时，会在**主文档到达渲染器之前**把样式插进 `<head>`
（`shouldInterceptRequest` 里自己取 HTML、插入 `<style>` 再交给 WebView），
所以首帧就是最终效果，不会出现「先显示完整页面再收起」的跳动。只去掉两个装饰元素：

```css
#p-banner, a.mw-wiki-logo { display: none !important; }
```

- `#p-banner` —— 顶部那条 750×80 的 wordmark 图
- `a.mw-wiki-logo` —— 侧栏的 logo 链接

**不要用 `.mdui-ripple` 做选择器**：它是站点的水波纹工具类，同时挂在
`#header-upload`（上传）和 `#header-search-button`（搜索）上，按类隐藏会把工具栏一起干掉。
其余元素（侧栏、页脚、工具栏）一律不动。

## 七、活动增删改（App 端）

与网页表格 `activity.js` 用的是同一套接口，表单编码，登录态靠 Cookie，
服务端另外校验 `edit` 权限（`/years` 里的 `canEdit`）。

### POST /api/thonly-activity/save

| 字段 | 说明 |
|---|---|
| `year` | 目标年份 |
| `id` | 空表示新增，非空表示改这一条 |
| `category` | `tho` / `thp` / `school` / `live` / `other` |
| `status` | `计划` / `定档` / `如期` / `延期` / `取消` |
| `name` | 名称，不能为空 |
| `region` | 地区，可空 |
| `date` | `2026/2/13` 这种写法；日期未定时传 `暂无` |
| `qq` | QQ 群号，可空 |
| `comment` | 写进变更历史的备注 |

成功：`{"saved": true, ...}`；失败：`{"message": "..."}`。

### POST /api/thonly-activity/delete

`year`、`id`、`comment`。成功返回 `{"deleted": true, ...}`。

### 客户端约定

- 写成功后必须 `forceRefresh` 重新拉取，否则会被本地离线存档盖住
- 未登录不显示编辑入口；退出登录时自动退出编辑模式
- 保存失败不关闭弹窗，直接把服务端 `message` 显示在弹窗里
