package com.lightxin.feature.aiclass.domain

data class AiClassQrPayload(
    val rawValue: String,
    val token: String,
)

/** FIF 自家的三个域；扫码请求只认它们 */
private val QR_TRUSTED_HOSTS = setOf(
    "sttp.fifedu.com",
    "izuoye.fifedu.com",
    "aiitpass.fifedu.com",
)

private const val HTTPS_PREFIX = "https://"

/**
 * 二维码原文只有指向 FIF 自家域名时才允许直接当请求地址用，否则返回 null。
 *
 * 扫码请求会带上 `Basic <FIF token>`、身份 Cookie 和追加进查询串的校内 access_token，
 * 原文能随便写 host 的话，在教室屏幕上覆盖一张 `https://evil.com/x?token=a` 就能把
 * 整份会话凭据一次性送到攻击者服务器 —— 不需要任何漏洞利用。
 *
 * 带端口或 userinfo 的一律不认：`https://sttp.fifedu.com@evil.com/` 的权威部分是 evil.com。
 */
fun trustedQrRequestUrl(rawValue: String): String? {
    val trimmed = rawValue.trim()
    if (!trimmed.startsWith(HTTPS_PREFIX, ignoreCase = true)) return null
    val authority = trimmed.substring(HTTPS_PREFIX.length)
        .takeWhile { it != '/' && it != '?' && it != '#' && it != '\\' }
    if (authority.any { it == ':' || it == '@' }) return null
    return trimmed.takeIf { authority.lowercase() in QR_TRUSTED_HOSTS }
}
