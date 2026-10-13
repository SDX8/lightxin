---
doc_type: architecture
slug: schedule-overview
scope: feature/schedule/ 的周课表获取、三态周选择器、动态节次课程网格渲染、断网本地快照回退，以及与首页 SceneResolver 的共享边界
summary: schedule 以 ScheduleRepository 封装周次信息与课表数据并 write-through 到 JSON 快照，断网/接口异常时回退读快照并在 UI 标注快照时间；UI 层用 LazyRow 胶囊周选择器 + 支持第 0 节的绝对定位网格渲染 7 天课程，详情走 ModalBottomSheet
status: current
last_reviewed: 2026-10-12
tags: [schedule, course, timetable, week-selector, grid, section-zero, offline-cache]
depends_on: [network-overview, home-overview]
---

# 课表模块总览

## 0. 术语

- **三态周选择器** — 周次胶囊支持三种视觉状态：普通、当前周、选中周；选中周填充陶土色，当前周有边框，普通周透明
- **周次信息** — `WeekInfo` 包含当前周、总周数、学年、学期，由 `getWeekList` 接口返回
- **动态节次范围** — 常规课表渲染 1-10 节；当任一课程 `startSection <= 0` 时，网格从第 0 节开始渲染，并把第 0 节标签显示为“早”

## 1. 定位与受众

本 doc 描述 `feature/schedule/` 的职责边界：

- 从后端获取周次列表与课表数据，在本地映射为 `Course` 领域模型
- 渲染周课表网格 + 周次选择器 + 课程详情弹窗
- 作为数据源被 `feature/home/` 的 `SceneResolver` 消费（课程数据由 `ScheduleRepository` 暴露）

适用读者：

- 要改课表网格布局、周选择器交互、或课程详情展示的人
- 要理解课表数据如何被首页场景引擎消费的人
- 要排查"课表为什么显示不对"或"周次切换后数据没更新"的人

## 2. 结构与交互

### 2.1 三层结构

```
ScheduleApi (data)         → POST dict/selectZcList.do
                              POST mobile/selectStuSelfTimeTable.do
ScheduleRepository (data)  → 周次 → 课表 两段式加载
ScheduleViewModel (ui)     → 周次 → 课表状态机
ScheduleScreen (ui)        → 周选择器 + 网格 + 详情弹窗
```

### 2.2 两段式加载：先拿周次，再拿课表

锚点：`feature/schedule/data/ScheduleRepository.kt:24-123`

`ScheduleViewModel` init 时：

1. `loadWeekInfo()` — 调用 `repository.getWeekInfo()`
2. 成功后自动调用 `loadCourses(schoolYear, schoolTerm, currentWeek)`
3. 用户切周时调用 `onWeekSelected(week)` → 再次 `loadCourses()`

这种两段式设计的原因是后端将周次信息与课表数据分离在两个接口中，且课表查询依赖周次接口返回的学年/学期参数。

### 2.3 课表按天分组，课程块绝对定位

锚点：`feature/schedule/data/ScheduleRepository.kt:74-101`

`ScheduleRepository.getCourses()` 将后端返回的平铺数据结构映射为 `Course` 列表：
- 按 `dayOfWeek`（1-7）区分七天
- 每门课记录 `startSection` / `endSection`，用于网格中计算高度与偏移

UI 层用 `CELL_HEIGHT = 58.dp` 为基准，先根据课程数据计算 `firstSection`：没有第 0 节课程时为 1；存在 `startSection <= 0` 时为 0。课程块按 `top = (startSection - firstSection) * CELL_HEIGHT` 绝对定位，避免第 0 节课程产生负偏移或崩溃。

锚点：`feature/schedule/ui/ScheduleScreen.kt:282-415`。

### 2.4 三态周选择器

锚点：`feature/schedule/ui/ScheduleScreen.kt:183-277`

`WeekSelector` 使用 `LazyRow` 渲染周次胶囊，支持：
- **选中态**（`isSelected`）— 陶土色填充，白字
- **当前周态**（`isCurrent`）— 灰边框，深灰字
- **普通态** — 透明底，浅灰字

三态组合实际产生四种视觉结果（选中+当前周时选中优先）。

