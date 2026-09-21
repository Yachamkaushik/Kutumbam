package com.kutumbam.app

import com.kutumbam.app.data.MedicineEntity
import com.kutumbam.app.reminder.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderScheduleTest {
    private fun med(start: String, days: Int?) = MedicineEntity(
        id = 7, documentId = 1, memberId = 1, name = "X", strength = null, form = null, frequencyCode = "BD",
        timesCsv = "08:00,20:00", mealTiming = "UNSPECIFIED", durationDays = days, startDate = start, confirmedByUser = true,
    )

    @Test fun laterTodayIfTheTimeHasNotPassed() {
        val next = ReminderScheduler.nextOccurrence(med("2026-09-21", 30), LocalTime.of(20, 0), LocalDateTime.of(2026, 9, 21, 7, 50))
        assertEquals(LocalDateTime.of(2026, 9, 21, 20, 0), next)
    }

    @Test fun tomorrowIfTheTimeHasPassed() {
        val next = ReminderScheduler.nextOccurrence(med("2026-09-21", 30), LocalTime.of(8, 0), LocalDateTime.of(2026, 9, 21, 9, 0))
        assertEquals(LocalDateTime.of(2026, 9, 22, 8, 0), next)
    }

    @Test fun stopsAfterTheCourseEnds() {
        // 5-day course from the 21st covers the 21st..25th
        assertEquals(LocalDateTime.of(2026, 9, 25, 8, 0), ReminderScheduler.nextOccurrence(med("2026-09-21", 5), LocalTime.of(8, 0), LocalDateTime.of(2026, 9, 24, 9, 0)))
        assertNull(ReminderScheduler.nextOccurrence(med("2026-09-21", 5), LocalTime.of(8, 0), LocalDateTime.of(2026, 9, 25, 9, 0)))
    }

    @Test fun openEndedCourseKeepsGoing() {
        assertEquals(LocalDateTime.of(2027, 1, 2, 8, 0), ReminderScheduler.nextOccurrence(med("2026-09-21", null), LocalTime.of(8, 0), LocalDateTime.of(2027, 1, 1, 9, 0)))
    }

    @Test fun requestCodesAreUniquePerMedicineAndTime() {
        val a = ReminderScheduler.requestCode(7, LocalTime.of(8, 0))
        val b = ReminderScheduler.requestCode(7, LocalTime.of(20, 0))
        val c = ReminderScheduler.requestCode(8, LocalTime.of(8, 0))
        assertEquals(3, setOf(a, b, c).size)
    }
}
