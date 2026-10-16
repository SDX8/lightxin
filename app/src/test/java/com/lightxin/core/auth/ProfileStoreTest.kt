package com.lightxin.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ProfileStore 本身要 Context 与 DataStore，纯 JVM 测不了；这里钉住它的全部决策逻辑：
 * 昵称怎么清洗、显示名怎么回落、降采样档位怎么取。
 */
class ProfileStoreTest {

    @Test
    fun `nickname is trimmed, kept to one line and capped`() {
        assertEquals("小张", sanitizeNickname("  小张  "))
        assertEquals("小张", sanitizeNickname("小张\n第二行不要"))
        assertEquals("小张", sanitizeNickname("小张\r\n第二行不要"))
        assertEquals("", sanitizeNickname("   \n  "))
        assertEquals(
            "一二三四五六七八九十一二",
            sanitizeNickname("一二三四五六七八九十一二三四十"),
        )
    }

    @Test
    fun `display name falls back to the server real name`() {
        assertEquals("小张", resolveDisplayName(nickname = "小张", serverName = "张建泓"))
        assertEquals("张建泓", resolveDisplayName(nickname = "", serverName = "张建泓"))
        assertEquals("张建泓", resolveDisplayName(nickname = "   ", serverName = "张建泓"))
        assertEquals("", resolveDisplayName(nickname = "", serverName = ""))
    }

    @Test
    fun `avatar initial takes the first visible character`() {
        assertEquals("张", avatarInitial("张建泓"))
        assertEquals("L", avatarInitial("  LightXin"))
        assertEquals("?", avatarInitial("   "))
        assertEquals("?", avatarInitial(""))
    }

    @Test
    fun `inSampleSize picks the largest power of two that stays above the limit`() {
        assertEquals(4, computeInSampleSize(4000, 3000, 512))
        assertEquals(1, computeInSampleSize(1000, 800, 512))
        assertEquals(1, computeInSampleSize(512, 512, 512))
        assertEquals(1, computeInSampleSize(0, 0, 512))
        assertEquals(1, computeInSampleSize(-1, 2000, 512))
    }
}
