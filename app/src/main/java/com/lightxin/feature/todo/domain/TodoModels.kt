package com.lightxin.feature.todo.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 一条待办：今天布置的作业、要交的材料、任何"这几天还得做"的事。
 *
 * 全部留在本地 —— 学校没有任何待办接口，这份清单从一开始就没有服务端来源。
 */
data class TodoItem(
    val id: String,
    val title: String,
    val subject: String,
    val dueDate: LocalDate,
    val createdAtMillis: Long,
    val done: Boolean = false,
    val doneAt: LocalDate? = null,
)

/**
 * 待办页的三段。
 *
 * 逾期不单独成段：它本来就是"今天还没做完"里最该先看到的那几条，
 * 拆出去反而要用户在两个段落之间来回找。
 */
data class TodoSections(
    val today: List<TodoItem>,
    val later: List<TodoItem>,
    val done: List<TodoItem>,
) {
    val openCount: Int get() = today.size + later.size
    val isEmpty: Boolean get() = today.isEmpty() && later.isEmpty() && done.isEmpty()
}

fun todoSections(items: List<TodoItem>, today: LocalDate): TodoSections {
    val open = items.filterNot { it.done }
    val byDueThenAdded = compareBy<TodoItem>({ it.dueDate }, { it.createdAtMillis })
    return TodoSections(
        today = open.filter { !it.dueDate.isAfter(today) }.sortedWith(byDueThenAdded),
        later = open.filter { it.dueDate.isAfter(today) }.sortedWith(byDueThenAdded),
        done = items.filter { it.done }
            .sortedWith(compareByDescending<TodoItem> { it.doneAt ?: it.dueDate }
                .thenBy { it.createdAtMillis }),
    )
}

fun isOverdue(item: TodoItem, today: LocalDate): Boolean =
    !item.done && item.dueDate.isBefore(today)

/** "逾期 3 天"；没逾期返回 null，由调用方决定显示什么 */
fun overdueLabel(item: TodoItem, today: LocalDate): String? {
    if (!isOverdue(item, today)) return null
    val days = today.toEpochDay() - item.dueDate.toEpochDay()
    return if (days == 1L) "昨天到期，还没做完" else "逾期 $days 天"
}

private val DUE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

private val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** "今天" / "明天" / "周三 10月21日"，跨年才带年份 */
fun dueLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "今天"
    today.plusDays(1) -> "明天"
    today.minusDays(1) -> "昨天"
    else -> if (date.year == today.year) {
        "${WEEKDAY_NAMES[date.dayOfWeek.value - 1]} ${DUE_FORMAT.format(date)}"
    } else {
        "${date.year}年${DUE_FORMAT.format(date)}"
    }
}

/** 标题与科目都是单行，长度卡死是为了列表行不折行、也不至于存进无穷长的粘贴内容 */
const val TODO_TITLE_MAX = 80
const val TODO_SUBJECT_MAX = 16

fun sanitizeTodoTitle(raw: String): String = raw.lineSequence().first().trim().take(TODO_TITLE_MAX)

fun sanitizeTodoSubject(raw: String): String = raw.lineSequence().first().trim().take(TODO_SUBJECT_MAX)

/** 科目可留空；列表行里"高数 · 今天"不能变成"· 今天" */
fun todoMetaLine(item: TodoItem, today: LocalDate): String {
    val due = overdueLabel(item, today) ?: dueLabel(item.dueDate, today)
    return if (item.subject.isBlank()) due else "${item.subject} · $due"
}
