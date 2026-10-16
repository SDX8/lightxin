package com.lightxin.feature.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningSectionTest {

    private fun setting(
        id: String,
        name: String,
        slot: MorningSlot = MorningSlot.MORNING_STUDY,
        room: String = "S203",
        days: Set<Int>,
    ) = MorningSetting(id = id, name = name, slot = slot, room = room, days = days)

    @Test
    fun `one setting expands into one section-zero course per weekday`() {
        val courses = morningCourses(
            listOf(setting(id = "a", name = "早自习", days = setOf(3, 1))),
        )

        assertEquals(2, courses.size)
        // 展开时按周一到周五排序，不是按勾选顺序
        assertEquals(listOf(1, 3), courses.map { it.dayOfWeek })
        courses.forEach {
            assertEquals(0, it.startSection)
            assertEquals(0, it.endSection)
            assertEquals(MorningSlot.MORNING_STUDY, it.morningSlot)
            assertEquals("早自习", it.name)
        }
    }

    @Test
    fun `weekends and nameless settings never produce courses`() {
        val courses = morningCourses(
            listOf(
                setting(id = "a", name = "早锻炼", days = setOf(1, 6, 7)),
                setting(id = "b", name = "  ", room = "操场", days = setOf(2)),
            ),
        )

        assertEquals(listOf(1), courses.map { it.dayOfWeek })
    }

    @Test
    fun `a day already covered by the school timetable is not overlaid`() {
        val server = listOf(
            Course("学校早自习", 0, 0, "", "", 1),
            Course("高等数学", 1, 2, "S203", "王", 1),
        )

        val merged = mergeMorningCourses(
            serverCourses = server,
            settings = listOf(setting(id = "a", name = "我的早自习", days = setOf(1, 2))),
        )

        // 周一让给学校下发的第 0 节，周二才补用户自己的
        assertEquals(3, merged.size)
        assertEquals(listOf("学校早自习", "我的早自习"), merged.filter { it.startSection == 0 }.map { it.name })
    }

    @Test
    fun `conflicts only report days taken by another setting`() {
        val existing = listOf(
            setting(id = "a", name = "早锻炼", slot = MorningSlot.MORNING_EXERCISE, days = setOf(2, 4)),
        )

        assertTrue(morningDayConflicts(setting(id = "a", name = "早锻炼", days = setOf(2)), existing).isEmpty())
        assertTrue(morningDayConflicts(setting(id = "b", name = "早自习", days = setOf(1, 3)), existing).isEmpty())

        val conflicts = morningDayConflicts(setting(id = "b", name = "早自习", days = setOf(1, 2)), existing)
        assertEquals(setOf(2), conflicts.keys)
        assertEquals("早锻炼", conflicts[2]?.name)
        assertEquals("周二已被「早锻炼」占用", morningConflictMessage(conflicts))
    }

    @Test
    fun `section zero shows only its time range so a custom name never contradicts it`() {
        val morning = morningCourses(listOf(setting(id = "a", name = "晨跑", room = " 东区操场 ", days = setOf(1))))
            .first()
        val normal = Course("高等数学", 1, 2, "S203", "王", 1)

        // 名称在上一行已经念过"晨跑"，时间行不再重复时段自带的"早自习"
        assertEquals("07:40–08:15 · 东区操场", courseSectionLine(morning))
        assertEquals("07:40–08:15", courseSectionRangeLabel(morning))
        assertEquals("第1-2节", courseSectionRangeLabel(normal))
        assertEquals("第1-2节 · S203", courseSectionLine(normal))
    }

    @Test
    fun `a morning-only day has no blank room tail and reads its own slot`() {
        val exercise = morningCourses(
            listOf(setting(id = "a", name = "早锻炼", slot = MorningSlot.MORNING_EXERCISE, room = "", days = setOf(2))),
        ).first()

        assertEquals("07:30–08:00", courseSectionLine(exercise))
        assertEquals("周一、周三", morningDaysLabel(setOf(3, 1, 9)))
    }
}
