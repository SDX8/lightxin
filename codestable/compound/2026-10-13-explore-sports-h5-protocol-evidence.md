---
doc_type: exploration
category: protocol
date: 2026-10-13
slug: sports-h5-protocol-evidence
status: current
area: 智慧运动 / 跑步上传
tags: [running, sports, coordinate, bd09, h5, protocol-evidence]
---

## 为什么值得单独记

`docs/接口分析/跑步接口深度分析报告.md` 与 `HAR/` 都不在当前工作副本里（被 `.gitignore` 主动排除），所以跑步上传链路的若干关键假设长期只有 `running-overview.md` 里一句"来源：docs/..."的转述，无法复核。

本次找到了另一条不依赖抓包的取证路径：**智慧运动模块本身是 H5**，其 JS 明文由服务端直接下发。

## 取证方法

```
curl http://sports.aiit.edu.cn:8082/dist/            # 入口页，标题「智慧体育管理平台」
# 从 app.<hash>.js 的 webpack runtime 里解出全部 56 个 chunk 的 hash 名，逐个下载
# 在 chunk 内 grep 接口名 / 字段名 / 地图 SDK
```

无需登录、无需凭证 —— 静态资源公开。入口页 HTML 只列首屏 chunk，按需加载的 chunk 名字要从 `app.js` 里 `"chunk-<id>":"<hash>"` 映射表还原，否则只能拿到一半。

## 结论

### 1. 服务端坐标系确认是 BD-09

详情页 `RunDetail` 使用 **`BMap`（百度地图 JS SDK）** 绘制轨迹，并在初始化时以 `BMap.Convertor.translate(points, 1, 5, cb)` 做转换 —— 百度的类型码 `1` = GPS(WGS-84)、`5` = BD-09。

这证实了 `CoordinateConverter.wgs84ToBd09()` 的**方向**是对的。`RunningCampus` 中心常量的坐标系归属仍无一手出处，但已不再是纯猜测。

### 2. 轨迹点确实不带时间戳

`initMap()` 解析方式：

```js
JSON.parse(this.basicInfo.runningRoute)
  .map(p => ({ lng: parseFloat(p.longitude), lat: parseFloat(p.latitude) }))
```

只有 `longitude` / `latitude` 两个字段。我们上传的点位结构**没有问题**，"无轨迹"不能归因于缺时间戳。

### 3. 有效性判据是 `state`，理由在 `remark`

记录详情来自 `POST /api/mobile/extra/getExtraCheck.do`，参数 `{checkId}`，响应 `data.extraInfo` + `data.applyInfo`。前端提示逻辑：

```js
"1" === state ? "辛苦了，请继续加油！"
: ("0" === state || "2" === state) ? remark : ""
```

即 **`state=1` 有效；`0`/`2` 无效，且服务端为每条记录存了一句人类可读的 `remark` 说明原因**。这条接口我们尚未实现 —— 它是唯一能直接读到"服务端为什么判无效"的通道。

### 4. 官方 H5 的里程累计规则与我们不同

H5 的 `startTracking`：

```js
if (positions.length > 0) {
  const d = map.getDistance(last, point) / 1000;   // 单位 km
  if (d > 3) basicInfo.mile += d;                  // 注意：>3 米才计
}
positions.push(point);                             // 点一律保留
```

- **相邻点 ≤3 m：不计里程，但点仍然入列**
- 我们（`RunningTracker`）：**<1.5 m 或 >120 m 的段，点直接丢弃**

两种点集分布不同。若服务端按官方口径校验相邻点间距或点数密度，我们的稀疏点集可能被判定异常。

- 速度取的是 **GPS 瞬时速度** `coords.speed * 3.6`，不是 `里程/时长`；我们上传的是平均速度。

### 5. 另有一个明文上传接口，字段名是复数

H5 自己的跑步上传走 `POST /api/mobile/sportApp/addExtractCheckRecord.do`，字段 `extractCheckRecord`，载荷：

```js
{ extraMemberId, startDate, endDate, mile, speed, time, runningRoutes }
```

**轨迹字段是 `runningRoutes`（复数）**，而我们发给 `addExtraCheckNew.do` 的是 `runningRoute`（单数）。两个接口不同，不能据此断定我们写错了 —— 但这是"服务端存不下轨迹"时第一个该排除的名字。H5 全程明文、不做 RSA；RSA 的 `list=` 载荷只出现在原生 App 那条链路上。

