---
doc_type: architecture
slug: checkin-overview
scope: feature/checkin/ 的查寝签到全流程（任务列表 / 详情 / 拍照 / 定位 / 提交 / 历史），并作为 fdygl 域同源任务的寄生入口（当前承载节假日离返校登记，未来可扩返校登记等同源任务）
summary: checkin 以分页列表 + 详情两页式交互，详情页集成了原生定位（WGS-84 → BD-09 坐标转换）、照片上传、与多字段鉴权提交；独立 FileUploadApi 处理图片上传；历史页按「月 + 状态档」向 pageStudentStatics 扇出查询。列表页同时双 section 承载同 fdygl 域的节假日任务（feature/holiday/），数据各自独立加载、错误彼此隔离、UI 共用一屏
status: current
last_reviewed: 2026-10-19
tags: [checkin, sign-in, sign-in-history, location, photo-upload, pagination, holiday]
depends_on: [network-overview]
---

# 查寝模块总览

## 0. 术语

- **taskDateId** — 签到任务的唯一标识，从列表接口多个可能字段中提取（`taskDateId` / `id` / `userTaskId` / `taskId`），是后续详情和提交的核心参数
- **WGS-84 → BD-09** — 原生定位得到 WGS-84 坐标，经 `CoordinateConverter.wgs84ToBd09()` 转为 BD-09 后上传；服务端期望 BD-09 格式
- **多 header 鉴权** — 查寝接口（`fdygl.aiit.edu.cn`）的 `AuthInterceptor` 需同时携带 `accessToken / access_token / userCode / xh / userType / X-Requested-With / Origin / Referer`，缺任一个就被判非法访问。实测（2026-10-19）：拒绝是以 **HTTP 200** 返回的 `{"code":"0","flag":false,"msg":"非法访问"}`，既不是 HTTP 错误码也不是 `-100`
- **成败看 `flag` 不看 `code`** — 统一走 `core/network/FdyglProtocol.kt` 的 `fdyglSucceeded(code, flag)`；只看 `code == "0"` 会把顶号/失效会话显示成「暂无签到与节假日任务」
- **照片上传** — 使用独立 `FileUploadApi`（Multipart），上传结果的数据结构不确定（可能是字符串或 JSON 对象），`extractUploadUrl()` 做启发式提取
- **状态档（executionedStatus）** — 官方 H5「查寝历史」页的五个签，本项目用前四档：`1`=已签到、`3`=已请假、`0`=未签到、`4`=登记离校（`5` 是辅导员侧「二维码查寝负责人次数」，学生端不用）。服务端在历史行里**不回**这个字段，状态由查询条件带回来，建模为 `CheckinRecordStatus`
- **`createBy`** — `pageStudentStatics` / `collectionStudentStatics` 必填的学号，取 `TokenManager.getUserCode()`；缺了直接 `-100 用户编码不能为空`。只能查自己，越权由服务端兜
- **`staticDate`** — 历史接口的月份键，格式 `yyyy-MM`，由 `checkinMonthKey()` 生成（不用 `String.format`，避免 locale digit 问题）
- **学生端没有「已取消」** — 任务被学院取消/终止（`app/dorm/end`、`reStart`、`stopFrequency`、`dayDetailDetele` 这些辅导员侧接口）对学生侧不成档，历史里表现为不出现或落进未签到

## 1. 定位与受众

本 doc 描述 `feature/checkin/` 的职责边界：

- 查寝签到任务的列表浏览、详情查看、拍照上传、定位签到完整流程
- 不覆盖其他类型的签到（如 AI 课堂的数字码签到）

适用读者：

- 要改签到流程、加新字段、或调定位策略的人
- 要排查"上传照片后找不到 URL"或"签到提交返回 -100"的人
- 要理解查寝数据如何被首页消费的人

## 2. 结构与交互

### 2.1 三页式：列表页 + 详情页 + 历史页

```
CheckinListScreen    → CheckinViewModel      → CheckinRepository → CheckinApi
CheckinDetailScreen  → CheckinDetailViewModel → CheckinRepository → CheckinApi
                                               → FileUploadApi
                                               → LocationProvider
CheckinHistoryScreen → CheckinHistoryViewModel → CheckinRepository → CheckinApi（历史两个接口）
```

