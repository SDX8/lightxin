package com.lightxin.feature.home.domain

import com.lightxin.feature.schedule.domain.Course
import java.time.LocalTime

/**
 * 一节课的起止时间。
 *
 * 用户自设的早/晚行不在全校作息表里——早自习与早锻炼时段不同，晚自习又是一整段，
 * 且课表接口从不下发时间——所以自带时段优先，其余节次照旧查 [SectionSchedule]。
 */
object CourseTimes {
    fun startOf(course: Course): LocalTime? =
        course.specialSlot?.start ?: SectionSchedule.startOf(course.startSection)

    fun endOf(course: Course): LocalTime? =
        course.specialSlot?.end ?: SectionSchedule.endOf(course.endSection)
}
