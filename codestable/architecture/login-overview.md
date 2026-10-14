---
doc_type: architecture
slug: login-overview
scope: feature/login/ 的登录流程、RSA 密码加密、Token 持久化，以及与其他模块的认证共享边界
summary: login 以 LoginRepository 封装 RSA 加密与 Token 存取，UI 层是单表单无状态机，认证凭据写入 TokenManager 后供 AuthInterceptor 消费；「记住密码」由 core/auth/RememberedLoginStore 用 AndroidKeyStore 加密落盘
status: current
last_reviewed: 2026-10-14
tags: [login, auth, rsa, token, datastore, remember-password, keystore]
depends_on: [network-overview]
---

# 登录模块总览

## 0. 术语

- **RSA 密码加密** — `RSAUtils.encryptPassword()` 用 `publicKey` 对明文密码加密后再传输；与跑步模块的 `publicKey2` 是两组不同的密钥
- **Token 体系** — 登录成功后返回 `accessToken + refreshToken`，经 `TokenManager.saveLoginData()` 持久化到 DataStore，供后续请求的 `AuthInterceptor` 取用
- **登录态判定** — `SessionManager.isLoggedIn` 基于 `TokenManager` 中的 accessToken 是否存在且非空
- **记住密码** — `RememberedLoginStore`（`core/auth/`）把密码用 AndroidKeyStore 里的 AES/GCM 密钥加密后存进 `lightxin_login` DataStore；学号仍明文存同一处

## 1. 定位与受众

本 doc 描述 `feature/login/` 的职责边界：

- 登录请求的入口，不是全局认证中心（认证由 `core/auth/` 的 `TokenManager` + `AuthInterceptor` 承载）
- 只处理首次登录与 token 刷新两种场景；退出登录由 `HomeScreen` 或 `SessionManager.clearSession()` 触发，不在 login 模块范围内

适用读者：

- 要改登录表单流程、加验证方式、或调 RSA 加密策略的人
- 要理解 accessToken / refreshToken 在项目里的存储与流转的人
- 要排查"登录成功了但后续请求仍然 401"的人

## 2. 结构与交互

### 2.1 三层结构：Api → Repository → ViewModel

login 是项目中最简洁的 feature 模块，三个文件对应 clean architecture 三层：

```
LoginApi (data)       → POST mobile/getToken.do
                       POST mobile/refresh.do
LoginRepository (data) → 加密 → 调用 → 落 Token
LoginViewModel (ui)   → 输入状态 + 触发
LoginScreen (ui)      → 表单渲染
（记住密码的落盘在 core/auth/RememberedLoginStore，不在 feature/login 内）
```

没有 domain 层仓储（`LoginModels.kt` 只定义了 `UserInfo`，但当前未被 ViewModel 使用）。

### 2.2 LoginRepository 承担加密 + 网络 + Token 持久化三件事

锚点：`feature/login/data/LoginRepository.kt:19-63`

`login()` 的执行顺序：

1. `RSAUtils.encryptPassword(password)` 加密明文密码
2. 调用 `LoginApi.login(userCode, encryptedPassword)`
3. 从响应中取出 `TokenInfo`（`accessToken / refreshToken / userCode / userName / userType / fileAddress`）
4. 全部写入 `TokenManager.saveLoginData()`

`refreshToken()` 同理，用本地保存的 refreshToken 换取新 token 对。

### 2.3 LoginViewModel 管理表单输入与提交状态

锚点：`feature/login/ui/LoginViewModel.kt:24-88`

`LoginUiState` 包含：
- `userCode` / `password` — 双向绑定
- `rememberPassword` — 「记住密码」勾选态
- `isLoading` — 防重复提交
- `error` — 错误提示
- `loginSuccess` — 驱动导航跳转

ViewModel init 时调 `restoreRemembered()`：从 `RememberedLoginStore` 读回学号，并在勾选过「记住密码」且能解密时把密码一并填进表单（`rememberPassword` 跟着回填为 true）。

登录成功时：
1. `rememberedLoginStore.save(userCode, password, remember)` —— 学号总存，密码只在勾选时加密存、未勾选时删掉已存密文
2. 置 `loginSuccess = true`
3. 外部 `LoginScreen` 的 `LaunchedEffect` 监听此信号触发导航

### 2.4 LoginScreen 是单页表单，无多步骤流程

锚点：`feature/login/ui/LoginScreen.kt:66-206`

UI 结构：
- 顶部插画框（`LoginIllustration` — 由 Canvas 绘制，不在本模块内）
- 表单卡：学号输入框 + 密码输入框（带显隐切换）+ `RememberPasswordRow` + 错误提示 + 登录按钮/加载指示器

输入框使用 `LxTextField`（designsystem 组件），错误提示用 `AnimatedVisibility` 做渐入渐出。整体布局包含 `imePadding()` 处理键盘弹起。

`RememberPasswordRow`（锚点 `feature/login/ui/LoginScreen.kt:210-235`）整行可点：落在 `Checkbox` 上的点击被它自己消费，落在文字上的点击走行 `clickable`，实测不会重复翻转。

### 2.5 记住密码的落盘：RememberedLoginStore

锚点：`core/auth/RememberedLoginStore.kt:26-124`

`lightxin_login` 这个 DataStore 现在由 `RememberedLoginStore` 独占（delegate 只能有一处，否则两个 DataStore 实例抢同一个文件会崩），三个 key：`saved_user_code`（沿用原 key 名，老用户的学号不丢）、`remember_password`、`encrypted_password`。

