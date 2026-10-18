package com.lightxin.core.network

import com.lightxin.core.auth.TokenRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当收到401响应时，尝试用 refresh_token 刷新 access_token 并重试请求。
 * 通过 Lazy 注入 TokenRefresher 避免网络栈初始化循环依赖。
 */
@Singleton
class TokenRefreshInterceptor @Inject constructor(
    private val tokenRefresher: dagger.Lazy<TokenRefresher>,
) : Interceptor {

    private val refreshMutex = Mutex()
    private val lastRefreshSuccessMillis = AtomicLong(0L)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // 取 token / 刷新 token 自己的 401 绝不能再触发刷新：
        // 本拦截器挂在主 client 上，包括这两个接口，少这一句就是无限递归
        if (path.contains("getToken.do") || path.contains("refresh.do")) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)
        if (response.code != 401) return response

        val refreshed = try {
            runBlocking { singleFlightRefresh() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

        if (!refreshed) return response
        response.close()
        return chain.proceed(request)
    }

    /**
     * 并发 401 只刷一次。
     *
     * 服务端轮换 refresh_token，后到的那次刷新会把刚拿到的新 token 顶成无效，
     * 表现就是"莫名掉登录"。锁 + 一个短合并窗口：窗口内成功的刷新直接复用。
     */
    private suspend fun singleFlightRefresh(): Boolean = refreshMutex.withLock {
        val now = System.currentTimeMillis()
        if (now - lastRefreshSuccessMillis.get() < REFRESH_COALESCE_MS) return@withLock true
        val refreshed = tokenRefresher.get().refreshToken()
        if (refreshed) lastRefreshSuccessMillis.set(now)
        refreshed
    }

    private companion object {
        const val REFRESH_COALESCE_MS = 3_000L
    }
}