### 6. 前端侧单次有效阈值 0.8 km

H5 的 `end()` 里 `isValid = parseFloat(basicInfo.mile) > .8`，与服务端下发的 `mixOnceMile: "1"` 不一致，说明 `mixOnceMile` 是任务要求、0.8 是前端兜底。

## 未能证实的部分

- `RunningCampus.CENTER_LATITUDE/LONGITUDE` 究竟是不是 BD-09。
- 原生 App 的 `addExtraCheckNew.do` 明文载荷字段清单（RSA 公钥加密，无私钥不可解；H5 不走这条）。
- 服务端判"运动轨迹异常"的完整规则。

## 一次没做成的验证

试图在模拟器上用 mock 位置复现"转换后落围栏外"。数学上是成立的：模拟器注入点距校园中心 287 m，经 `wgs84ToBd09` 后变成 987 m（单次转换偏移 1167 m），已出 `RouteQualityChecker` 的 ±600 m 框。

但**模拟器不是有效试验场**：Android 17 模拟器上 `adb emu geo fix` 返回 OK 却不再更新位置，须改用 `cmd location providers add-test-provider gps` + `set-test-provider-enabled true` + `set-test-provider-location gps --location <lat>,<lng>`；即便如此，经我们 3 s / 2 m 的采样过滤后，喂 420 个 3.5 m 步进的点只累计到 0.26 km、均速 1.2 km/h —— 本身已低于服务端 `minSpeed: 2`，结果无法归因。**结论：这条链路必须真机验证。** 测试会话已放弃，未向服务端上传任何数据。

## 现场进展（2026-10-14 更新）

### 坐标系已被 A/B 排除

同一账号、同一条内置校园环线，分别以两种坐标系上传：

| 上传内容 | 结果 |
|---|---|
| 真实跑步：GPS 原始 WGS-84 → `wgs84ToBd09` → BD-09 | `state=0`，`运动轨迹异常，无效` |
| 模拟提交（内置环线，标 `isBd09=true`，原样上传 WGS-84） | `state=0`，`运动轨迹异常，无效` |

两次都：记录建成、返回有效 id、`runningRoute` 存下了密文、`mile`/`speed`/`time` 全在服务端自己下发的限值内、原始点都在校园框内。

**所以 §1 的「服务端要 BD-09」虽然对 H5 的显示链路成立，但不足以解释服务端校验；坐标系不是根因，已排除。**

### 剩下的分歧：端点本身就不一样

官方 H5 里大一大二实际走的上传，与我们走的不是同一个接口：

| | 官方 H5 | 我们 |
|---|---|---|
| 接口 | `mobile/sportApp/addExtractCheckRecord.do` | `mobile/extra/addExtraCheckNew.do` |
| 表单字段 | `extractCheckRecord` | `list` |
| 载荷 | 明文 JSON | RSA 双加密 |
| 成员标识 | `extraMemberId`（值是学号） | `memberId`（UUID） |
| 轨迹字段 | `runningRoutes`（复数，值为数字） | `runningRoute`（单数，值为字符串） |

`addExtraCheckNew.do` 在整个 H5 里**一次都没出现过**，只能来自原生 App 的抓包；而本仓库是 fork 来的、没有原始 HAR，无法核对原生实现。

### 为此加的 A/B 开关

`DeveloperPrefs.extractRecordUploadEnabled`（关于页 → 开启调试功能后才出现「跑步上传改走 H5 明文端点」）。打开后 `RunningRepository.uploadSnapshot()` 改走 `addExtractCheckRecord.do` + `RunningEncryption.buildExtractRecordPayload()` 的明文载荷，结果页文案会带上「走 addExtractCheckRecord.do」以标明这次测的是哪条臂。

额外好处：这条端点是**明文**的，debug 包的 BODY 日志能直接看到我们发出的完整载荷 —— RSA 那条永远看不到。

## 相关文档

- `codestable/architecture/running-overview.md` §6
- `codestable/architecture/location-overview.md`
- `codestable/compound/2026-04-22-decision-no-map-sdk.md`
- `codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md`
