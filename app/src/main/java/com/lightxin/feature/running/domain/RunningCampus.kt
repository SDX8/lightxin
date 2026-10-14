package com.lightxin.feature.running.domain

import kotlin.math.cos

object RunningCampus {

    // 坐标系：这组常量来自一份**未做 WGS-84→GCJ-02 转换**的真实户外 GPX，即它是 WGS-84。
    // 因此 RouteQualityChecker 的围栏判定必须拿转换前的原始点来比（见 RunningRepository.trajectoryWarning），
    // 否则会恒定报出约 1.2 km 的假越界 —— 那正是 WGS→BD 在本地的偏移量级。
    //
    // 未决：服务端到底要 WGS-84 还是 BD-09。官方 H5 用 BMap 画图、并用 Convertor.translate(1→5) 做
    // WGS→BD，指向 BD-09；但真实跑步转换后仍被判「运动轨迹异常」，尚未排除服务端其实收原始坐标。
    // 注意 TrajectoryGenerator.generate 目前把内置环线标成 isBd09=true（即不转换直接上传）——
    // 若服务端要 BD-09，这个标记是错的；要 WGS-84 才是对的。A/B 结论出来前不要动它。
    const val CENTER_LATITUDE = 31.13893
    const val CENTER_LONGITUDE = 118.628131

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
