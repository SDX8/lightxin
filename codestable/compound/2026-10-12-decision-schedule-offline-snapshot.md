---
doc_type: decision
category: architecture
date: 2026-10-12
slug: schedule-offline-snapshot
status: active
area: 课表离线可用
tags: [schedule, storage, offline, json, cache]
---

## 背景

改前课表完全依赖实时网络：断网打开 App 时，课表页整屏 `LxError("网络异常，请检查连接后重试")`，首页"今日课程"也拿不到数据，课程倒计时通知同样失去输入。`schedule-overview.md` §6 当时明确记录着"课表数据不缓存到本地"。

## 决定

课表增加**应用层本地快照**：`ScheduleCacheStore` 以单 JSON 文件（`filesDir/schedule_cache.json`）+ `Mutex` 落地周次信息与逐周课表；`ScheduleRepository` 采用 write-through（网络成功即写盘）+ 失败回退（网络异常时读快照，并把 `cachedAtMillis` 透出给 UI 标注数据新鲜度）。不引入 Room，不做启动预取。

## 理由

1. **OkHttp 的 HTTP 缓存对本项目无效** —— 课表两个接口（`dict/selectZcList.do`、`mobile/selectStuSelfTimeTable.do`）全是 `@FormUrlEncoded @POST`，OkHttp `Cache` 只缓存 GET/部分可缓存响应，靠网络层配置拿不到离线能力。
2. **与既有存储决定同构** —— `route-template-json-not-room` 已确立"轻量资产用 JSON 文件 + Mutex"的写法，`RouteTemplateStore` 是现成模板（原子写 `.tmp` + rename、`runCatching` 兜破损坏文件）。为一张课表再引入 Room schema 与 migration 不值得。
3. **两段式加载决定了周次信息也必须缓存** —— 课表查询依赖周次接口给出的学年/学期参数；只缓存课程而不缓存 `WeekInfo`，断网时第一段就失败，第二段根本不会被调用。
4. **失败即回退，而不是判网再回退** —— 不需要 `ConnectivityManager` 探活；回退原因可能是断网，也可能是登录失效或服务端 5xx，UI 措辞只陈述"快照时间 + 未能刷新"，不宣称具体原因。

## 考虑过的替代方案

- **OkHttp `Cache` + 强制 offline header**：改动小，但对 POST 表单接口不生效，直接排除。
- **Room**：查询能力强，但课表是"整周读写、不做关系查询"的资产语义，迁移维护成本换不到收益。
- **DataStore Preferences 存 JSON 串**：与 `TokenManager` 一致，但课表体积（整周课程）比偏好项大一个量级，且需要按周做键裁剪，文件方式更直观。
- **冷启动预取整学期 20 周**：离线覆盖率最高，但把 1 次请求放大成 20 次，对校园后端不友好；改为按需逐周缓存 —— 首页每次冷启动都会加载当前周，所以"断网时最可能需要的那一周"天然在盘上。

## 后果

- **首次安装后从未联网进过课表，断网仍无数据** —— 此时仍是原来的整屏错误，属于冷启动必然限制。
- **离线切到从未在线浏览过的周次仍是错误页** —— 缓存只覆盖浏览过的周；周选择器保持"能看则看，不能则提示重试"。
- **离线时当前周可能偏旧** —— `currentWeek` 来自快照，隔天/隔周断网打开时"今天"高亮可能落在旧周次上；靠快照时间戳向用户说明新鲜度。
- **缓存按 `userCode` 分键，并在每次写入时裁剪到当前账号 + 当前学期** —— 文件体积有上限，换账号后读不到上一位使用者的课表；退出登录不主动清理盘上快照（键含 userCode，不构成跨账号可读）。
- 首页与课程倒计时通知走同一个 `ScheduleRepository`，自动获得离线能力，未新增首页离线标识。

## 相关文档

- `codestable/architecture/schedule-overview.md` §2.8
- `codestable/compound/2026-04-22-decision-route-template-json-not-room.md`
- `feature/schedule/data/ScheduleCacheStore.kt`、`feature/schedule/data/ScheduleRepository.kt`
