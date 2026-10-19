package com.lightxin.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FdyglProtocolTest {

    @Test
    fun `code zero alone is not success when the server says flag false`() {
        // 真实载荷：{"code":"0","flag":false,"msg":"非法访问"} —— 会话被顶掉，但 code 仍是 "0"
        assertFalse(fdyglSucceeded("0", false))
        assertTrue(fdyglSucceeded("0", true))
        // 不带 flag 的老响应按成功处理，否则会把好数据判成失败
        assertTrue(fdyglSucceeded("0", null))
        assertTrue(fdyglSucceeded("200", true))
        assertFalse(fdyglSucceeded("500", true))
        assertFalse(fdyglSucceeded(null, null))
    }

    @Test
    fun `illegal access is translated into something a student can act on`() {
        val message = fdyglFailureMessage("非法访问", "获取签到任务失败")

        assertTrue(message, message.contains("重新登录"))
        assertFalse(message, message.contains("非法访问"))
    }

    @Test
    fun `other server messages pass through, blank ones fall back`() {
        assertEquals("任务不存在", fdyglFailureMessage("任务不存在", "获取任务详情失败"))
        assertEquals("获取任务详情失败", fdyglFailureMessage(null, "获取任务详情失败"))
        assertEquals("获取任务详情失败", fdyglFailureMessage("  ", "获取任务详情失败"))
    }
}