三页各自有独立的 ViewModel：
- `CheckinViewModel` — 管理列表分页加载
- `CheckinDetailViewModel` — 管理详情、定位、拍照、提交
- `CheckinHistoryViewModel` — 管理「当前月份」与历史加载，翻页即换月

### 2.2 CheckinApi 包含五个接口 + 一个上传接口

锚点：`feature/checkin/data/CheckinApi.kt:13-56`

| 接口 | 方法 | 用途 |
|---|---|---|
| `pageStudentSignIn` | POST | 分页查询签到任务列表 |
| `pageStudentStatics` | POST | 查寝历史（按月 + 状态档） |
| `collectionStudentStatics` | POST | 某学生某月的查寝汇总数字 |
| `getTaskInfoByDateId` | POST | 获取任务详情（含签到范围/位置） |
| `signIn` | POST | 提交签到 |
| `FileUploadApi.uploadFile` | POST Multipart | 照片上传 |

所有查寝接口使用 JSON body 格式，认证通过 `AuthInterceptor` 注入（与主站 token 体系共享，但需要额外的 header 字段）。

### 2.3 分页加载

锚点：`feature/checkin/data/CheckinRepository.kt:40-73`

`CheckinRepository.getTasks()` 使用 `pageNum` / `pageSize` 分页，固定 `type=1`、`taskMajorType=3`。

`CheckinViewModel` 管理分页状态：
- 首次加载 `loadInitial()` → page 1
- `loadMore()` → 追加下一页
- `hasMore` 根据返回列表大小是否 ≥ pageSize（10）判断
- `isLoadingMore` 防重复加载

列表数据映射时，`taskDateId` 从多个可能字段中取第一个非空值（`row.taskDateId ?: row.id ?: row.userTaskId ?: row.taskId`），因为后端在不同场景下返回的字段名不一致。

### 2.4 详情页集成定位 + 拍照 + 提交

锚点：`feature/checkin/ui/CheckinDetailViewModel.kt:42-164`

详情页的执行流程：

1. **加载详情** — `getTaskDetail(taskDateId)` 获取签到位置范围、是否需要拍照等信息
2. **获取定位** — 先尝试 `getLastKnownLocation()` 快速定位，失败则订阅 `locationUpdates()` 取首次结果
3. **坐标转换** — `CoordinateConverter.wgs84ToBd09()` 将原生 WGS-84 转为 BD-09
4. **拍照（可选）** — 根据 `detail.needPhoto` 决定是否需要拍照
5. **提交签到** — 先上传照片（如需）→ 提交 `taskDateId + photoUrl + place + latLng + outSignin`

定位的两种策略：
- 快速路径：`locationProvider.getLastKnownLocation()`（可能为 null）
- 持续路径：`locationProvider.locationUpdates(2000ms, 0m)` 取第一个有效值后取消订阅

### 2.5 照片上传的启发式 URL 提取

锚点：`feature/checkin/data/CheckinRepository.kt:248-273`

`FileUploadApi.uploadFile()` 返回的 `data` 字段类型为 `JsonElement?`（可能是字符串或 JSON 对象），`extractUploadUrl()` 按优先级尝试：
1. JSON 基本类型 → 直接作为 URL
2. JSON 对象 → 搜索 `url` / `fileUrl` / `fullPath` / `path` / `filePath` 等常见键
3. 遍历所有字段值，查找以 `http://` 或 `https://` 开头或包含 `/group` 的字符串

这是对后端不一致响应结构的防御性处理。

### 2.6 列表页同时承载 fdygl 域同源任务（节假日 / 未来返校登记）

`CheckinListScreen` 不是纯查寝列表，而是 fdygl 域（`fdygl.aiit.edu.cn`）同源任务的统一入口：

- 查寝任务（`tasks: List<CheckinTask>`）— 主流程，§2.1–2.5 覆盖
- 节假日离返校登记任务（`holidayTasks: List<HolidayTask>` + `holidayError: String?`）— 寄生 section，数据来自 `feature/holiday/data/HolidayRepository`

两组数据由 `CheckinViewModel` 在 `init` 中并发触发（`loadInitial()` + `loadHolidays()`）但状态彼此隔离——查寝主流程的 `error` / `isLoading` 不被节假日加载影响；节假日加载失败只在 section 顶部显示错误行，可独立重试（`retryHoliday()`）不触发主流程刷新。`CheckinListScreen` 用两个 LazyColumn section 渲染，分别走 `TaskCard` / `HolidayTaskCard`，section header 区分。

