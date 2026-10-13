package com.lightxin.feature.schedule.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lightxin.feature.schedule.domain.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCacheStoreTest {

    private val gson = Gson()

    @Test
    fun `course key separates weeks and terms`() {
        val base = courseCacheKey("2021001", "2025-2026", "2", 8)
        assertEquals(base, courseCacheKey("2021001", "2025-2026", "2", 8))
        assertTrue(base != courseCacheKey("2021001", "2025-2026", "2", 9))
        assertTrue(base != courseCacheKey("2021001", "2025-2026", "1", 8))
        assertTrue(base != courseCacheKey("2021002", "2025-2026", "2", 8))
    }

    @Test
    fun `prune keeps only current account and term`() {
        val keep = snapshot(userCode = "A", schoolYear = "2025-2026", schoolTerm = "2", week = 8)
        val otherWeek = snapshot(userCode = "A", schoolYear = "2025-2026", schoolTerm = "2", week = 9)
        val otherTerm = snapshot(userCode = "A", schoolYear = "2025-2026", schoolTerm = "1", week = 8)
        val otherUser = snapshot(userCode = "B", schoolYear = "2025-2026", schoolTerm = "2", week = 8)

        val input = listOf(keep, otherWeek, otherTerm, otherUser)
            .associateBy { courseCacheKey(it.userCode, it.schoolYear, it.schoolTerm, it.week) }

        val pruned = pruneToTerm(input, keep)

        assertEquals(setOf(keep.week, otherWeek.week), pruned.values.map { it.week }.toSet())
        assertTrue(pruned.values.all { it.userCode == "A" && it.schoolTerm == "2" })
    }

    @Test
    fun `gson round trip preserves courses and week dates`() {
        val entry = snapshot(userCode = "A", schoolYear = "2025-2026", schoolTerm = "2", week = 8)
            .copy(
                courses = listOf(
                    Course("高等数学", 1, 2, "A301", "张老师", dayOfWeek = 1),
                    Course("英语", 3, 4, "B105", "", dayOfWeek = 5),
                ),
                weekDates = mapOf("1" to "05/11", "5" to "05/15"),
            )
        val file = ScheduleCacheFile(
            weekInfo = mapOf(
                "A" to CachedWeekInfo(
                    userCode = "A",
                    currentWeek = 8,
                    totalWeeks = 18,
                    schoolYear = "2025-2026",
                    schoolTerm = "2",
                    cachedAtMillis = 1_700_000_000_000L,
                )
            ),
            courses = mapOf(
                courseCacheKey("A", "2025-2026", "2", 8) to entry,
            ),
        )

        val json = gson.toJson(file)
        val restored: ScheduleCacheFile =
            gson.fromJson(json, object : TypeToken<ScheduleCacheFile>() {}.type)

        val restoredEntry = restored.courses?.values?.single()
        assertEquals(2, restoredEntry?.courses?.size)
        assertEquals("高等数学", restoredEntry?.courses?.first()?.name)
        assertEquals(1, restoredEntry?.courses?.first()?.startSection)
        assertEquals(mapOf("1" to "05/11", "5" to "05/15"), restoredEntry?.weekDates)
        assertEquals(1_700_000_000_000L, restored.weekInfo?.get("A")?.cachedAtMillis)
    }

    @Test
    fun `blank json yields empty cache file`() {
        val restored = gson.fromJson("", ScheduleCacheFile::class.java)
        assertNull(restored)
    }

    @Test
    fun `snapshot maps back to schedule data with int day keys`() {
        val entry = snapshot(userCode = "A", schoolYear = "2025-2026", schoolTerm = "2", week = 8)
            .copy(
                courses = listOf(Course("体育", 6, 7, "操场", "李老师", dayOfWeek = 3)),
                weekDates = mapOf("3" to "05/13", "x" to "脏数据"),
            )

        val data = entry.toScheduleData()

        assertEquals(mapOf(3 to "05/13"), data.weekDates)
        assertEquals(1_700_000_000_000L, data.cachedAtMillis)
        assertEquals("体育", data.courses.single().name)
    }

    private fun snapshot(
        userCode: String,
        schoolYear: String,
        schoolTerm: String,
        week: Int,
    ) = CachedCourseWeek(
        userCode = userCode,
        schoolYear = schoolYear,
        schoolTerm = schoolTerm,
        week = week,
        cachedAtMillis = 1_700_000_000_000L,
        courses = emptyList(),
        weekDates = emptyMap(),
    )
}
