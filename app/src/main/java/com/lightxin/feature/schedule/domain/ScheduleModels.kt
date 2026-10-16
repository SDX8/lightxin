package com.lightxin.feature.schedule.domain

data class WeekInfo(
    val currentWeek: Int,
    val totalWeeks: Int,
    val schoolYear: String,
    val schoolTerm: String,
    /** 非空表示数据来自本地快照（断网回退），值为缓存写入时刻 */
    val cachedAtMillis: Long? = null,
)

data class Course(
    val name: String,
    val startSection: Int,
    val endSection: Int,
    val room: String,
    val teacher: String,
    val dayOfWeek: Int,  // 1=周一, 7=周日
    /** 非空表示这是用户自设的第 0 节（早自习/早锻炼），时间跟着它走，不查全校作息表 */
    val morningSlot: MorningSlot? = null,
)

/**
 * getCourses() 返回值，包含课程列表与 weekDates（dayOfWeek → MM/dd）
 */
data class ScheduleData(
    val courses: List<Course>,
    val weekDates: Map<Int, String>,
    /** 非空表示数据来自本地快照（断网回退），值为缓存写入时刻 */
    val cachedAtMillis: Long? = null,
)
