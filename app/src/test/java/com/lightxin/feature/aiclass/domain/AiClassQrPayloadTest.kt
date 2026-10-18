package com.lightxin.feature.aiclass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiClassQrPayloadTest {

    private val legit = "https://sttp.fifedu.com/coursecenter-interaction/qrcodeV2/qrcodeHandler?token=abc&openTheWay=2"

    @Test
    fun `a qr url pointing at the fif host is accepted as-is`() {
        assertEquals(legit, trustedQrRequestUrl(legit))
        assertEquals(legit, trustedQrRequestUrl("  $legit  "))
        assertEquals(
            "https://izuoye.fifedu.com/x",
            trustedQrRequestUrl("https://izuoye.fifedu.com/x"),
        )
    }

    @Test
    fun `scheme matching ignores case but https is mandatory`() {
        assertEquals(
            "HTTPS://sttp.fifedu.com/x",
            trustedQrRequestUrl("HTTPS://sttp.fifedu.com/x"),
        )
        assertNull(trustedQrRequestUrl("http://sttp.fifedu.com/x"))
    }

    @Test
    fun `any other host is refused`() {
        assertNull(trustedQrRequestUrl("https://evil.com/x?token=a"))
        // 后缀拼一个我们的域名做钓鱼
        assertNull(trustedQrRequestUrl("https://sttp.fifedu.com.evil.com/x"))
        // userinfo 伪装：真正的权威部分是 evil.com
        assertNull(trustedQrRequestUrl("https://sttp.fifedu.com@evil.com/x"))
        // 带端口的一律不认
        assertNull(trustedQrRequestUrl("https://sttp.fifedu.com:8443/x"))
    }

    @Test
    fun `a bare token or junk is not a url`() {
        assertNull(trustedQrRequestUrl("abc123"))
        assertNull(trustedQrRequestUrl("//sttp.fifedu.com/x"))
        assertNull(trustedQrRequestUrl(""))
    }
}
