# 校园跑 / 模拟提交 —— 改动说明与修改指南（跨工作区交接）

> 写给另一个工作区里接手跑步功能的 agent。
> 生成时间：2026-10-17，对应 `main` = `8f76e6a`、跑步分支现状见 §5。
> 文中所有路径相对仓库根；行号是 2026-10-17 的快照，改过之后会漂，**以类名/函数名为准**。
> 所有结论都已回读代码核实；标注「实测」的是拿真机 + 官方 App 验出来的，不是推断。

## 1. 一分钟看懂：两条主链

```
真实跑步（有 GPS）
  RunningHomeScreen ─权限─▶ RunTrackingService(前台服务) ─▶ RunningTracker(StateFlow)
                                   │  LocationProvider.locationUpdates(3000ms, 2f)
                                   ▼
                            RunningSnapshot ─▶ RunningRepository.uploadSnapshot()
                                                 ├ CoordinateConverter.wgs84ToBd09（逐点）
                                                 ├ RunningEncryption.encryptUploadPayload
                                                 └ RunningApi.uploadRunningRecord(list=…)

模拟提交（无 GPS）
  RunningSimScreen ─▶ RunningViewModel.submitSimulation()
                        └─▶ RunningRepository.submitSimulation(config, overrideTrajectory)
                              ├ TrajectoryGenerator.generate()            ← 内置环线
                              │   或 generateFromTemplate() → PolylineSampler.sample()
                              ├ 同样进 uploadSnapshot()：转换 + 加密 + 上传
                              └ 模板来源 RouteTemplateStore（filesDir/route_templates.json）
```

两条链在 `uploadSnapshot()` 之后完全同路，**上传协议只有一份实现**。任何"模拟和真实不一样"的想法都要先在代码里核对。

涉及包：`feature/running/{data,domain,ui,service,exercise}`、`core/location`、`core/network`、`core/auth`、`core/settings`。

## 2. 硬约束（改之前必读，违反即返工）

| 约束 | 位置 | 违反后的现象 |
|---|---|---|
| 不引任何地图 / 定位 SDK，坐标转换手写 | `core/location/CoordinateConverter.kt` | 见 `AGENTS.md` 硬约束、`codestable/compound/2026-04-22-decision-no-map-sdk.md` |
| 上传字段**名和值都要** RSA(publicKey2) 加密 | `RunningEncryption.encryptUploadPayload` | 服务端判定无效 |
| **唯一例外**：`runningRoute` 的值走明文 | 同上，`if (key == "runningRoute") value else …` | 记录能建、里程速度都对，但官方 App 画不出轨迹并判「运动轨迹异常，无效」 |
| `publicKey`(登录) 与 `publicKey2`(跑步) 不可混用 | `core/auth/RSAUtils.kt` | 解密失败 |
| 协议字段名保持原样，含拼错的 | `RunningRepository` / `SportsGradeMapper` | 服务端不认，且无法与官方 App 对齐 |
| 上传成功要同时满足 `result=OK` **且** id 有效（非空 / 非 `0` / 非 `null`） | `RunningRepository.kt:309-321` | 把"没入账"当成功报给用户 |

## 3. 坐标系：结论、证据、改动守则

### 3.1 现在的结论

- 转换链：`CoordinateConverter.wgs84ToBd09` = WGS-84 → `wgs84ToGcj02` → `gcj02ToBd09`。`outOfChina` 的点原样返回。
- **校园中心 `RunningCampus.CENTER_LATITUDE/LONGITUDE = 31.134154 / 118.631502` 是 WGS-84**（用户提供，来源 GPX / 谷歌地球）。围栏是中心 ±600 m 两轴。
- 服务端**显示侧要 BD-09**。两条独立证据：① 官方 H5 用 `BMap` + `Convertor.translate(1→5)`；② 2026-10-14 实测——把一份**已经转成 GCJ-02** 的轨迹喂进定位、再按 WGS-84 转 BD-09 上传，结果正好朝东南偏一个 WGS→GCJ 的量（本地算 531 m，实测约 530 m）。多转的那一步就是偏移本身。
- 本地偏移量参考（写在 `CoordinateConverter` 头注释里）：WGS→GCJ ≈ 535 m（南 253 + 东 472）、GCJ→BD ≈ 897 m（东北）、WGS→BD ≈ 1165 m。