密码走 `AndroidKeyStore` 里别名 `lightxin.remembered_login` 的 AES-256-GCM 密钥：密文 = `Base64(iv[12] + gcmBody)`，解密时按固定偏移切回。密钥不可导出，所以导出 DataStore 文件也拿不到明文。

加解密都裹在 `runCatching` 里，失败一律降级成「没记住」并打 warning：
- 写失败 → 本次不保存密码
- 读失败（换机、恢复备份后密钥失效）→ 顺手清掉密文与勾选态，避免每次启动白试

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `LoginUiState` | 表单输入 + 记住密码勾选 + 加载 + 错误 + 成功信号 | `LoginViewModel.kt:14-22` |
| `TokenInfo` | accessToken / refreshToken / userCode / userName / userType / fileAddress | `LoginResponse.kt:15-22` |
| `UserInfo` | userCode / userName / userType / fileAddress（领域模型，当前未使用） | `LoginModels.kt:3-8` |

### 3.2 状态所有权

- `LoginUiState` 由 `LoginViewModel` 持有，页面级状态
- Token 持久化由 `TokenManager`（DataStore）持有，跨 ViewModel / 跨进程共享
- 学号与加密后的密码由 `RememberedLoginStore` 独占的 `lightxin_login` DataStore 持有（与 Token DataStore 分离）

### 3.3 Token 刷新不在 login 模块内

token 过期后的自动刷新由 `core/network/TokenRefreshInterceptor` 拦截 401 后触发，调用 `LoginRepository.refreshToken()`。这意味着 `LoginRepository` 被两个调用方共享：
- `LoginViewModel` — 首次登录
- `TokenRefreshInterceptor` — 静默刷新

## 4. 关键决策

- **密码在客户端 RSA 加密后传输** —— 不使用 HTTPS 传输明文密码；`publicKey` 硬编码在客户端。来源：`feature/login/data/LoginRepository.kt:21`、`core/auth/RSAUtils.kt`。这条直接受后端接口约束。
- **Token 写入 DataStore 而非内存或 SharedPreferences** —— 与项目的偏好存储策略一致。来源：`core/auth/TokenManager.kt`。
- **登录成功后跳转使用 `popUpTo(LOGIN) { inclusive = true }`** —— 防止用户按返回键回到登录页。来源：`NavGraph.kt:124-126`。
- **学号缓存独立于 Token DataStore** —— 即使 token 过期，学号仍然保留，方便快速重新登录。来源：`core/auth/RememberedLoginStore.kt:39-41`。
- **记住密码走 AndroidKeyStore，不用第三方加密库** —— 项目没引 `androidx.security:crypto`，直接 `KeyGenParameterSpec` + AES/GCM 二十行够用；密钥不可导出，比把密码明文写进 DataStore 对得上「校园账号密码」这个敏感度。来源：`core/auth/RememberedLoginStore.kt:101-124`。
- **加解密失败一律降级为「没记住」** —— 宁可让用户重输一次，也不能让登录页因为 Keystore 抽风而崩或卡住。来源：`core/auth/RememberedLoginStore.kt:44-59,61-70`。

## 5. 代码锚点

- `feature/login/data/LoginApi.kt:9-21` — 登录与刷新两个接口
- `feature/login/data/LoginRepository.kt:19-63` — 加密 → 调用 → 落 Token 主链
- `feature/login/data/LoginResponse.kt:6-22` — 响应模型与 TokenInfo
- `feature/login/ui/LoginViewModel.kt:24-88` — 表单状态管理与登录触发
- `feature/login/ui/LoginScreen.kt:66-206` — 表单 UI 渲染
- `feature/login/ui/LoginScreen.kt:210-235` — 记住密码勾选行
- `core/auth/RememberedLoginStore.kt:26-124` — 学号 + 加密密码落盘（AndroidKeyStore AES/GCM）
- `core/auth/TokenManager.kt` — Token 读写接口
- `core/auth/RSAUtils.kt` — RSA 加密工具
- `core/network/TokenRefreshInterceptor.kt` — 401 自动刷新

## 6. 已知约束 / 边界情况

- **UserInfo 领域模型未被使用** —— `LoginModels.kt:3-8` 定义了 `UserInfo`，但 ViewModel 直接消费 `TokenInfo`，没有中间映射层。未来若需要跨模块传递用户信息，需决定是否启用此模型。
- **login 不负责退出登录** —— 退出由 `SessionManager.clearSession()` 处理，入口在 `HomeScreen` 的"我的"页。来源：`feature/home/ui/HomeScreen.kt`。
- **登录成功导航后 LoginViewModel 不会被立即销毁** —— 因为使用了 `popUpTo(LOGIN) { inclusive = true }`，LoginScreen 会从返回栈移除，ViewModel 也会被清理。
- **学号缓存与 Token DataStore 分离的设计意味着"清除 Token 不影响学号"** —— 这是有意的；「记住密码」同理，退出登录后密码仍在，配合首页 AI课堂横幅的「重新登录」可以一键登回。
- **记住密码只在「本机 + 本应用安装」内可解** —— Keystore 私钥不可导出，清应用数据、换机、恢复备份都会让它失效；解密失败时按未勾选处理（§2.5）。
- **跑 `:app:connectedDebugAndroidTest` 会先清空被测应用的数据** —— 模拟器上要重新登录，`RememberedLoginStoreInstrumentedTest` 也会真的写 `lightxin_login`（靠 finally 还原原学号与未勾选态）。别拿有真实登录态的设备当测试机。

## 7. 相关文档

- 网络层多域名架构：`codestable/architecture/network-overview.md`
- RSA 双密钥决策：`codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md`
- 架构总入口：`codestable/architecture/DESIGN.md`
