package com.lightxin.feature.running.domain

import kotlin.math.cos

object RunningCampus {

    // 坐标系：内置环线顶点按 BD-09 对待（TrajectoryGenerator.generate 标 isBd09=true，上传时不再
    // 转换）；模板录制拿到的是原生 GPS（WGS-84），上传前经 wgs84ToBd09 转换。
    // 「服务端要 BD-09」已由官方 H5 证实：详情页用 BMap 绘制，并以 BMap.Convertor.translate(pts,1,5)
    // 做 WGS-84→BD-09，见 codestable/compound/2026-10-13-explore-sports-h5-protocol-evidence.md §1。
    // 但这组常量本身属于哪种坐标系仍无一手出处；RouteQualityChecker.inCampus 用同一组常量判真机
    // 录制的 WGS-84 点，若普遍报「超出校园范围」，说明这里不是 BD-09。
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
