package com.lightxin.feature.schedule.domain

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 第 0 节的两种固定时段。
 *
 * 与 [com.lightxin.feature.home.domain.SectionSchedule] 一样，作息写死在客户端——
 * 课表接口本来就不下发时间，早自习的时间也没有任何服务端来源可查。
 */
enum class MorningSlot(val label: String, val start: LocalTime, val end: LocalTime) {
    MORNING_STUDY("早自习", LocalTime.of(7, 40), LocalTime.of(8, 15)),
    MORNING_EXERCISE("早锻炼", LocalTime.of(7, 30), LocalTime.of(8, 0)),
}

/**
 * 用户自设的一条早安排。
 *
 * 名称自由输入（有些班级叫早读、晨跑），时段只跟 [slot] 走；
 * [days] 取值限定在周一~周五，且同一天不能属于两条设置——每天只可能有一种早安排。
 */
data class MorningSetting(
    val id: String,
    val name: String,
    val slot: MorningSlot,
    val room: String,
    val days: Set<Int>,
)

/** 早安排只可能出现在工作日 */
val MORNING_ALLOWED_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5)

/** 展开成课表里的第 0 节课程：一条设置 × 它勾选的每个工作日 */
fun morningCourses(settings: List<MorningSetting>): List<Course> =
    settings.filter { it.name.isNotBlank() }
        .flatMap { setting ->
            setting.days.filter { it in MORNING_ALLOWED_DAYS }.sorted().map { day ->
                Course(
                    name = setting.name,
                    startSection = 0,
                    endSection = 0,
                    room = setting.room.trim(),
                    teacher = "",
                    dayOfWeek = day,
                    morningSlot = setting.slot,
                )
            }
        }

/** 保存前查冲突：day → 已经占用这一天的另一条设置 */
fun morningDayConflicts(
    candidate: MorningSetting,
    existing: List<MorningSetting>,
): Map<Int, MorningSetting> =
    candidate.days.filter { it in MORNING_ALLOWED_DAYS }.mapNotNull { day ->
        existing.firstOrNull { it.id != candidate.id && day in it.days }?.let { day to it }
    }.toMap()

/**
 * 把用户自设的早安排并进课表结果。
 *
 * 大一大二的接口本来就会下发第 0 节，那一天已经有学校给的早自习时不再叠加，
 * 否则网格里同一列会出现两个重叠的第 0 节方块。
 */
fun mergeMorningCourses(
    serverCourses: List<Course>,
    settings: List<MorningSetting>,
): List<Course> {
    if (settings.isEmpty()) return serverCourses
    val schoolOwnedDays = serverCourses.filter { it.startSection <= 0 }.map { it.dayOfWeek }.toSet()
    return serverCourses + morningCourses(settings).filterNot { it.dayOfWeek in schoolOwnedDays }
}

private val MORNING_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 早安排只可能落在工作日，标签与之一致 */
val MORNING_DAY_NAMES: Map<Int, String> = mapOf(
    1 to "周一",
    2 to "周二",
    3 to "周三",
    4 to "周四",
    5 to "周五",
)

/** 列表行里的"周一、周三"，固定按周一到周五的顺序念 */
fun morningDaysLabel(days: Set<Int>): String =
    MORNING_ALLOWED_DAYS.filter { it in days }.joinToString("、") { MORNING_DAY_NAMES.getValue(it) }

/** "07:40–08:15" */
fun morningTimeRange(slot: MorningSlot): String =
    "${MORNING_TIME_FORMAT.format(slot.start)}–${MORNING_TIME_FORMAT.format(slot.end)}"

/**
 * 课程详情里"时间"那一行的值：第 0 节只报时段——名称由用户自定，
 * 再念一遍时段自带的"早自习"会和它打架（比如用户把早锻炼时段命名为"晨跑"）。
 */
fun courseSectionRangeLabel(course: Course): String {
    val slot = course.morningSlot ?: return "第${course.startSection}-${course.endSection}节"
    return morningTimeRange(slot)
}

/** 课程列表里的副标题一行：节次/时段 + 地点，地点为空时不留悬空分隔符 */
fun courseSectionLine(course: Course): String {
    val range = courseSectionRangeLabel(course)
    return if (course.room.isBlank()) range else "$range · ${course.room}"
}

/** 保存被拦下时念给用户的原话，例如"周二已被「早锻炼」占用" */
fun morningConflictMessage(conflicts: Map<Int, MorningSetting>): String =
    conflicts.entries.sortedBy { it.key }.joinToString("；") { (day, taken) ->
        "${MORNING_DAY_NAMES[day]}已被「${taken.name}」占用"
    }
