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
