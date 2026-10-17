---
doc_type: architecture
slug: todo-overview
scope: feature/todo/ 的待办数据模型、本地 JSON 落盘、三段列表与增删改交互
summary: todo 是一个纯本地的待办清单（作业 / 事务），条目 = 标题 + 可选科目 + 截止日，落在 filesDir/todo_items.json；列表分「今天（含逾期）/ 以后 / 已完成」三段，挂在底栏第 3 个 tab；不引 Room、不做通知、不接任何服务端
status: current
last_reviewed: 2026-10-17
tags: [todo, task, homework, local-storage, json, bottom-tab]
depends_on: [home-overview, designsystem-overview]
---

# 待办模块总览

## 0. 术语

- **待办（Todo）** — 用户自己记的一条"要做的事"，最常见的是"今天高数布置了什么、周几要交"；也可以与课无关
- **三段列表** — 今天（逾期项折叠在最前）/ 以后 / 已完成
- **逾期** — 未完成且截止日早于今天；不单独成段，只在今天段里排到最前并染成玫瑰色

## 1. 定位与受众

本 doc 描述 `feature/todo/` 的职责边界：

- 待办的领域模型、三段划分与文案规则
- 本地 JSON 落盘（增 / 删 / 改 / 勾选 / 清空已完成）
- 底栏 tab 的挂载方式

不负责：

- 与服务端同步 —— 学校没有任何待办接口，这份清单从设计上就是纯本地
- 到期提醒 —— 见 §4 最后一条
- AI 课堂作业的自动导入 —— 见 §6

## 2. 结构与交互

### 2.1 入口是底栏第 3 个 tab，不是路由

锚点：`feature/home/ui/HomeScreen.kt:56-61`、`feature/home/ui/HomeScreen.kt:115-163`

底栏四个 tab：`首页 0 / 课程表 1 / 待办 2 / 我的 3`。待办与课表一样由 `HomeScreen` 内部的 `AnimatedContent` 切换，**没有** `Routes` 常量 —— 加一级页面走 tab，加二级页面才走导航图。`HomeDashboard` 里"去看今天的课"用的是 `onTabSelected(1)`，索引在待办之前，插 tab 不影响它。

### 2.2 一条待办 = 标题 + 科目（可空）+ 截止日

锚点：`feature/todo/domain/TodoModels.kt:11-19`

标题必填、科目可空、截止日必填（默认今天）。三者都够用来分类和排序，再往下加备注 / 优先级会让录入成本超过清单本身的价值。标题与科目都是单行输入，落盘前经 `sanitizeTodoTitle` / `sanitizeTodoSubject` 截断（80 / 16 字）—— 列表行不折行，也不会被一次粘贴灌进无穷长的内容。

### 2.3 三段划分是纯函数

锚点：`feature/todo/domain/TodoModels.kt:36-46`

`todoSections(items, today)` 一次算出三段：未完成里"截止日 ≤ 今天"进今天段（先按截止日、再按添加顺序，所以逾期自然排在最前），"截止日 > 今天"进以后段，已完成按 `doneAt` 倒序。空段整段不渲染。

逾期不单独成段是刻意的：它就是"今天还没做完"里最该先看到的那几条，拆出去反而要用户在两段之间来回找。

### 2.4 勾选态自己画

锚点：`feature/todo/ui/TodoScreen.kt:286-310`

设计系统里没有 Checkbox，行首那颗 22dp 圆点就是勾选态：未勾是沙色描边空心圆，勾上是鼠尾草底 + 对勾，标题同时加删除线并褪到 `LxInkFaint`。整行可点（进编辑），圆点单独可点（切换完成），两者互不干扰。

### 2.5 新增与编辑共用一个 BottomSheet

锚点：`feature/todo/ui/TodoScreen.kt:313-411`

`ModalBottomSheet` 里放标题、科目、截止日期与保存 / 删除 / 取消。截止日给「今天」「明天」两颗快捷 chip + 一颗「选日期」打开 Material3 `DatePickerDialog`，选中的日期由 chip 行下面那行小字念出来 —— 不把日期直接写进第三颗 chip，否则默认选"今天"时会出现两颗同名 chip。