### 3.2 `isBd09` 这个标记到底管什么

- `GeneratedTrajectory(points, isBd09)`（`domain/RunningModels.kt:93-96`）。
- **两个生产者目前都写死 `isBd09 = false`**（`domain/TrajectoryGenerator.kt:46,53`）：内置环线和模板插值都是 WGS-84，与手机原始定位同源。
- 唯一读者：`RunningRepository.kt:261` 把它抄进 `RunningSnapshot.pointsAreBd09`，`:282-292` 据此决定上传前要不要逐点 `wgs84ToBd09`。`false` ⇒ 转换。
- 真实 GPS 点从来不设这个标记，默认 `false` ⇒ 一样走转换。所以今天所有轨迹上传前都是 WGS-84 → BD-09。
- 被 `TrajectoryGeneratorTest` 钉住（断言两个来源都 `isBd09 == false`）。

### 3.3 改坐标系时必须同时动的地方

1. `RunningCampus` 中心常量 + 它上面那段"坐标系已确认为 WGS-84"的注释（注释是证据链，别删）。
2. 生产轨迹的每一个来源的 `isBd09` 标记（新增来源时必须显式表态，不能靠默认值）。
3. 围栏判定 `RouteQualityChecker` / `RunningCampus` 的取点：**必须拿转换前的原始点**比（`cb5dc77` 就是修这个错配，之前拿 BD-09 点去比 WGS-84 围栏会误判出校）。
4. `core/location/CoordinateConverterTest` 的金色值与偏移带。

## 4. 上传载荷：字段与加密

`RunningEncryption.encryptUploadPayload()` 里的明文键（顺序有意义，用 `linkedMapOf`）：

```
exerciseId, memberId, runningType, startDate, endDate,
mile(km, %.2f), time(秒), speed(km/h, %.2f),
runningRoute = [{"latitude":"%.6f","longitude":"%.6f"}, …] 的 JSON 字符串,
sourceInfo = Build.MODEL
```

- 每个**键**都先 `RSAUtils.encryptSportData(key)`；每个**值**同样加密，除 `runningRoute`。
- RSA：`RSA/None/PKCS1Padding`，1024 位 ⇒ 单块明文 ≤117 B、密文 128 B；长文本分块后**字节级拼接**，整体一次 `Base64.NO_WRAP`。这就是服务端解不开多块拼接密文的原因（`runningRoute` 几 KB ⇒ 几十块）。
- 最终线格式：`JSONArray().put(encryptedObject).toString()` ⇒ `[{ "<encKey>":"<encVal>", … }]`，作为表单唯一字段 `list` 发出去。
- 端点：`POST mobile/extra/addExtraCheckNew.do`，`@SportsRetrofit`（base `http://sports.aiit.edu.cn:8082`）。**光有表单参数不够**，`AuthInterceptor` 要按 sports 域补齐身份头（含 `studentCode` / `Origin` / `Referer: …/dist/`）。
- 上传后还有一次回读：`getExtraCheck.do` + `todayKm` 差值，用来在结果页说"本次实际计入了多少"（`RunningViewModel` 的回读逻辑，跑在 `viewModelScope` 里而不是调用方的协程作用域，否则收尾会被取消 —— `e70a98d`）。

## 5. 2026-10-13 ~ 10-15 实际改了什么

| 提交 | 改了什么 | 为什么 |
|---|---|---|
| `e9158b9` | 6 处缺陷：上传可重试、模板闭环插值、录制与真实跑步双向互斥 | 上传失败即丢会话；模板折线不闭合；两种会话能并发 |
| `474a12b` | 有效速度区间改用服务端下发值；结果页显示实际计入里程 | 之前写死区间，服务端改了我们就误判 |
| `aa694c9` | HTTP BODY 日志仅 debuggable 构建开启（`NetworkModule.kt:59` 判 `FLAG_DEBUGGABLE`） | release 包不能把密文/身份写进 logcat |
| `25c7d0c` / `f3be28d` | 真实跑步上传前补质量检查；清 4 处小问题 | 质量检查后来只保留在**模板录制**路径（服务端不做围栏校验，见 §6.4） |
| `c76fc2b` / `e70a98d` / `2124ba3` | 上传后回读服务端判定；修"已存轨迹 0 个点"的假信号；围栏提示改定量 | 排查期间诊断本身在骗人 |
| `cb5dc77` | 围栏检查改用转换前的原始点 | 坐标系错配 |
| `ec049e6` → `c29e0eb` | 加了 H5 明文端点做 A/B 备用臂，随后**整体回滚** | 证明问题不在端点 |
| `6186cbc` → `d6dbe37` | 先加"轨迹字段不加密"A/B 开关做验证，验证通过后**收敛成无条件例外** | 同一份数据：加密轨迹 = 判无效，明文轨迹 = 判有效。这是结案证据 |
| `f573f71` | 校园中心换成 `31.134154 / 118.631502` | 旧常量来源不明，与新点相距约 620 m |
| `92a6f68` | 内置模拟环线明确按 WGS-84 处理、上传前转 BD-09 | 结案：偏移来自多转一步，不是环线画错 |

