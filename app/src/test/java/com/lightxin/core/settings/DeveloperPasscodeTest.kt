package com.lightxin.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 这里刻意不写真实口令：仓库里一旦出现明文，加盐哈希就白做了。
 * 正向路径（库里那串哈希确实对应使用者知道的口令）只能靠第一次输入来验证，
 * 所以这里钉住「哈希算法没写错」和「常量形状没抄错」。
 */
class DeveloperPasscodeTest {

    @Test
    fun `hashing scheme matches the independently computed golden vector`() {
        // printf '%s%s' 'lightxin.dev.passcode.v1' 'abc' | shasum -a 256
        assertEquals(
            "d0483573c6a7116299fa658d565426834db8f6fd375ab74e7637e76e1b472cf7",
            DeveloperPasscode.hashFor("abc"),
        )
    }

    @Test
    fun `hash output is always a full lowercase sha-256 hex string`() {
        assertTrue(DeveloperPasscode.hashFor("abc").matches(Regex("[0-9a-f]{64}")))
        assertTrue(DeveloperPasscode.hashFor("非 ASCII 口令").matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `verify rejects blank and mismatched passcodes`() {
        assertFalse(DeveloperPasscode.verify(""))
        assertFalse(DeveloperPasscode.verify("   "))
        assertFalse(DeveloperPasscode.verify("LightXin2025"))
        assertFalse(DeveloperPasscode.verify("lightxin2026"))
    }
}
