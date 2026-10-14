package com.lightxin.feature.running.data

import com.lightxin.core.auth.RSAUtils
import com.lightxin.feature.running.domain.TrackPoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class RunningUploadPayload(
    val exerciseId: String,
    val memberId: String,
    val runningType: String,
    val startDate: String,
    val endDate: String,
    val mileKm: Double,
    val timeSeconds: Long,
    val speedKmh: Double,
    val runningRoute: List<TrackPoint>,
    val sourceInfo: String,
)

@Singleton
class RunningEncryption @Inject constructor() {

    fun encryptUploadPayload(payload: RunningUploadPayload): String {
        val routeArray = JSONArray()
        payload.runningRoute.forEach { point ->
            routeArray.put(
                JSONObject()
                    .put("latitude", formatCoordinate(point.latitude))
                    .put("longitude", formatCoordinate(point.longitude))
            )
        }

        val plainValues = linkedMapOf(
            "exerciseId" to payload.exerciseId,
            "memberId" to payload.memberId,
            "runningType" to payload.runningType,
            "startDate" to payload.startDate,
            "endDate" to payload.endDate,
            "mile" to formatDecimal(payload.mileKm),
            "time" to payload.timeSeconds.toString(),
            "speed" to formatDecimal(payload.speedKmh),
            "runningRoute" to routeArray.toString(),
            "sourceInfo" to payload.sourceInfo,
        )

        val encryptedObject = JSONObject()
        plainValues.forEach { (key, value) ->
            encryptedObject.put(
                RSAUtils.encryptSportData(key),
                RSAUtils.encryptSportData(value),
            )
        }

        return JSONArray().put(encryptedObject).toString()
    }

    /**
     * 官方 H5 的上传载荷：不加密，字段名与类型都和 RSA 那条不同 —— 轨迹字段是复数
     * `runningRoutes`，成员标识是学号而不是 memberId，经纬度/里程/速度是数字而不是字符串。
     */
    fun buildExtractRecordPayload(payload: RunningUploadPayload, studentCode: String): String {
        val route = JSONArray()
        payload.runningRoute.forEach { point ->
            route.put(
                JSONObject()
                    .put("longitude", round(point.longitude, 6))
                    .put("latitude", round(point.latitude, 6)),
            )
        }

        return JSONObject()
            .put("extraMemberId", studentCode)
            .put("startDate", payload.startDate)
            .put("endDate", payload.endDate)
            .put("mile", round(payload.mileKm, 2))
            .put("speed", round(payload.speedKmh, 2))
            .put("time", payload.timeSeconds)
            .put("runningRoutes", route)
            .toString()
    }

    private fun round(value: Double, decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        return Math.round(value * factor) / factor
    }

    private fun formatDecimal(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.6f", value)
}
