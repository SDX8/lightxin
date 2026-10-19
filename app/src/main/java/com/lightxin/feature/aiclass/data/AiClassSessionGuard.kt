package com.lightxin.feature.aiclass.data

import android.util.Log
import com.lightxin.core.network.FifSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val PROBE_LOG_TAG = "AiClassProbe"

/**
 * 一次 SSO 的结论。只有 [Blocked] 才该在首页提示多端登录，
 * [NetworkIssue] 不能把断网说成被人顶号。
 */
enum class AiClassSessionCheck { Healthy, Blocked, NetworkIssue }

/**
 * AI课堂会话体检。
 *
 * FIF 的 token 持久化在 DataStore，但 SESSION Cookie 只在内存里，所以进程活着的时候
 * `isSessionValid()` 会一直返回 true，官方 App 登录把服务端映射顶掉之后本地完全察觉不到，
 * 要等到签到才被服务端打回「FIF用户重映射失败」。这里主动跑一次 SSO：
 * 成功即顺带把会话续上（AI课堂能直接用），失败即说明服务端已经不认这个映射了。
 *
 * 探测不看本地有没有缓存 token —— 被顶号时第 3 步用户映射失败，`performSso()` 根本不会落盘，
 * 于是"没有 token 就跳过"等于对最该报警的那种账号永远沉默。SSO 只需要校内 accessToken，
 * 从头跑一次就能验；代价是每次冷启动多三个后台请求，换来横幅真的会出现。
 */
@Singleton
class AiClassSessionGuard @Inject constructor(
    private val fifSession: FifSessionManager,
) {
    private val _blocked = MutableStateFlow(false)

    /** 首页横幅的数据源：启动探测与 AI课堂页内的失败都写这里 */
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()

    suspend fun checkOnLaunch(): AiClassSessionCheck = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
        val check = checkOf(fifSession.performSso())
        record(check)
        when (check) {
            AiClassSessionCheck.Blocked -> Log.w(PROBE_LOG_TAG, "服务端拒绝 FIF 会话")
            AiClassSessionCheck.NetworkIssue -> Log.w(PROBE_LOG_TAG, "会话体检未能下结论")
            AiClassSessionCheck.Healthy -> Unit
        }
        check
    } ?: AiClassSessionCheck.NetworkIssue.also { Log.w(PROBE_LOG_TAG, "会话体检超时") }

    /** AI课堂页自己跑过一次 SSO 之后也要记账，否则用户得重启 App 才能看到横幅 */
    fun record(check: AiClassSessionCheck) {
        // 网络问题不足以推翻已有的结论，也不该把它清成"正常"
        if (check != AiClassSessionCheck.NetworkIssue) _blocked.value = check == AiClassSessionCheck.Blocked
    }

    /** 重登前丢弃本地 FIF 身份，避免下次 SSO 带着旧映射重来 */
    suspend fun clearFifSession() {
        fifSession.clear()
        _blocked.value = false
    }

    private companion object {
        const val CHECK_TIMEOUT_MS = 12_000L
    }
}

/** 一次 SSO 的结果意味着什么 */
internal fun checkOf(ssoResult: Result<*>): AiClassSessionCheck = when {
    ssoResult.isSuccess -> AiClassSessionCheck.Healthy
    isProbeFailure(ssoResult.exceptionOrNull()) -> AiClassSessionCheck.NetworkIssue
    else -> AiClassSessionCheck.Blocked
}

/** 网络异常与协程超时都不构成「被顶号」的证据 */
internal fun isProbeFailure(error: Throwable?): Boolean =
    generateSequence(error) { it.cause }.any { it is IOException || it is CancellationException }
