package com.lightxin.core.network

/**
 * fdygl 域（查寝 / 节假日登记）共用的成败判定。
 *
 * 这个域的 `code` 在失败时也是 `"0"` —— 会话被顶掉或过期时服务端返回
 * `{"code":"0","flag":false,"msg":"非法访问"}`。只看 `code` 会把它当成
 * "请求成功、列表为空"，用户看到的是一句"暂无任务"，而不是"你得重新登录"。
 *
 * `flag` 缺失（null）按成功处理：并非所有接口都带这个字段。
 */
fun fdyglSucceeded(code: String?, flag: Boolean?): Boolean =
    (code == "0" || code == "200") && flag != false

/**
 * 服务端拒绝时的文案。"非法访问"是它自己的措辞，对用户没有意义，
 * 而它恰恰是多端登录顶号的表现 —— 说清楚，否则只能靠猜。
 */
fun fdyglFailureMessage(msg: String?, fallback: String): String = when {
    msg?.contains("非法访问") == true -> "校园服务会话已失效（在其它设备登录会互相顶号），请重新登录后再试"
    !msg.isNullOrBlank() -> msg
    else -> fallback
}