**排查期的 A/B 开关、备用端点、死引用已全部清干净**（`2ff2725`），代码里不再有条件分支的遗留调试臂。`isBd09` 虽然恒为 `false`，但它仍是"围栏用原始点、上传用转换后点"这套语义的载体，**不要顺手删掉**。

## 6. 常见改动怎么改（配方）

### 6.1 加 / 改一个上传字段
1. 改 `RunningUploadPayload`（`RunningEncryption.kt` 顶部）与 `plainValues` 的插入顺序。
2. 值要加密就别碰那行 `if`；只有"长字段服务端解不开"这种情况才走明文，且必须在 `encryptUploadPayload` 的 KDoc 里写清实测证据。
3. 补一个纯函数测试锁字段集合与顺序（见 §7 的缺口）。

### 6.2 换校园中心 / 改围栏大小
只改 `RunningCampus` 的常量，注释里那段证据链保留；然后跑 `RouteQualityCheckerTest` 与 `CoordinateConverterTest`。围栏比较必须用原始 WGS-84 点。

### 6.3 新增一种模拟轨迹来源
必须显式给 `GeneratedTrajectory.isBd09` 表态（该来源的点在哪个坐标系），并在 `TrajectoryGeneratorTest` 加一条断言。默认值会静默走"按 WGS-84 转换"，错坐标系从这里溜进去。

### 6.4 质量校验放哪
`RouteQualityChecker` 现在是**警告级、只挂在模板录制路径**；上传路径不做围栏判定，因为服务端根本不校验围栏，拦在本地只会误伤真实用户。要加硬门槛前先确认服务端行为，别凭直觉。

### 6.5 速度 / 里程校验
优先用服务端下发区间：`RunningDashboard.minSpeedKmh / maxSpeedKmh` ← `extraDetailInfo.do` 的 `minSpeed` / `maxSpeed`（`SportsGradeMapper`）。只有下发缺失才退回常量 `2.0 / 15.0`。改校验前先确认它该不该跟服务端走。

### 6.6 门禁（模拟提交入口）
现状：`DeveloperPrefs`（DataStore `lightxin_dev` / 键 `advanced_enabled`）+ `DeveloperPasscode.verify()`（`SHA-256(SALT + 输入)` 与常量 `PASSCODE_SHA256_HEX` 比对，明文口令不在仓库里）。口令弹窗在 `AboutScreen`，读取方是 `RunningViewModel` → `RunningHomeScreen`（隐藏「模拟提交」）和 `ProfileViewModel` → `ProfileScreen`（隐藏「路线模拟」）。

**已知边界**：这是 UI 级门禁，`NavGraph` 仍无条件注册 `RUNNING_SIM`，上传路径本身不查标志位。目标是防同学误点，不是防攻击。要变成硬门禁，得在 `submitSimulation()` 里再判一次并说明理由。

### 6.7 上传失败与会话
`RunningViewModel` 上传失败**故意不 clear session**：轨迹和 `exerciseId` 留着，回到跑步页还能重试，且时长在 `RunningTracker.stopCollecting()` 时已冻结，重试上传的是同一份数字。改这块前先想清楚"重试"这个行为要不要保留。

### 6.8 协程取消
`RunningRepository` 每个方法都先 `catch (e: CancellationException) { throw e }`，其它异常才转 `Result.failure`。新增方法必须沿用，否则取消会被当成业务失败吞掉。

## 7. 测试与已知缺口

