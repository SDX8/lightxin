package com.lightxin.feature.schedule.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 课表上两行"学校没给、用户自己填"的安排：最上面的「早」行与最下面的「晚」行 */
enum class SectionKind(val row: Int) {
    MORNING(MORNING_ROW),
    EVENING(EVENING_ROW),
}

/** 「早」行的节次号；课表网格本来就支持第 0 节 */
const val MORNING_ROW = 0

/**
 * 「晚」行的节次号。作息表只到第 11 节，取 12 保证它一定排在所有真实课程之后，
 * 也不会与学校下发的任何节次撞行。
 */
const val EVENING_ROW = 12

/**
 * 用户可自设的固定时段。
 *
 * 与 [com.lightxin.feature.home.domain.SectionSchedule] 一样写死在客户端——课表接口从不下发时间，
 * 早晚自习的时间也没有任何服务端来源可查。
 */
enum class SpecialSlot(
    val kind: SectionKind,
    val label: String,
    val start: LocalTime,
    val end: LocalTime,
) {
    MORNING_STUDY(SectionKind.MORNING, "早自习", LocalTime.of(7, 40), LocalTime.of(8, 15)),
    MORNING_EXERCISE(SectionKind.MORNING, "早锻炼", LocalTime.of(7, 30), LocalTime.of(8, 0)),
    EVENING_STUDY(SectionKind.EVENING, "晚自习", LocalTime.of(19, 0), LocalTime.of(20, 40)),
}

/**
 * 用户自设的一条早/晚安排。名称自由输入（有的班叫早读、机房晚自习），
 * 时段只跟 [slot] 走；同一种类里，一个星期几只能被一条占用。
 */
data class SpecialItem(
    val id: String,
    val name: String,
    val slot: SpecialSlot,
    val room: String,
    val days: Set<Int>,
)

/** 早安排只可能落在工作日 */
val MORNING_ALLOWED_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5)

/**
 * 周五、周六晚上没有晚自习——第二天是周六或周日，不上课。
 *
 * 这是"明天要上课才有晚自习"规则里能靠星期判断的那一半，写死在选择范围里，
 * 所以界面上压根选不出这两天。节假日那一半不引入节假日表，由用户用「今晚无晚自习」开关自己顶掉；
 * 周日晚上反而要上（第二天周一上课），所以周日在范围内。
 */
val EVENING_SKIPPED_DAYS: Set<Int> = setOf(5, 6)

val EVENING_ALLOWED_DAYS: Set<Int> = (1..7).toSet() - EVENING_SKIPPED_DAYS

fun allowedDaysFor(kind: SectionKind): Set<Int> =
    if (kind == SectionKind.MORNING) MORNING_ALLOWED_DAYS else EVENING_ALLOWED_DAYS

/**
 * 展开成课表上的额外行。
 *
 * [eveningPausedOn] 是用户按下「今晚无晚自习」那天的日期，只抹掉那一天的晚自习，
 * 过了那天自然失效，不需要谁记得去关。
 */
fun specialSectionCourses(
    settings: List<SpecialItem>,
    today: LocalDate = LocalDate.now(),
    eveningPausedOn: LocalDate? = null,
): List<Course> =
    settings.filter { it.name.isNotBlank() }
        .flatMap { setting ->
            val kind = setting.slot.kind
            allowedDaysFor(kind)
                .filter { it in setting.days }
                .filterNot { kind == SectionKind.EVENING && eveningPausedOn == today && it == today.dayOfWeek.value }
                .sorted()
                .map { day ->
                    Course(
                        name = setting.name,
                        startSection = kind.row,
                        endSection = kind.row,
                        room = setting.room.trim(),
                        teacher = "",
                        dayOfWeek = day,
                        specialSlot = setting.slot,
                    )
                }
        }

/** 保存前查冲突：只跟同一种类（早/晚）比，早晚各占一行，互不干扰 */
fun specialDayConflicts(
    candidate: SpecialItem,
    existing: List<SpecialItem>,
): Map<Int, SpecialItem> {
    val allowed = allowedDaysFor(candidate.slot.kind)
    return candidate.days.filter { it in allowed }.mapNotNull { day ->
        existing.firstOrNull { it.id != candidate.id && it.slot.kind == candidate.slot.kind && day in it.days }
            ?.let { day to it }
    }.toMap()
}

/**
 * 把用户自设的早晚安排并进课表结果。
 *
 * 大一大二的接口本来就会下发第 0 节；学校已经给过的那一行那一天不再叠加，
 * 否则同一列会叠出两个方块。
 */
fun mergeSpecialSections(
    serverCourses: List<Course>,
    settings: List<SpecialItem>,
    today: LocalDate = LocalDate.now(),
    eveningPausedOn: LocalDate? = null,
): List<Course> {
    if (settings.isEmpty()) return serverCourses
    val occupied = serverCourses.flatMap { course ->
        (course.startSection..course.endSection).map { row -> row to course.dayOfWeek }
    }.toSet()
    return serverCourses + specialSectionCourses(settings, today, eveningPausedOn)
        .filterNot { (it.startSection to it.dayOfWeek) in occupied }
}

private val SPECIAL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

val DAY_NAMES: Map<Int, String> = mapOf(
    1 to "周一",
    2 to "周二",
    3 to "周三",
    4 to "周四",
    5 to "周五",
    6 to "周六",
    7 to "周日",
)

/** 列表行里的"周一、周三"，固定按周一到周日排序 */
fun specialDaysLabel(days: Set<Int>): String =
    (1..7).filter { it in days }.joinToString("、") { DAY_NAMES.getValue(it) }

/** "07:40–08:15" */
fun specialTimeRange(slot: SpecialSlot): String =
    "${SPECIAL_TIME_FORMAT.format(slot.start)}–${SPECIAL_TIME_FORMAT.format(slot.end)}"

/**
 * 课程详情里"时间"那一行的值：自设行只报时段——名称由用户自定，
 * 再念一遍时段自带的"早自习"会和它打架（比如用户把早锻炼时段命名为"晨跑"）。
 */
fun courseSectionRangeLabel(course: Course): String {
    val slot = course.specialSlot ?: return "第${course.startSection}-${course.endSection}节"
    return specialTimeRange(slot)
}

/** 课程列表里的副标题一行：节次/时段 + 地点，地点为空时不留悬空分隔符 */
fun courseSectionLine(course: Course): String {
    val range = courseSectionRangeLabel(course)
    return if (course.room.isBlank()) range else "$range · ${course.room}"
}

/** 保存被拦下时念给用户的原话，例如"周二已被「早锻炼」占用" */
fun specialConflictMessage(conflicts: Map<Int, SpecialItem>): String =
    conflicts.entries.sortedBy { it.key }.joinToString("；") { (day, taken) ->
        "${DAY_NAMES[day]}已被「${taken.name}」占用"
    }
