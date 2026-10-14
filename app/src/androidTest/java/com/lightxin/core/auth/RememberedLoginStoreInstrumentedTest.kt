package com.lightxin.core.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AndroidKeyStore 在纯 JVM 测试里拿不到，这条链路只能上设备验。
 * 验的就一件事：IV / 密文的字节偏移和 GCM 参数是不是自洽的——写错了「记住密码」会永远解不出来。
 *
 * 注意它会真的写 `lightxin_login` 这个 DataStore，所以 finally 里把原学号与「未记住」状态还原回去。
 */
@RunWith(AndroidJUnit4::class)
class RememberedLoginStoreInstrumentedTest {

    @Test
    fun rememberedPasswordRoundTripsThroughKeystore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = RememberedLoginStore(context)
        val originalCode = store.loadUserCode()
        val password = "P@ss 中文 123"

        try {
            store.save(userCode = "instrumented", password = password, remember = true)
            assertEquals(password, store.loadPassword())
        } finally {
            store.save(userCode = originalCode, password = "", remember = false)
        }

        assertNull(store.loadPassword())
        assertEquals(originalCode, store.loadUserCode())
    }
}
