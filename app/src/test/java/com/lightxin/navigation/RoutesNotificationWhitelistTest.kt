package com.lightxin.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutesNotificationWhitelistTest {

    @Test
    fun `the two routes notifications actually send are accepted`() {
        assertEquals(Routes.AICLASS_HOME, Routes.notificationRouteOrNull(Routes.AICLASS_HOME))
        assertEquals(Routes.RUNNING_ACTIVE, Routes.notificationRouteOrNull(Routes.RUNNING_ACTIVE))
    }

    @Test
    fun `anything else is dropped, including the gated sim screen`() {
        // 外部 Intent 注入这两个就能绕过口令门禁 / 拉起扫码相机
        assertNull(Routes.notificationRouteOrNull(Routes.RUNNING_SIM))
        assertNull(Routes.notificationRouteOrNull(Routes.AICLASS_SCAN))
        // 带参数的路由本来就不在名单里，不接受前缀匹配
        assertNull(Routes.notificationRouteOrNull(Routes.checkinDetail("42")))
        // 不存在的路由以前会让 navigate() 抛 IllegalArgumentException
        assertNull(Routes.notificationRouteOrNull("whatever/not-a-route"))
        assertNull(Routes.notificationRouteOrNull(""))
        assertNull(Routes.notificationRouteOrNull(null))
    }
}
