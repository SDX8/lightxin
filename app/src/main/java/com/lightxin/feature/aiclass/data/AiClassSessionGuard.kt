package com.lightxin.feature.aiclass.data

import com.lightxin.core.network.FifSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启动探测的结论。只有 [Blocked] 才该在首页提示多端登录，
 * 其余三种都不提示——[Skipped] 是没用过 AI课堂，[NetworkIssue] 是不能把断网说成被人顶号。
 */
enum class AiClassSessionCheck { Skipped, Healthy, Blocked, NetworkIssue }

/**
 * AI课堂会话体检。
 *
 * FIF 的 token 持久化在 DataStore，但 SESSION Cookie 只在内存里，所以进程活着的时候
 * `isSessionValid()` 会一直返回 true，官方 App 登录把服务端映射顶掉之后本地完全察觉不到，
 * 要等到签到才被服务端打回「FIF用户重映射失败」。这里主动跑一次 SSO：
 * 成功即顺带把会话续上（AI课堂能直接用），失败即说明服务端已经不认这个映射了。
 */
@Singleton
class AiClassSessionGuard @Inject constructor(
    private val fifSession: FifSessionManager,
) {
    suspend fun checkOnLaunch(): AiClassSessionCheck = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
        if (fifSession.getFifToken().isNullOrBlank()) {
            // 从没进过 AI课堂，没有 FIF 身份可验，不去打扰
            return@withTimeoutOrNull AiClassSessionCheck.Skipped
        }
        val result = fifSession.performSso()
        when {
            result.isSuccess -> AiClassSessionCheck.Healthy
            isProbeFailure(result.exceptionOrNull()) -> AiClassSessionCheck.NetworkIssue
            else -> AiClassSessionCheck.Blocked
        }
    } ?: AiClassSessionCheck.NetworkIssue

    /** 重登前丢弃本地 FIF 身份，避免下次 SSO 带着旧映射重来 */
    suspend fun clearFifSession() {
        fifSession.clear()
    }

    private companion object {
        const val CHECK_TIMEOUT_MS = 12_000L
    }
}

/** 网络异常与协程超时都不构成「被顶号」的证据 */
internal fun isProbeFailure(error: Throwable?): Boolean =
    generateSequence(error) { it.cause }.any { it is IOException || it is CancellationException }