节假日除"列表页入口"外完全独立——独立 Api / Repository / 路由 / ViewModel / 表单页。复用查寝模块的只有：

- `@CheckinRetrofit`（同 fdygl 域，没必要新建实例）
- `AuthInterceptor` 的 fdygl 档（多 header 鉴权对节假日同样有效）
- 列表页本身（作为入口寄生）

`HolidayApi` 由 `HolidayModule` 通过 `@CheckinRetrofit` 提供，`HolidayRepository` 直接注入 API，不在构造函数内自行 `retrofit.create()`。这样节假日和查寝同源接口共用同一个 fdygl 网络栈，同时保持 Repository 只依赖业务 API。

这种"主任务 + 同域寄生 section"模式有意为后续同 fdygl 域扩展（如返校登记、其他学工在线任务）预留——新增同类任务时沿用 `holidayTasks` / `holidayError` / `retryHoliday` 这套字段命名与并发加载风格，不必单独建一级导航或独立 tab。

锚点：`feature/checkin/ui/CheckinViewModel.kt:14-26,38-42,66-87,113-115` / `feature/checkin/ui/CheckinListScreen.kt:161-178` / `feature/holiday/data/HolidayRepository.kt:22-25,204-209` / `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md`。

### 2.7 历史页：按月 + 状态档的扇出查询

入口是列表页顶栏右上角的历史图标（`CheckinListScreen.kt:81` → `Routes.CHECKIN_HISTORY` → `NavGraph.kt:236`），不新增一级 tab。

`CheckinRepository.getMonthHistory(month)` 一次调用打 5 个请求（`coroutineScope` 内并行）：

- 1 × `collectionStudentStatics` → 月度汇总（`signiningNum` 是「本月应查寝 N 次」，其余是各档次数）
- 4 × `pageStudentStatics`，每个 `CheckinRecordStatus` 一档，`executionedStatus` 取该档的 `code`

这么打是因为服务端的行为决定的：历史行是任务实体的投影，`executionedStatus` / `executionedStatusTxt` 在行里回 `null`，只有 `taskDateStr`（`MM-dd`）、`title`、`id`、`signinPlace`、`signinPhoto` 有值——**状态只能由查询条件确定**，官方 H5 的历史页正是用五个签分别查的。

因此：状态由 `fetchStatics` 在映射时按查询档位写进 `CheckinHistoryRecord.status`，不靠响应推断。`getTasks()` 用的 `pageStudentSignIn` 与历史无关——实测它对 `type` / `taskMajorType` / 日期区间 / 各种状态键全部无感，永远只回「当前可操作的那一条」。

翻页策略：一个月最多 31 条记录，`pageSize=50` 一次取完，历史页不做无限滚动，`CheckinHistoryViewModel` 的「分页」就是换月份（`previousMonth` / `nextMonth`，未来月份禁止）。快速连点换月时只认最后一次请求的结果。

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `CheckinTask` | id / taskName / taskDateId / isSigned / startTime / endTime | `CheckinModels.kt:6-13` |
| `TaskDetail` | taskName / taskDateId / needPhoto / isSigned / signinPlace / locationRange / centerLng / centerLat / address | `CheckinModels.kt:18-30` |
| `CheckinUiState` | tasks / holidayTasks / isLoading / isLoadingHoliday / isLoadingMore / error / holidayError / hasMore / currentPage | `CheckinViewModel.kt:16-26` |
| `CheckinRecordStatus` | code + label 四档：`1`已签到 / `3`已请假 / `0`未签到 / `4`登记离校 | `CheckinModels.kt:76-91` |
| `CheckinHistoryRecord` | id / taskName / dateLabel / time / place / hasPhoto / status | `CheckinModels.kt:93-102` |
| `CheckinMonthSummary` | dueCount（本月应查寝次数）+ 各档 counts | `CheckinModels.kt:104-107` |
| `CheckinMonthHistory` | records + summary，`getMonthHistory` 的返回体 | `CheckinModels.kt:109-112` |
| `CheckinHistoryUiState` | monthLabel / records / summary / isLoading / canGoNext / error | `CheckinHistoryViewModel.kt:17-24` |
| `CheckinDetailUiState` | detail / bdLng/bdLat / locationStatus / photoUri / isSubmitting / submitSuccess / submitError | `CheckinDetailViewModel.kt:19-36` |

