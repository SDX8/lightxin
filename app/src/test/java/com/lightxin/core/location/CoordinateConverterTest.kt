package com.lightxin.core.location

import com.lightxin.feature.running.domain.GeoDistance
import com.lightxin.feature.running.domain.RouteQualityChecker
import com.lightxin.feature.running.domain.RouteQualityStatus
import com.lightxin.feature.running.domain.RunningCampus
import com.lightxin.feature.running.domain.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 转换公式里 `0.000003` 被写成 `0.00003` 这类一位数的错，肉眼看不出来，
 * 但会让轨迹平移几百米、直接被服务端判成「运动轨迹异常」。这里用黄金值钉住。
 */
class CoordinateConverterTest {

    @Test
    fun `wgs84 to bd09 matches the golden value at the campus`() {
        val bd = CoordinateConverter.wgs84ToBd09(31.138771, 118.615759)

        assertEquals(31.1421305, bd.latitude, 1e-6)
        assertEquals(118.6272907, bd.longitude, 1e-6)
    }

    @Test
    fun `each step of the chain stays in its documented offset band`() {
        val lat = RunningCampus.CENTER_LATITUDE
        val lng = RunningCampus.CENTER_LONGITUDE

        val gcj = CoordinateConverter.wgs84ToGcj02(lat, lng)
        val bd = CoordinateConverter.gcj02ToBd09(gcj.latitude, gcj.longitude)

        val wgsToGcj = GeoDistance.metersBetween(lat, lng, gcj.latitude, gcj.longitude)
        val gcjToBd = GeoDistance.metersBetween(gcj.latitude, gcj.longitude, bd.latitude, bd.longitude)
        val total = GeoDistance.metersBetween(lat, lng, bd.latitude, bd.longitude)

        assertTrue("WGS→GCJ ${wgsToGcj.toInt()} m", wgsToGcj in 300.0..700.0)
        assertTrue("GCJ→BD ${gcjToBd.toInt()} m", gcjToBd in 400.0..1200.0)
        assertTrue("WGS→BD ${total.toInt()} m", total in 800.0..1400.0)
    }

    @Test
    fun `a campus-centred loop leaves the fence box once converted, because the box is not BD-09`() {
        val loop = List(40) { i ->
            val angle = i * Math.PI / 20.0
            RunningCampus.pointAt(200.0 * kotlin.math.sin(angle), 200.0 * kotlin.math.cos(angle))
        }
        val converted = loop.map {
            val bd = CoordinateConverter.wgs84ToBd09(it.latitude, it.longitude)
            TrackPoint(bd.latitude, bd.longitude)
        }

        // 说明 RunningCampus 的 ±600 m 框与 CENTER_* 是同一坐标系；任何拿 BD-09 点去比这个框的
        // 判定都会恒定误报约 1.2 km 的"越界"。上传路径已不再做该检查，模板录制仍用原始点。
        assertEquals(RouteQualityStatus.PASS, RouteQualityChecker.evaluate(loop).status)
        assertTrue(
            RouteQualityChecker.evaluate(converted).message?.contains("超出校园范围") == true,
        )
    }

    @Test
    fun `coordinates outside china skip the gcj offset entirely`() {
        val gcj = CoordinateConverter.wgs84ToGcj02(48.8584, 2.2945)

        assertEquals(48.8584, gcj.latitude, 1e-9)
        assertEquals(2.2945, gcj.longitude, 1e-9)
    }
}