`LazyRow` 在选中周变化时自动 `animateScrollToItem(selectedWeek - 2)`，让选中周保持在可视区中央偏左位置。同时通过 `derivedStateOf` 计算"本周"指示器（`← 本周` / `本周 →`），在当前周不在可视区时显示方向提示。

### 2.5 课程详情走 ModalBottomSheet

锚点：`feature/schedule/ui/ScheduleScreen.kt:130-140`

点击课程块弹起 `ModalBottomSheet`，展示课程名（Newsreader 衬线字）、时间、教室、教师信息。非全屏弹窗，`sheetState` 使用默认值。

### 2.6 课程颜色基于名称 hash

锚点：`feature/schedule/ui/ScheduleScreen.kt:79-82`

`courseColor(name)` 通过 `name.hashCode()` 从 `LxCategoryColors` 取色，保证同一课程名在不同周次渲染同一颜色。这是纯 UI 层约定，不与后端返回的颜色字段关联。

### 2.7 首页对课表的消费

`feature/home/` 通过 `ScheduleRepository` 获取今日课程列表，在 `SceneResolver.resolve()` 中判断当前场景。课表数据进入 splash 预加载阶段（`HomeBootstrap`），与查寝数据一同在冷启动时预取。

### 2.8 断网本地快照回退

锚点：`feature/schedule/data/ScheduleCacheStore.kt:48-128`、`feature/schedule/data/ScheduleRepository.kt:24-133`

课表两个接口都是 POST 表单，OkHttp 的 HTTP 缓存不会命中，所以离线能力落在应用层：

- **落地形态** —— 单 JSON 文件 `filesDir/schedule_cache.json` + `Mutex`，与 `RouteTemplateStore` 同构（`.tmp` 写完 rename 的原子写、`runCatching` 兜住损坏文件、首次读盘后驻内存）。
- **两类快照** —— `CachedWeekInfo`（按 `userCode` 分键）与 `CachedCourseWeek`（按 `userCode#学年#学期#周次` 分键）。周次信息必须一起缓存，否则两段式加载在断网时第一段就失败，第二段不会被调用。
- **读写时机** —— `ScheduleRepository` 网络成功后 write-through；catch 到任何异常时先查快照，命中就返回 `Result.success` 并带上 `cachedAtMillis`，未命中才返回原来的错误文案。因此 `HomeBootstrap`、`HomeViewModel.loadTodayCourses()`、课程倒计时通知都自动获得离线能力，无需各自改动。
- **裁剪** —— 每次写课程快照时把缓存收敛到「当前账号 + 当前学期」，文件体积受上限（一学期二十余周），且换账号后读不到上一位使用者的课表。
- **UI 表达** —— `ScheduleUiState.cachedAtMillis` 非空时，网格上方出现一条琥珀色提示「显示 M月d日 HH:mm 的课表快照（未能刷新）」。措辞刻意不写"无网络"，因为回退原因也可能是登录失效或服务端 5xx。颜色用 `LxAmberSoft`/`LxWarning` 而非陶土色，遵守单屏陶土色使用密度准则。

完整理由与被排除的方案见 `codestable/compound/2026-10-12-decision-schedule-offline-snapshot.md`。

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `WeekInfo` | currentWeek / totalWeeks / schoolYear / schoolTerm / cachedAtMillis | `ScheduleModels.kt:3-10` |
| `Course` | name / startSection / endSection / room / teacher / dayOfWeek | `ScheduleModels.kt:12-19` |
| `ScheduleData` | courses / weekDates / cachedAtMillis | `ScheduleModels.kt:24-29` |
| `ScheduleUiState` | weekInfo / courses / weekDates / selectedWeek / isLoading / error / cachedAtMillis | `ScheduleViewModel.kt:15-24` |
| `CachedWeekInfo` / `CachedCourseWeek` | 落盘的快照形态；weekDates 用 String 键规避 Gson 对非 String 键的不稳定行为 | `ScheduleCacheStore.kt:18-42` |

`cachedAtMillis` 为 null 表示数据来自实时网络，非 null 表示来自本地快照；它同时是 UI 提示条的显示条件。

### 3.2 状态所有权