### 3.2 首页对查寝数据的消费

`feature/home/` 通过 `CheckinRepository` 获取待签到任务列表：
- `HomeBootstrap` 在冷启动时预取查寝数据
- `SceneResolver` 在 22:00 后未签到时可触发 `EveningCheckin` 场景
- `HomeDashboard` 在开始时间前后 4 小时窗口内显示查寝卡片

## 4. 关键决策

- **taskDateId 从多字段提取** —— 后端不同接口返回的 ID 字段名不一致，Repository 层做兼容。来源：`CheckinRepository.kt:59`。
- **定位策略：先取缓存再订阅** —— `getLastKnownLocation()` 快速返回，避免每次冷启动都等待 GPS 锁定。来源：`CheckinDetailViewModel.kt:82-101`。
- **坐标转换在客户端完成** —— 原生定位得到 WGS-84，上传前转为 BD-09，不依赖地图 SDK。来源：`CheckinDetailViewModel.kt:103-111`、`core/location/CoordinateConverter.kt`。
- **多 header 鉴权** —— 查寝接口需要 accessToken / access_token / userCode / xh / userType / X-Requested-With / Origin / Referer 共 8 个字段同时携带。来源：`compound/2026-04-22-decision-checkin-multi-header-auth.md`。
- **照片上传结果启发式解析** —— 后端返回结构不固定，不做严格类型匹配。来源：`CheckinRepository.kt:248-273`。
- **查寝列表页作为 fdygl 域同源任务的寄生入口** —— 节假日不另开一级导航或独立 tab，CheckinListScreen 双 section 同时渲染查寝与节假日；两套数据加载、错误、重试各自独立。同模式预留给未来同源任务（如返校登记）。来源：`feature/checkin/ui/CheckinViewModel.kt:38-42` / `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md` §1。
- **历史走 `pageStudentStatics`，不给 `pageStudentSignIn` 加参数** —— 实测 `pageStudentSignIn` 对 `type`（0/1/2/3/4）、`taskMajorType`、日期区间、`isHistory` 等 20+ 组合全部无感，永远只回当前可操作的那一条；历史是另一个接口。来源：`feature/checkin/data/CheckinApi.kt:22-31`。
- **接口与参数从官方 H5 反查而不是猜** —— `https://fdygl.aiit.edu.cn/swp-app/` 的 `app.js` 里带完整路由表与懒加载分片哈希表，历史记录页（`/studentEvent/bedCheck/bedCheckDetail/studentStatistics`，title「查寝历史」）所在分片 `chunk-34e2e70e` 内嵌了 fdygl 的 dorm 接口全表（`"228e"` 模块）与请求构造代码。四个状态档的中文文案、`createBy` 必填、`staticDate=yyyy-MM` 都抄自那里。见 `compound/2026-10-19-explore-checkin-h5-protocol-evidence.md`。
- **状态写在查询条件里，不写在响应里** —— 历史行的 `executionedStatus` / `executionedStatusTxt` 实测回 `null`，所以按档分 4 次查、映射时用查询档位补 `status`，而不是解析响应猜状态。来源：`feature/checkin/data/CheckinRepository.kt:111-131`。
- **历史不做无限滚动** —— 单月记录数上限就是天数，`pageSize=50` 一次取完；翻页语义让位给换月，避免和「按月查」的两套心智混在一起。来源：`feature/checkin/ui/CheckinHistoryViewModel.kt:40-52`。

## 5. 代码锚点

