package com.lightxin.feature.todo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodoModelsTest {

    // 2026-10-17 是周六
    private val saturday = LocalDate.of(2026, 10, 17)

    private fun todo(
        id: String,
        title: String = id,
        subject: String = "",
        due: LocalDate = saturday,
        done: Boolean = false,
        createdAt: Long = 0L,
        doneAt: LocalDate? = null,
    ) = TodoItem(
        id = id,
        title = title,
        subject = subject,
        dueDate = due,
        createdAtMillis = createdAt,
        done = done,
        doneAt = doneAt,
    )

    @Test
    fun `overdue items fold into today instead of getting their own section`() {
        val sections = todoSections(
            listOf(
                todo(id = "later", due = saturday.plusDays(2)),
                todo(id = "today", due = saturday),
                todo(id = "over", due = saturday.minusDays(3)),
                todo(id = "done", due = saturday, done = true, doneAt = saturday),
            ),
            today = saturday,
        )

        // 逾期排在今天最前面：先按到期日，再按添加顺序
        assertEquals(listOf("over", "today"), sections.today.map { it.id })
        assertEquals(listOf("later"), sections.later.map { it.id })
        assertEquals(listOf("done"), sections.done.map { it.id })
        assertEquals(3, sections.openCount)
    }

    @Test
    fun `a just-checked item stays where it was until the list settles`() {
        val items = listOf(
            todo(id = "keep", title = "刚勾掉的", due = saturday, done = true, doneAt = saturday),
            todo(id = "next", due = saturday.plusDays(1)),
            todo(id = "archived", due = saturday, done = true, doneAt = saturday),
        )

        val pinned = todoSections(items, today = saturday, pinnedDone = setOf("keep"))
        val settled = todoSections(items, today = saturday)

        // 钉住时它还在今天段里，已完成段只剩早就做完的那条；只有没做完的算进 openCount
        assertEquals(listOf("keep"), pinned.today.map { it.id })
        assertEquals(listOf("archived"), pinned.done.map { it.id })
        assertEquals(1, pinned.openCount)

        // 松开之后一起归档：今天段空了，两条按完成时间倒序（同一天则按添加顺序）
        assertTrue(settled.today.isEmpty())
        assertEquals(listOf("next"), settled.later.map { it.id })
        assertEquals(listOf("keep", "archived"), settled.done.map { it.id })
        assertEquals(1, settled.openCount)
    }

    @Test
    fun `same-day items keep the order they were added in`() {
        val sections = todoSections(
            listOf(
                todo(id = "c", due = saturday, createdAt = 300L),
                todo(id = "a", due = saturday, createdAt = 100L),
                todo(id = "b", due = saturday, createdAt = 200L),
            ),
            today = saturday,
        )

        assertEquals(listOf("a", "b", "c"), sections.today.map { it.id })
    }

    @Test
    fun `completed items are read newest-done-first`() {
        val sections = todoSections(
            listOf(
                todo(id = "old", done = true, doneAt = saturday.minusDays(2)),
                todo(id = "new", done = true, doneAt = saturday),
                todo(id = "never-marked", done = true),
            ),
            today = saturday,
        )

        // 没记 doneAt 的历史条目拿到期日兜底，不至于冒到最前面
        assertEquals(listOf("new", "never-marked", "old"), sections.done.map { it.id })
    }

    @Test
    fun `overdue wording says how many days`() {
        assertNull(overdueLabel(todo(id = "t", due = saturday), saturday))
        assertEquals("昨天到期，还没做完", overdueLabel(todo(id = "t", due = saturday.minusDays(1)), saturday))
        assertEquals("逾期 3 天", overdueLabel(todo(id = "t", due = saturday.minusDays(3)), saturday))
        assertTrue(isOverdue(todo(id = "t", due = saturday.minusDays(1)), saturday))
        // 做完了就不该再被叫逾期
        assertTrue(!isOverdue(todo(id = "t", due = saturday.minusDays(1), done = true), saturday))
    }

    @Test
    fun `due labels read the way a student says them`() {
        assertEquals("今天", dueLabel(saturday, saturday))
        assertEquals("明天", dueLabel(saturday.plusDays(1), saturday))
        assertEquals("昨天", dueLabel(saturday.minusDays(1), saturday))
        assertEquals("周一 10月19日", dueLabel(saturday.plusDays(2), saturday))
        assertEquals("2027年1月5日", dueLabel(LocalDate.of(2027, 1, 5), saturday))
    }

    @Test
    fun `a blank subject never leaves a dangling separator`() {
        assertEquals("高数 · 今天", todoMetaLine(todo(id = "t", subject = "高数"), saturday))
        assertEquals("今天", todoMetaLine(todo(id = "t"), saturday))
        assertEquals("高数 · 逾期 2 天", todoMetaLine(todo(id = "t", subject = "高数", due = saturday.minusDays(2)), saturday))
    }

    @Test
    fun `input is single-line and length-capped`() {
        assertEquals("第一行", sanitizeTodoTitle("  第一行\n第二行\n"))
        assertEquals(80, sanitizeTodoTitle("字".repeat(200)).length)
        assertEquals(16, sanitizeTodoSubject("科目很长很长的名字".repeat(5)).length)
    }
}
