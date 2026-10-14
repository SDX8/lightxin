package com.lightxin.feature.aiclass.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * 探测结论只有一格是「多端登录」，其余都必须沉默。误报的代价比漏报大：
 * 断网、超时都会被 performSso 包成普通 Exception，所以分类只能靠异常类型链。
 */
class AiClassSessionGuardTest {

    @Test
    fun `network failures are not treated as a device conflict`() {
        assertTrue(isProbeFailure(IOException("Unable to resolve host")))
        assertTrue(isProbeFailure(Exception("AI课堂登录失败: timeout", IOException())))
        assertTrue(isProbeFailure(Exception("外层", Exception("中层", IOException()))))
    }

    @Test
    fun `a cancelled probe is not treated as a device conflict`() {
        assertTrue(isProbeFailure(CancellationException("Timed out")))
        assertTrue(isProbeFailure(Exception("AI课堂登录失败", CancellationException())))
    }

    @Test
    fun `a mapping rejected by the server is the conflict signal`() {
        assertFalse(isProbeFailure(null))
        assertFalse(isProbeFailure(Exception("FIF 用户映射失败")))
        assertFalse(isProbeFailure(Exception("FIF 单点登录失败")))
    }
}
