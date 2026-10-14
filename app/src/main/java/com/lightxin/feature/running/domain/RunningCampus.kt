package com.lightxin.feature.running.domain

import kotlin.math.cos

object RunningCampus {

    // 校园中心：2026-10-14 由用户提供，替换掉来源不明的 31.13893 / 118.628131（两点相距约 620 m）。
    //
    // 坐标系归属仍未确认，但影响面已经很小：服务端不做校园围栏校验（轨迹在校外仍判有效），
    // 所以这组常量只决定内置模拟环线画在哪、以及模板质量检查的框，不影响记录能否入账。
    // 服务端「显示侧」是 BD-09 这点由官方 H5 证实（BMap + Convertor.translate(pts,1,5)），
    // 见 codestable/compound/2026-10-13-explore-sports-h5-protocol-evidence.md §1。
    const val CENTER_LATITUDE = 31.134154
    const val CENTER_LONGITUDE = 118.631502

    private const val METERS_PER_LAT_DEGREE = 111_320.0
    private const val CAMPUS_HALF_HEIGHT_METERS = 600.0
    private const val CAMPUS_HALF_WIDTH_METERS = 600.0

    val minLatitude: Double = CENTER_LATITUDE - metersToLatitude(CAMPUS_HALF_HEIGHT_METERS)
    val maxLatitude: Double = CENTER_LATITUDE + metersToLatitude(CAMPUS_HALF_HEIGHT_METERS)
    val minLongitude: Double = CENTER_LONGITUDE - metersToLongitude(CAMPUS_HALF_WIDTH_METERS)
    val maxLongitude: Double = CENTER_LONGITUDE + metersToLongitude(CAMPUS_HALF_WIDTH_METERS)

    fun pointAt(latOffsetMeters: Double, lngOffsetMeters: Double): TrackPoint {
        return TrackPoint(
            latitude = CENTER_LATITUDE + metersToLatitude(latOffsetMeters),
            longitude = CENTER_LONGITUDE + metersToLongitude(lngOffsetMeters),
        )
    }

    private fun metersToLatitude(meters: Double): Double = meters / METERS_PER_LAT_DEGREE

    private fun metersToLongitude(meters: Double): Double {
        return meters / (METERS_PER_LAT_DEGREE * cos(Math.toRadians(CENTER_LATITUDE)))
    }
}
