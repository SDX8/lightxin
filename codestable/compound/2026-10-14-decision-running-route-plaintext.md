---
doc_type: decision
category: constraint
date: 2026-10-14
slug: running-route-plaintext
status: active
area: 跑步数据上传
tags: [running, rsa, encryption, sports-api, protocol]
---

## 背景

`2026-04-22-decision-running-dual-rsa-encryption.md` 记录的规则是"跑步上传时字段名和字段值**都**要用 `publicKey2` RSA 加密"，来源是原生 App 的抓包。本仓库是 fork 来的，原始 HAR 不在副本里（`.gitignore` 排除了 `/HAR`），所以这条规则长期无法复核，只能照抄。

近期跑步记录一直被判 `state=0`、`remark="运动轨迹异常，无效"`，官方 App 里画不出轨迹。围绕它做的排查依次排除了若干方向，最后定位到这条加密规则本身。

## 决定

**`runningRoute` 字段的值以明文上传，不做 RSA 加密。** 其余字段的值、以及全部字段名，仍然走 `RSAUtils.encryptSportData()`。

即：`addExtraCheckNew.do` 的 `list` 参数是

```json
[{"<enc \"exerciseId\">":"<enc value>", ..., "<enc \"runningRoute\">":"[{\"latitude\":\"31.138930\",\"longitude\":\"118.628131\"}, ...]", ...}]
```

键名照旧加密，只有 `runningRoute` 的 value 是明文 JSON 数组字符串。

## 证据

同一账号、同一份轨迹数据，只改这一个值的加密方式：

| `runningRoute` 值 | 服务端结果 |
|---|---|
| RSA 密文（原实现） | 记录建成、返回有效 id、`mile`/`speed`/`time` 全部正确入库，但 `state=0`、`remark="运动轨迹异常，无效"`、官方 App 无轨迹 |
| 明文 | **`state=1`，判有效** |

## 机理

RSA 是分段加密：1024 位公钥每块只能吃 117 字节明文，输出 128 字节密文，多块直接拼接后整体 Base64。

- `mile` / `speed` / `time` / `memberId` 等字段明文都只有几十字节 → **单个 RSA 块** → 服务端按 128 字节切分即可解，一切正常。这也解释了为什么这些字段的值在记录里始终是对的。
- `runningRoute` 是几 KB（127–488 个点，每点约 46 字节）→ **几十个块拼成一个 Base64 串**。服务端解密时若按"整个字符串 = 一个块"或按不同的分块约定处理，结果就是一团垃圾 → 轨迹解析失败 → 判"运动轨迹异常"、地图画不出来。

也就是说：**服务端能解开短字段、解不开长字段**，症状恰好是"数值都对、只有轨迹不行"。

## 考虑过的替代方案

- **换端点**：官方 H5 走 `mobile/sportApp/addExtractCheckRecord.do`（明文）。实测该端点只在 `/api` 上下文存在，而 `/api/*` 受 `in.aiit.edu.cn/uaac-server` 的 CAS 单点登录保护，我们的 `/mobile` 上下文里它是 404。不可行。
- **换坐标系**：分别上传 BD-09 与原样 WGS-84，两次都判无效。已排除（详见 `2026-10-13-explore-sports-h5-protocol-evidence.md`）。
- **把长字段拆成多个 RSA 块并改变传输结构**：需要服务端配合改解码方式，我们改不了。

## 后果

- 上传的轨迹坐标在链路上是**明文可读**的。它不含身份证信息，只有校园跑步的位置；但这是个隐私面，改动服务端协议时应重新评估。
- "字段名和字段值都要加密"这句口头规则不再准确，已在 `AGENTS.md` 硬约束里改写为"键名全加密、值除 `runningRoute` 外全加密"。旧决策文档保留但标注为部分失效。
- 上传路径上的轨迹质量检查（`RouteQualityChecker`）已移除：服务端根本不做校园围栏校验（轨迹明显在校外仍判有效），那条告警只会制造噪音。模板录制仍保留质量检查。
- **仍未解决**：`RunningCampus.CENTER_LATITUDE/LONGITUDE = 31.13893 / 118.628131` 的出处与坐标系归属。公开资料显示该校位于南京市栖霞区仙林大学城文澜路99号（约 32.1°N），与该常量相差约一个纬度（110 km 量级），而真机操场数据实测只偏离它 1.4 km —— 两者矛盾，说明这个常量既可能坐标系不明、也可能数值本身有误。它现在只影响内置模拟环线和模板质量检查，不影响记录有效性。

## 相关文档

- 被本决定部分修正：`2026-04-22-decision-running-dual-rsa-encryption.md`
- 取证过程与已排除方向全表：`2026-10-13-explore-sports-h5-protocol-evidence.md`
- 代码：`feature/running/data/RunningEncryption.encryptUploadPayload()`
- 模块约束：`codestable/architecture/running-overview.md` §6
