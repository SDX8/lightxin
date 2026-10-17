package com.lightxin.feature.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpecialSectionTest {

    // 2026-10-21 是周三
    private val wednesday = LocalDate.of(2026, 10, 21)

    private fun setting(
        id: String,
        name: String,
        slot: SpecialSlot = SpecialSlot.MORNING_STUDY,
        room: String = "S203",
        days: Set<Int>,
    ) = SpecialItem(id = id, name = name, slot = slot, room = room, days = days)

    private fun evening(name: String = "晚自习", days: Set<Int>, room: String = "A201") =
        setting(id = "e", name = name, slot = SpecialSlot.EVENING_STUDY, room = room, days = days)

    @Test
    fun `one setting expands into one section-zero course per weekday`() {
        val courses = specialSectionCourses(
            listOf(setting(id = "a", name = "早自习", days = setOf(3, 1))),
            today = wednesday,
        )

        assertEquals(2, courses.size)
        // 展开时按周一到周五排序，不是按勾选顺序
        assertEquals(listOf(1, 3), courses.map { it.dayOfWeek })
        courses.forEach {
            assertEquals(MORNING_ROW, it.startSection)
            assertEquals(MORNING_ROW, it.endSection)
            assertEquals(SpecialSlot.MORNING_STUDY, it.specialSlot)
            assertEquals("早自习", it.name)
        }
    }

    @Test
    fun `weekends and nameless settings never produce morning courses`() {
        val courses = specialSectionCourses(
            listOf(
                setting(id = "a", name = "早锻炼", days = setOf(1, 6, 7)),
                setting(id = "b", name = "  ", room = "操场", days = setOf(2)),
            ),
            today = wednesday,
        )

        assertEquals(listOf(1), courses.map { it.dayOfWeek })
    }

    @Test
    fun `evening lands on its own row below every real section`() {
        val courses = specialSectionCourses(listOf(evening(days = setOf(1, 3, 7))), today = wednesday)

        assertEquals(listOf(1, 3, 7), courses.map { it.dayOfWeek })
        courses.forEach {
            assertEquals(EVENING_ROW, it.startSection)
            assertEquals(EVENING_ROW, it.endSection)
        }
        assertEquals("19:00–20:40", courseSectionRangeLabel(courses.first()))
    }

    @Test
    fun `friday and saturday evenings are cut by the rule, and come back when it is switched off`() {
        val wholeWeek = evening(days = (1..7).toSet())

        val withRule = specialSectionCourses(listOf(wholeWeek), today = wednesday, skipFriSat = true)
        val withoutRule = specialSectionCourses(listOf(wholeWeek), today = wednesday, skipFriSat = false)

        // 周日晚上要上（第二天周一上课），所以规则只挡周五、周六
        assertEquals(setOf(1, 2, 3, 4, 7), allowedDaysFor(SectionKind.EVENING))
        assertEquals(listOf(1, 2, 3, 4, 7), withRule.map { it.dayOfWeek })
        assertEquals((1..7).toList(), withoutRule.map { it.dayOfWeek })
    }

    @Test
    fun `the tonight switch erases only the evening of the day it was pressed on`() {
        val settings = listOf(evening(days = setOf(3, 4)))

        val paused = specialSectionCourses(settings, today = wednesday, eveningPausedOn = wednesday)
        val otherDay = specialSectionCourses(
            settings,
            today = wednesday,
            eveningPausedOn = wednesday.minusDays(1),
        )

        // 周三按下的开关只顶掉周三，周四照旧
        assertEquals(listOf(4), paused.map { it.dayOfWeek })
        assertEquals(listOf(3, 4), otherDay.map { it.dayOfWeek })
    }

    @Test
    fun `conflicts only report days taken by another setting of the same kind`() {
        val existing = listOf(
            setting(id = "a", name = "早锻炼", slot = SpecialSlot.MORNING_EXERCISE, days = setOf(2, 4)),
        )

        assertTrue(specialDayConflicts(setting(id = "a", name = "早锻炼", days = setOf(2)), existing).isEmpty())
        assertTrue(specialDayConflicts(setting(id = "b", name = "早自习", days = setOf(1, 3)), existing).isEmpty())
        // 早晚各占一行，同一天可以并存
        assertTrue(specialDayConflicts(evening(days = setOf(2, 4)), existing).isEmpty())

        val conflicts = specialDayConflicts(setting(id = "b", name = "早自习", days = setOf(1, 2)), existing)
        assertEquals(setOf(2), conflicts.keys)
        assertEquals("早锻炼", conflicts[2]?.name)
        assertEquals("周二已被「早锻炼」占用", specialConflictMessage(conflicts))
    }

    @Test
    fun `a day already covered by the school timetable is not overlaid`() {
        val server = listOf(
            Course("学校早自习", 0, 0, "", "", 1),
            Course("高等数学", 1, 2, "S203", "王", 1),
        )

        val merged = mergeSpecialSections(
            serverCourses = server,
            settings = listOf(
                setting(id = "a", name = "我的早自习", days = setOf(1, 2)),
                evening(days = setOf(1)),
            ),
            today = wednesday,
        )

        // 周一让给学校下发的第 0 节，周二才补用户自己的；晚自习那一行没人下发，照常补上
        assertEquals(4, merged.size)
        assertEquals(listOf("学校早自习", "我的早自习"), merged.filter { it.startSection == MORNING_ROW }.map { it.name })
        assertEquals(listOf(1), merged.filter { it.startSection == EVENING_ROW }.map { it.dayOfWeek })
    }

    @Test
    fun `a self-set row shows only its time range so a custom name never contradicts it`() {
        val morningCourse = specialSectionCourses(
            listOf(setting(id = "a", name = "晨跑", room = " 东区操场 ", days = setOf(1))),
            today = wednesday,
        ).first()
        val normal = Course("高等数学", 1, 2, "S203", "王", 1)

        // 名称在上一行已经念过"晨跑"，时间行不再重复时段自带的"早自习"
        assertEquals("07:40–08:15 · 东区操场", courseSectionLine(morningCourse))
        assertEquals("07:40–08:15", courseSectionRangeLabel(morningCourse))
        assertEquals("第1-2节", courseSectionRangeLabel(normal))
        assertEquals("第1-2节 · S203", courseSectionLine(normal))
    }

    @Test
    fun `a morning-only day has no blank room tail and reads its own slot`() {
        val exercise = specialSectionCourses(
            listOf(setting(id = "a", name = "早锻炼", slot = SpecialSlot.MORNING_EXERCISE, room = "", days = setOf(2))),
            today = wednesday,
        ).first()

        assertEquals("07:30–08:00", courseSectionLine(exercise))
        assertEquals("周一、周三", specialDaysLabel(setOf(3, 1, 9)))
    }
}
