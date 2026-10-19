package com.lightxin.feature.checkin

import com.lightxin.feature.checkin.domain.CheckinHistoryRecord
import com.lightxin.feature.checkin.domain.CheckinRecordStatus
import com.lightxin.feature.checkin.domain.checkinMonthKey
import com.lightxin.feature.checkin.domain.sortCheckinHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckinHistoryTest {

    private fun record(
        id: String,
        dateLabel: String,
        status: CheckinRecordStatus,
    ) = CheckinHistoryRecord(
        id = id,
        taskName = "晚点名",
        dateLabel = dateLabel,
        time = "22:30",
        place = "",
        hasPhoto = false,
        status = status,
    )

    @Test
    fun `月份键补零且跨年正确`() {
        assertEquals("2026-10", checkinMonthKey(2026, 10))
        assertEquals("2026-01", checkinMonthKey(2026, 1))
        assertEquals("2027-12", checkinMonthKey(2027, 12))
    }

    @Test
    fun `状态码按官方历史页的档位解析`() {
        assertEquals(CheckinRecordStatus.SIGNED, CheckinRecordStatus.fromCode("1"))
        assertEquals(CheckinRecordStatus.NOT_SIGNED, CheckinRecordStatus.fromCode("0"))
        assertEquals(CheckinRecordStatus.LEAVE, CheckinRecordStatus.fromCode("3"))
        assertEquals(CheckinRecordStatus.LEAVE_SCHOOL, CheckinRecordStatus.fromCode("4"))
        assertNull(CheckinRecordStatus.fromCode("5"))
        assertNull(CheckinRecordStatus.fromCode(null))
    }

    @Test
    fun `历史记录按日期倒序，同日已签到在前`() {
        val sorted = sortCheckinHistory(
            listOf(
                record("3", "10-05", CheckinRecordStatus.SIGNED),
                record("4", "10-19", CheckinRecordStatus.NOT_SIGNED),
                record("5", "10-05", CheckinRecordStatus.NOT_SIGNED),
                record("6", "10-12", CheckinRecordStatus.LEAVE),
            )
        )
        assertEquals(listOf("4", "6", "3", "5"), sorted.map { it.id })
    }

    @Test
    fun `月份标签日期字符串缺失时留空而不是崩`() {
        val sorted = sortCheckinHistory(listOf(record("1", "", CheckinRecordStatus.SIGNED)))
        assertEquals("", sorted.single().dateLabel)
    }
}
