---
doc_type: exploration
category: protocol
date: 2026-10-19
slug: checkin-h5-protocol-evidence
status: current
area: 查寝签到 / fdygl 域
tags: [checkin, fdygl, h5, protocol-evidence, sign-in-history, endpoint-discovery]
---

## 为什么值得单独记

「查寝历史」这个需求卡住的不是代码，是**协议里根本没有历史接口**：本项目从抓包学到的 `app/dorm` 只有 `pageStudentSignIn` / `getTaskInfoByDateId` / `signIn` 三个，而 `HAR/` 与 `docs/接口分析/` 都不在当前工作副本里（`.gitignore` 主动排除），无从复核。

对 `pageStudentSignIn` 做了 20+ 组参数实测（`type=0/1/2/3/4`、去掉 `type`、`taskMajorType=1/2/3/4`、日期区间、`isHistory`/`signinStatus`/`executionedStatus`/`status`/`state`/`taskStatus`），**全部返回同一批数据** —— 这个接口只有「当前你能操作的那一条」一种行为，`type` 之类的键服务端压根不看。继续在它身上试参数是死路。

同 `2026-10-13-explore-sports-h5-protocol-evidence.md` 的路子：查寝模块所在的 fdygl 也是 H5，JS 明文由服务端直接下发，**不需要登录、不需要凭证**。

## 取证方法

```
curl https://fdygl.aiit.edu.cn/swp-app/                  # 入口 HTML，只列 3 个首屏 chunk
# app.<hash>.js 里有 webpack runtime：
#   1) {"chunk-<id>":"<hash>"} 映射表  → 还原懒加载分片的真实文件名
#   2) path:"/studentEvent/...",name:"...",meta:{title:"..."},component:()=>t.e("chunk-<id>")  → 完整路由表
# 先查路由表定位页面，再只下那两个分片 grep，不要整站抓
```

本轮只下载了两个分片（`chunk-511130d2` 历史记录页 / `chunk-34e2e70e` 查寝历史页，共约 20KB），全程只读、不执行其中任何代码。

`chunk-34e2e70e` 内嵌了 fdygl 的 dorm 接口全表（webpack 模块 `"228e"`），一共 40 个 `app/dorm/*` 与 `app/dormTaskForward/*` 接口，本项目只用到其中 5 个。

## 结论

### 1. 历史接口是 `pageStudentStatics`，不是 `pageStudentSignIn`

官方路由 `/studentEvent/bedCheck/bedCheckDetail/studentStatistics`（`meta.title` = 「查寝历史」）就是学生侧历史页，请求体：

```json
{"pageNum":1,"pageSize":20,"taskMajorType":"3","createBy":"<学号>","staticDate":"yyyy-MM","executionedStatus":"<状态码>"}
```

实测（2026-10-19，有效会话）：`flag:true`、`rows` 有值；`createBy` 缺了直接 `-100 用户编码不能为空`，**必须显式传学号**，不像 `pageStudentSignIn` 那样从鉴权头里推。`staticDate` 是月份键，跨年用 `2027-01` 这种形式。

配套 `collectionStudentStatics` 回月度汇总：`{signinedNum, leaveNum, notSigninedNum, leaveSchoolNum, qrCodeNum, signiningNum}`，其中 `signiningNum` 官方文案是「本月应查寝 N 次」。

### 2. 状态码含义由官方页面的五个签给出，不是猜的

```js
cellList:[{title:"已签到",executionedStatus:"1"},{title:"已请假",executionedStatus:"3"},
          {title:"未签到",executionedStatus:"0"},{title:"登记离校",executionedStatus:"4"},
          {title:"二维码查寝负责人次数",executionedStatus:"5"}]
```

实测与之一致：`executionedStatus:"1"` 回 0 行（该账号当月还没签过），`"0"` 回今天那条。`"5"` 是辅导员侧口径，学生端不建模。

**关键点**：请求按档筛选后，响应行里的 `executionedStatus` / `executionedStatusTxt` **回的是 null** —— 行是任务实体的投影。所以状态不能从响应解析，只能「按档查询 → 用查询条件给行贴状态」，这就是 `getMonthHistory()` 打 4 次请求的原因。

### 3. 需求里的「被取消」在学生侧不存在

`app/dorm` 全表里，取消/终止类动作都在辅导员侧：`end`、`reStart`、`stopFrequency`、`delete`、`dayDetailDetele`。学生端四档没有「已取消」，被取消的任务对学生就是不出现或落进未签到。界面因此呈现 已签到 / 已请假 / 未签到 / 登记离校。

### 4. `pageStudentSignIn` 的 `type` / `taskForward` 只是抄格式，不承担筛选

H5 的历史记录页（`/studentEvent/bedCheck/history`，辅导员侧「查寝管理」）里能看到这样的构造：

```js
t.type="1"; t.taskForward="1";
"2"===this.typeStr&&(t.taskForward="2");
"3"===this.typeStr&&(t.type="2", t.taskForward="");
```

即 `typeStr=3` 会发 `{type:"2",taskForward:""}`。实测这三个组合（外加不带任何键）在服务端返回**完全相同**的一行 —— 所以它们是给辅导员侧列表用的、当前账号无差别被忽略的参数。**不要**把它们当历史开关接到 `getTasks()` 上。

### 5. 顺手确认的两件事

- `getTaskInfoByDateId` 的 `dateId` 是**行 id（雪花号）**，不是日期串：传 `2026-10-18` 回 `-100 任务不存在，请重试`（鉴权已过，是查不到）。
- 同表里的 `queryStudentPage` 用**自己**的学号当 `createBy` 会返回**别的学生**的名单行 —— 那是辅导员侧的越权视图，本项目不调用。要新增同源接口前先确认它属于哪一侧。

## 复用这套取证路径的注意点

1. 入口 HTML 只给首屏 chunk，懒加载页面必须从 `app.js` 的分片哈希表还原文件名，否则搜不到。
2. 页面组件常用 `defineApiData` / props 把请求构造下放到通用列表组件，**接口路径不在这个分片里**，要顺着 webpack 模块 id 找到内嵌 API 表的那个分片（本轮是 `"228e"`）。
3. 只下目标分片，不要整站拉取；文件只读、只 grep，不 require / 不执行。
4. 拿到的参数组合仍要用真实会话实测一遍再落地 —— 本轮就是实测推翻了「`type` 是历史开关」这个从代码里读出来的直觉。

## 相关

- 代码落点：`feature/checkin/data/CheckinApi.kt:22-31`、`feature/checkin/data/CheckinRepository.kt:77-139`、`feature/checkin/domain/CheckinModels.kt:76-122`
- 模块文档：`codestable/architecture/checkin-overview.md` §0 / §2.7 / §4 / §6
- 同一取证路径的前例：`codestable/compound/2026-10-13-explore-sports-h5-protocol-evidence.md`
- fdygl 鉴权与 `flag` 语义：`codestable/compound/2026-04-22-decision-checkin-multi-header-auth.md`