`app/src/test/java/com/lightxin/feature/running/`：`SportsGradeMapperTest`（年级判定 + 各接口解析 + 判定形态）、`ClubDetailMapperTest`、`GeoDistanceTest`、`RouteQualityCheckerTest`、`TrajectoryGeneratorTest`（步长 / 围栏 / 时间戳 / 确定性 / **两个来源 isBd09=false**）、`RunningTrackerTest`（时长冻结、模板会话无快照）、`exercise/` 下考勤轮询与二维码三个。相关还有 `core/location/CoordinateConverterTest`、`core/settings/DeveloperPasscodeTest`。

**缺口（重要）**：`RunningEncryption.encryptUploadPayload` 与 `RSAUtils` **没有任何测试**。也就是说 `runningRoute` 明文这个例外、"键也要加密"这条规则，全靠一次实测和这段文档兜着 —— 谁做"一致性重构"把它们统一加密，测试不会红，线上记录会直接无效。要补测试就断言：① 键集合与顺序；② 除 `runningRoute` 外的值都不同于明文；③ `runningRoute` 值是可 `JSONArray()` 解析的原文。

测试栈只有 JUnit 4，没有 Mockito / Robolectric；带 `android.location.Location` 之类签名的方法在纯 JVM 测试里要绕开。

## 8. 踩过的坑（别重犯）

1. **只加密值不加密键** ⇒ 无效。反过来只加密键也不行。
2. **把 `runningRoute` 改回加密** ⇒ 记录无效且没有任何报错提示，只有官方 App 里画不出轨迹。有实测证据，别"为了统一"改回去。
3. **拿转换后的点判围栏** ⇒ 在校内被判出校。
4. **上传失败清会话** ⇒ 用户只能重跑一遍。
5. **回读放在调用方协程作用域** ⇒ 页面一切换就被取消，服务端判定从来没显示出来过。
6. **`:app:connectedDebugAndroidTest` 会先清空应用数据**（AGENTS.md 已记）：登录态、课表缓存、FIF 身份、待办全没。别拿还想保留状态的机器当仪器化测试机。
7. **release 走 R8**：新增的 Gson 模型必须落在 `…data` / `…domain` 包里，才会被 `proguard-rules.pro` 的 `-keep class com.lightxin.**.data.**` / `.domain.**` 罩住；放错包会出现"debug 正常、release 解析出空列表"。
8. **改完不更新 `codestable/architecture/running-overview.md` 的行号锚点**：一处插行会让后续行号整体位移。
9. **系统代理会伪装成代码 bug**：本机 `127.0.0.1:7897` 会被 Gradle JVM 继承但 `curl` 不继承，出现过"curl 全 200、Gradle 握手被断"的场面。

## 9. 年级分流（背景，改动少但容易踩）

唯一判据：`SportsGradeMapper.isJuniorGrade(checkDsStudent 的 result 串包含 "大一大二")`，**不从学号或本地推断**。大一大二没有 `startRunning.do`，走 `startJuniorRunning()`，`exerciseId` 取自 `extraInfo.extraId`、`memberId` / `mixOnceMile` 取自 `extraDetailInfo.do`；额外接口（`extraInfo.do` / `clubInfo.do` / `extraDetailInfo.do?runningType=1`）逐个 `runCatching` 隔离，一个挂掉不牵连其它。大三/大四 `clubSummary = null`，首页俱乐部卡片自然隐藏。

## 10. 相关文档

- `codestable/architecture/running-overview.md`（模块总览，权威）
- `codestable/architecture/location-overview.md`、`network-overview.md`、`auth-overview.md`、`notification-overview.md`
- `codestable/compound/2026-10-14-decision-running-route-plaintext.md`（轨迹明文结案）
- `codestable/compound/2026-10-13-explore-sports-h5-protocol-evidence.md`（官方 H5 协议取证）
- `codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md`（双 RSA）
- `codestable/compound/2026-04-22-decision-no-map-sdk.md`、`…-decision-route-template-json-not-room.md`
- 根目录 `AGENTS.md` 的 Hard Constraints 与 项目碎片知识

> 注意：`docs/`、`prototype/`、`HAR/` 是 git-ignored，本仓库里不存在。`codestable/` 中指向 `docs/接口分析/…` 的引用无法在本地解析，涉及抓包细节的结论要标「未经抓包复核」。