- `feature/checkin/data/CheckinApi.kt:13-56` — 查寝五个接口 + 上传接口
- `feature/checkin/data/CheckinRepository.kt:40-73` — 待签到列表分页
- `feature/checkin/data/CheckinRepository.kt:77-139` — 历史按月扇出（`getMonthHistory` / `fetchStatics` / `monthBody`）
- `feature/checkin/data/CheckinRepository.kt:141-179` — 详情获取
- `feature/checkin/data/CheckinRepository.kt:183-200,248-273` — 照片上传 + 启发式 URL 提取
- `feature/checkin/data/CheckinRepository.kt:202-228` — 签到提交
- `feature/checkin/data/CheckinResponse.kt:50-87` — 历史两个接口的响应模型
- `feature/checkin/domain/CheckinModels.kt:6-30` — CheckinTask / TaskDetail
- `feature/checkin/domain/CheckinModels.kt:76-122` — CheckinRecordStatus / CheckinHistoryRecord / 月度汇总 / 排序 / 月份键
- `feature/checkin/ui/CheckinViewModel.kt:24-92` — 列表分页状态管理
- `feature/checkin/ui/CheckinHistoryViewModel.kt:27-84` — 历史按月加载与换月
- `feature/checkin/ui/CheckinHistoryScreen.kt:48-96` — 历史页（月份切换 + 汇总卡 + 列表）
- `feature/checkin/ui/CheckinListScreen.kt:76-90` — 顶栏历史入口图标
- `feature/checkin/ui/CheckinDetailViewModel.kt:42-164` — 详情 + 定位 + 提交
- `navigation/NavGraph.kt:229,236-238` / `navigation/Routes.kt:13` — 历史路由
- `core/location/CoordinateConverter.kt` — WGS-84 → BD-09 转换

## 6. 已知约束 / 边界情况

- **查寝接口使用 JSON body，而非 form-urlencoded** —— 与其他模块（如 login、schedule）不同。来源：`CheckinApi.kt:17-32`。
- **outSignin 固定为 "0"** —— 不支持"校外签到"场景，超出范围无法提交。来源：`CheckinRepository.kt:126`。
- **定位超时处理不明确** —— `locationUpdates()` 没有超时机制，若无法定位会持续阻塞。当前依赖用户在 UI 层的交互触发。
- **照片上传后没有预览确认** —— 选择照片后直接上传，没有让用户确认的中间步骤。
- **分页使用 `type=1`、`taskMajorType=3` 硬编码** —— 若后端调整类型值需要同步修改。注意 `pageStudentSignIn` 的 `type` 实测被服务端忽略（改成任何值都回同一批），写死只是与官方请求保持一致，不承担筛选语义。
- **学生端历史没有「已取消」档** —— 需求里的"被取消"在服务端只存在于辅导员侧的任务生命周期（`end` / `reStart` / `stopFrequency` / `dayDetailDetele`），学生侧四档里没有对应值；被取消的任务对学生就是「不出现」。历史页因此呈现 已签到 / 已请假 / 未签到 / 登记离校。
- **历史一次 4+1 个请求** —— 状态档必须分开查（响应不回状态），会话被顶时任意一档失败即整次失败，不做部分成功渲染。
- **`createBy` 只能填自己** —— 实测同域还有 `queryStudentPage`（`createBy` 传自己学号会返回别人的名单），本项目不使用该接口；新增同源接口前要确认它是不是辅导员侧的越权视图。
- **历史行的时间/地点可能是空** —— 投影里 `timedStartTime`、`signinPlace` 只有部分档有值，`metaLine()` 靠过滤空串拼接，不能假设有值。
- **fdygl 域同源任务寄生时不跟查寝主流程互相影响** —— 节假日（以及未来同模式扩展）的加载、错误、重试各自走独立字段（`holidayTasks` / `holidayError` / `retryHoliday`）。新增同源任务时沿用这套命名，不要塞进查寝主 `error` / `tasks` 通道，否则一处失败会污染另一处的可用性。

## 7. 相关文档

- 网络层多域名 + 多 header 鉴权：`codestable/architecture/network-overview.md`
- 查寝多 header 鉴权决策：`codestable/compound/2026-04-22-decision-checkin-multi-header-auth.md`
- 首页消费查寝数据：`codestable/architecture/home-overview.md` §2.2 / §2.4 / §4.4
- 节假日离返校登记搭载在查寝列表页：`CheckinViewModel` 同时持有 `tasks / holidayTasks / holidayError`，`CheckinListScreen` 双 section 渲染。详见 `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md`
- 架构总入口：`codestable/architecture/DESIGN.md`
- 历史接口与参数从何而来（官方 H5 反查的过程与约束）：`codestable/compound/2026-10-19-explore-checkin-h5-protocol-evidence.md`