- `ScheduleUiState` 由 `ScheduleViewModel` 持有
- `selectedWeek` 是 ViewModel 内部状态，用户切换时触发重新加载，不持久化
- `WeekInfo` 在 ViewModel init 时加载一次，后续不再刷新

## 4. 关键决策

- **课程块用绝对定位而非 LazyColumn** —— 网格按 10 节固定高度渲染，不虚拟化。因为课表数据量小（最多 7 天 × 每天数门课），绝对定位更直观。
- **课程颜色用 name.hashCode() 取色** —— 确保同一课程颜色稳定，不与后端绑定。来源：`ScheduleScreen.kt:79-82`。
- **离线走应用层 JSON 快照，不走 OkHttp HTTP 缓存 / Room** —— 课表接口全是 POST 表单，HTTP 缓存不命中；存储形态沿用"轻量资产用 JSON 文件 + Mutex"的既有决定。来源：`codestable/compound/2026-10-12-decision-schedule-offline-snapshot.md`。
- **课表数据进入 splash 预加载** —— 首页冷启动时 `HomeBootstrap` 预取课表数据，让首屏可直接展示。来源：`feature/home/domain/HomeBootstrap.kt:54-68`。
- **课表不走独立 NavHost 路由** —— 课表是首页三 tab 的第二个标签，由 `HomeScreen` 内部管理；当前 `Routes.kt` 不再定义独立 `SCHEDULE` 常量。

## 5. 代码锚点

- `feature/schedule/data/ScheduleApi.kt:9-23` — 周次与课表两个接口
- `feature/schedule/data/ScheduleRepository.kt:24-133` — 两段式加载主链 + 快照 write-through / 失败回退
- `feature/schedule/data/ScheduleCacheStore.kt:48-163` — JSON 快照读写、key 构造与按学期裁剪
- `feature/schedule/data/ScheduleResponse.kt:8-46` — 响应模型
- `feature/schedule/domain/ScheduleModels.kt:3-29` — WeekInfo / Course / ScheduleData
- `feature/schedule/ui/ScheduleViewModel.kt:24-93` — 状态管理与加载
- `feature/schedule/ui/ScheduleScreen.kt:86-450` — 周选择器 + 网格 + 离线提示条 + 详情
- `app/src/test/java/com/lightxin/feature/schedule/data/ScheduleCacheStoreTest.kt` — key 构造 / 裁剪 / Gson 往返
- `feature/home/domain/SceneResolver.kt` — 首页场景引擎消费课表

## 6. 已知约束 / 边界情况

- **离线能力受"曾在线加载过"约束** —— 快照只在网络成功后写入。安装后从未联网进过课表则断网仍无数据；离线切到从未在线浏览过的周次仍是整屏错误页。
- **离线时当前周可能偏旧** —— `currentWeek` 同样取自快照，隔天/隔周断网打开时"今天"高亮与首页场景判断可能落在旧周次上，由提示条的快照时间向用户说明新鲜度。
- **快照按 `userCode` 分键并裁剪到当前学期** —— 换账号后读不到上一位使用者的课表；退出登录不主动删盘上文件（键含 userCode，不构成跨账号可读）。
- **最大节次仍按 10 节处理** —— `SECTION_COUNT = 10` 仍是网格最大常量；常规显示 1-10，遇到第 0 节课程时显示 0-10（第 0 节标签为“早”）。来源：`ScheduleScreen.kt:61,238-320`。
- **第 0 节只影响课表页网格起点** —— 首页课程时间判断仍由 `feature/home/domain/SectionSchedule.kt` 维护，不从课表 UI 的动态 `firstSection` 反推。
- **课程详情中的 `LxDetailRow` 使用固定 48dp labelWidth** —— 适用于当前字段长度，若新增字段需要调整。
- **`selectedWeek` 在 ViewModel 重建时回到 init 值** —— 不持久化，进程重建后从当前周开始。

## 7. 相关文档

- 首页场景引擎：`codestable/architecture/home-overview.md`
- 网络层：`codestable/architecture/network-overview.md`
- 首页启动预加载：`codestable/architecture/home-overview.md` §2.2
- 架构总入口：`codestable/architecture/DESIGN.md`
