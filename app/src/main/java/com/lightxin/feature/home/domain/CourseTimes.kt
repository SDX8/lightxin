package com.lightxin.feature.home.domain

import com.lightxin.feature.schedule.domain.Course
import java.time.LocalTime

/**
 * 一节课的起止时间。
 *
 * 用户自设的第 0 节不在全校作息表里——早自习与早锻炼时段不同，且课表接口从不下发时间——
 * 所以自带时段优先，其余节次照旧查 [SectionSchedule]。
 */
object CourseTimes {
    fun startOf(course: Course): LocalTime? =
        course.morningSlot?.start ?: SectionSchedule.startOf(course.startSection)

    fun endOf(course: Course): LocalTime? =
        course.morningSlot?.end ?: SectionSchedule.endOf(course.endSection)
}
