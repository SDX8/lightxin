package com.lightxin.core.settings

import java.security.MessageDigest

/**
 * 「启用调试功能」的口令校验。
 *
 * 源码里只留加盐 SHA-256，不留明文口令。但它仍是纯客户端判断：拿到 APK 的人可以把哈希
 * 拿去离线爆破，所以它的定位是挡住顺手乱点的同学，不是安全边界，别拿它保护真正的秘密。
 *
 * 换口令：改 [PASSCODE_SHA256_HEX]，用这条命令重新算——
 * `printf '%s%s' 'lightxin.dev.passcode.v1' '新口令' | shasum -a 256`
 */
object DeveloperPasscode {

    private const val SALT = "lightxin.dev.passcode.v1"
    private const val PASSCODE_SHA256_HEX =
        "4ba7e2145746dd2bb7c42c1c1b9b75c6ca1ce44760b3ca04ab9e35bf1f75ebe3"

    fun verify(input: String): Boolean = hashFor(input) == PASSCODE_SHA256_HEX

    fun hashFor(passcode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((SALT + passcode).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