删除走二次确认弹窗（`LxDialog` + `Destructive`），文案直说"不会留在已完成里"。

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `TodoItem` | id / title / subject / dueDate / createdAtMillis / done / doneAt | `TodoModels.kt:11-19` |
| `TodoSections` | today / later / done 三段 + `openCount` / `isEmpty` | `TodoModels.kt:27-34` |
| `TodoEntity` | 落盘形态，日期一律 ISO 字符串 | `TodoStore.kt:108-116` |
| `TodoDraft` | 编辑中的表单，id 为 null 表示新增 | `TodoViewModel.kt:20-25` |
| `TodoUiState` | items / today / sections / draft / error | `TodoViewModel.kt:27-33` |

### 3.2 状态所有权

- `TodoStore` 是 `@Singleton`，内存 `StateFlow<List<TodoItem>>` + 磁盘 `filesDir/todo_items.json` 双份；进程内所有页面看到同一份
- `TodoViewModel` 只订阅 `store.items`，每次发射重算 `today` 与 `sections`；写操作一律委托给 store
- 草稿态（`draft` / `error`）是 ViewModel 内部状态，不持久化

## 4. 关键决策

- **JSON 文件 + Mutex，不引 Room** —— 沿用路线模板的既有决定，待办的量级（几十到几百条）远没到需要 schema 迁移。来源：`codestable/compound/2026-04-22-decision-route-template-json-not-room.md`、`feature/running/data/RouteTemplateStore.kt`。
- **日期存 ISO 字符串而不是让 Gson 反射 `LocalDate`** —— Gson 会把 `LocalDate` 的内部字段写进文件，换 JDK 版本就解不回来。`TodoEntity` 因此存在。来源：`feature/todo/data/TodoStore.kt:105-118`。
- **坏日期整行丢弃而不是兜底成今天** —— 半条待办会在勾选、删除时以 null 字段回来咬人，不如直接不显示。来源：`feature/todo/data/TodoStore.kt:129-141`。
- **退出登录不清待办** —— 与早/晚自习相反：那些跟着班级走，而待办是用户自己的内容，且首页那条"重新登录"路径会顺手清 session / profile / 早自习设置，把待办一起清掉等于让用户为了挤下线而丢清单。
- **不做到期提醒** —— 项目里没有任何定时通知基础设施（只有进程内 60s 循环的课程胶囊），做提醒要引 WorkManager + 通知权限流程，那是另一个功能的量级。

## 5. 代码锚点

- `feature/todo/domain/TodoModels.kt:11-86` — 模型与全部纯函数（分段、逾期、日期文案、清洗）
- `feature/todo/data/TodoStore.kt:29-103` — 单例存储：内存 StateFlow + JSON 文件 + Mutex
- `feature/todo/data/TodoStore.kt:105-152` — 落盘形态与编解码（internal 纯函数，JVM 可测）
- `feature/todo/ui/TodoViewModel.kt:36-114` — 草稿、校验、写操作委托
- `feature/todo/ui/TodoScreen.kt:78-186` — 三段列表 + 空态 + FAB
- `feature/todo/ui/TodoScreen.kt:313-437` — 编辑 BottomSheet 与日期选择器
- `feature/home/ui/HomeScreen.kt:56-61` — 底栏四 tab 定义
- `app/src/test/java/com/lightxin/feature/todo/domain/TodoModelsTest.kt` — 分段、排序、逾期文案、清洗
- `app/src/test/java/com/lightxin/feature/todo/data/TodoStoreTest.kt` — JSON 往返、坏数据降级

## 6. 已知约束 / 边界情况

- **纯本地，换机 / 卸载即丢** —— 没有任何同步与备份；1.3.x 的发布方式（换签名 keystore 需卸载重装）会连带清掉这份清单。
- **`today` 在每次数据变化时重算** —— 列表跨零点继续开着时，分段不会自己刷新，直到下一次增删改。待办场景下可接受。
- **AI 课堂作业不自动进来** —— `feature/aiclass` 有自己的作业列表与刷新逻辑，灌进待办会带来重复条目和同步时机两个问题；先让用户手写，形态验证过再谈联动。
- **DatePicker 的星期与月份名跟随系统语言** —— 我们自己写的文案恒为中文，框架日历在英文 ROM 上会露出英文，不是代码问题。UTC 毫秒与 `LocalDate` 的换算固定在 `ZoneOffset.UTC` 两侧，跨时区不会差一天。

## 7. 相关文档

- 底栏容器与首页：`codestable/architecture/home-overview.md`
- 组件与色板：`codestable/architecture/designsystem-overview.md`
- JSON 文件存储先例：`codestable/compound/2026-04-22-decision-route-template-json-not-room.md`
- 架构总入口：`codestable/architecture/DESIGN.md`
