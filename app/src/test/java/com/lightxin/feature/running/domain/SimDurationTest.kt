package com.lightxin.feature.running.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimDurationTest {

    @Test
    fun `秒段只收两位且不超过 59`() {
        assertEquals("5", normalizeSimDurationPart("5", SIM_SECONDS_MAX))
        assertEquals("59", normalizeSimDurationPart("59", SIM_SECONDS_MAX))
        assertEquals("05", normalizeSimDurationPart("05", SIM_SECONDS_MAX))
        assertNull("60 秒不是合法档位", normalizeSimDurationPart("60", SIM_SECONDS_MAX))
        assertNull(normalizeSimDurationPart("599", SIM_SECONDS_MAX))
    }

    @Test
    fun `分段收到三位且过滤非数字`() {
        assertEquals("90", normalizeSimDurationPart("a9b0", SIM_MINUTES_MAX))
        assertEquals("999", normalizeSimDurationPart("999", SIM_MINUTES_MAX))
        assertNull(normalizeSimDurationPart("1000", SIM_MINUTES_MAX))
    }

    @Test
    fun `清空输入是合法状态而不是被拒`() {
        assertEquals("", normalizeSimDurationPart("", SIM_SECONDS_MAX))
        assertEquals("", normalizeSimDurationPart("ab", SIM_MINUTES_MAX))
    }

    @Test
    fun `分秒合成总秒数`() {
        assertEquals(1_445L, simDurationTotalSeconds("24", "5"))
        assertEquals(1_440L, simDurationTotalSeconds("24", "0"))
        assertEquals(1_440L, simDurationTotalSeconds("24", ""))
        assertEquals(5L, simDurationTotalSeconds("", "5"))
        assertEquals(5L, simDurationTotalSeconds("0", "5"))
    }

    @Test
    fun `两段全空或全零时没有有效时长`() {
        assertNull(simDurationTotalSeconds("", ""))
        assertNull(simDurationTotalSeconds("0", "0"))
        assertNull(simDurationTotalSeconds("0", ""))
    }

    @Test
    fun `展示串补零到两位秒`() {
        assertEquals("24 分 00 秒", formatSimDuration(1_440L))
        assertEquals("24 分 05 秒", formatSimDuration(1_445L))
        assertEquals("1 分 30 秒", formatSimDuration(90L))
        assertEquals("0 分 45 秒", formatSimDuration(45L))
    }
}
