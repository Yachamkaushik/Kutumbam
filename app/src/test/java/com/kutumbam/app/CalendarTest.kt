package com.kutumbam.app

import com.kutumbam.app.calendar.CalendarEngine
import com.kutumbam.app.calendar.CalendarEventType
import com.kutumbam.app.data.DoseLog
import com.kutumbam.app.data.FamilyMember
import com.kutumbam.app.data.FollowUpVisit
import com.kutumbam.app.data.ImmunizationRecord
import com.kutumbam.app.data.MedicineEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarTest {
    private val today = LocalDate.of(2026, 9, 21)

    private val elder = FamilyMember(
        id = 1L, name = "Amma", relation = "Mother", dateOfBirth = "1960-05-10",
        selfOperatesPhone = false, isSelf = false,
    )
    private val self = FamilyMember(
        id = 2L, name = "Karan", relation = "Self", dateOfBirth = "1995-08-15",
        selfOperatesPhone = true, isSelf = true,
    )
    private val child = FamilyMember(
        id = 3L, name = "Aarav", relation = "Child", dateOfBirth = "2026-08-10", // 6 weeks old around 2026-09-21
        selfOperatesPhone = false, isSelf = false,
    )

    private val elderMed = MedicineEntity(
        id = 101L, documentId = 1L, memberId = 1L, name = "Telmisartan", strength = "40 mg",
        form = "Tablet", frequencyCode = "OD", timesCsv = "08:00", mealTiming = "AFTER_FOOD",
        durationDays = 30, startDate = "2026-09-20", confirmedByUser = true,
        stockCount = 10, stockAsOf = "2026-09-20T08:00:00", unitsCsv = "1.0",
    )

    private val selfMed = MedicineEntity(
        id = 102L, documentId = 2L, memberId = 2L, name = "Paracetamol", strength = "650 mg",
        form = "Tablet", frequencyCode = "BD", timesCsv = "08:00,20:00", mealTiming = "AFTER_FOOD",
        durationDays = 5, startDate = "2026-09-21", confirmedByUser = true,
        stockCount = 6, stockAsOf = "2026-09-21T08:00:00", unitsCsv = "1.0,1.0",
    )

    @Test
    fun generatesMergedDosesAcrossFamilyMembers() {
        val members = listOf(elder, self)
        val meds = listOf(elderMed, selfMed)
        val logs = listOf(
            DoseLog(medicineId = 101L, date = "2026-09-21", time = "08:00", status = "taken", loggedAt = 1000L),
        )

        val events = CalendarEngine.generate(
            members = members, medicines = meds, doseLogs = logs, visits = emptyList(),
            immunizations = emptyMap(), today = today, from = today, to = today,
        )

        val todayEvents = events[today] ?: emptyList()
        val doses = todayEvents.filter { it.type == CalendarEventType.DOSE }

        // 1 dose for Amma (08:00), 2 doses for Karan (08:00, 20:00) = 3 total doses
        assertEquals(3, doses.size)

        val ammaDose = doses.first { it.memberId == elder.id }
        assertEquals("Telmisartan 40 mg", ammaDose.title)
        assertEquals("Amma", ammaDose.memberName)
        assertTrue(ammaDose.isElder)
        assertTrue(ammaDose.isTaken)

        val karanDoses = doses.filter { it.memberId == self.id }
        assertEquals(2, karanDoses.size)
        assertEquals("You", karanDoses.first().memberName)
        assertTrue(karanDoses.first().isSelf)
        assertFalse(karanDoses.first().isTaken)
    }

    @Test
    fun generatesRefillWarningsOnDepletionDate() {
        val members = listOf(self)
        // Self has 6 tablets, taking 2 a day (08:00, 20:00) starting 21 Sep at 08:00
        // Day 21: takes 08:00 and 20:00 (left 4)
        // Day 22: takes 08:00 and 20:00 (left 2)
        // Day 23: takes 08:00 and 20:00 (left 0)
        // Runs out on 24 Sep
        val meds = listOf(selfMed)

        val events = CalendarEngine.generate(
            members = members, medicines = meds, doseLogs = emptyList(), visits = emptyList(),
            immunizations = emptyMap(), today = today, from = today, to = today.plusDays(10),
        )

        val allItems = events.values.flatten()
        val refills = allItems.filter { it.type == CalendarEventType.REFILL }
        assertTrue("Must generate at least one refill event", refills.isNotEmpty())

        val runOutEvent = refills.firstOrNull { it.isUrgent }
        assertNotNull("Must have urgent run-out refill event", runOutEvent)
        assertEquals(LocalDate.of(2026, 9, 24), runOutEvent?.date)
    }

    @Test
    fun generatesDoctorFollowUpVisits() {
        val visitDate = today.plusDays(3)
        val visit = FollowUpVisit(
            id = 501L, memberId = elder.id, doctorOrClinic = "Dr. K. Sharma (Cardiology)",
            date = visitDate.toString(), time = "11:00", reason = "BP review", completed = false,
        )

        val events = CalendarEngine.generate(
            members = listOf(elder), medicines = emptyList(), doseLogs = emptyList(),
            visits = listOf(visit), immunizations = emptyMap(), today = today,
            from = today, to = today.plusDays(7),
        )

        val dayEvents = events[visitDate] ?: emptyList()
        val visitEvent = dayEvents.firstOrNull { it.type == CalendarEventType.DOCTOR_VISIT }
        assertNotNull(visitEvent)
        assertEquals("Follow-up: Dr. K. Sharma (Cardiology)", visitEvent?.title)
        assertTrue(visitEvent?.subtitle?.contains("BP review") == true)
        assertEquals("Amma", visitEvent?.memberName)
    }

    @Test
    fun generatesChildImmunizationMilestoneVisits() {
        // Child born 2026-08-10. 6-week milestone is 6 weeks after birth = 2026-09-21 (today!)
        val events = CalendarEngine.generate(
            members = listOf(child), medicines = emptyList(), doseLogs = emptyList(),
            visits = emptyList(), immunizations = emptyMap(), today = today,
            from = today, to = today.plusDays(7),
        )

        val todayEvents = events[today] ?: emptyList()
        val vaccineEvent = todayEvents.firstOrNull { it.type == CalendarEventType.VACCINE_VISIT }
        assertNotNull("Should generate 6-week vaccine milestone visit", vaccineEvent)
        assertTrue(vaccineEvent?.title?.contains("OPV-1") == true || vaccineEvent?.title?.contains("Pentavalent-1") == true)
        assertEquals("Aarav", vaccineEvent?.memberName)
        assertTrue(vaccineEvent?.isChild == true)
    }

    @Test
    fun memberFilterIsolatesHouseholdEvents() {
        val members = listOf(elder, self)
        val meds = listOf(elderMed, selfMed)

        val ammaOnly = CalendarEngine.generate(
            members = members, medicines = meds, doseLogs = emptyList(),
            visits = emptyList(), immunizations = emptyMap(), today = today,
            from = today, to = today, filterMemberId = elder.id,
        )

        val ammaEvents = ammaOnly[today] ?: emptyList()
        assertTrue(ammaEvents.all { it.memberId == elder.id })
        assertEquals(1, ammaEvents.size)
    }

    @Test
    fun indicatorsSummarizeEventTypesCorrectly() {
        val members = listOf(elder)
        val visitDate = today.plusDays(2)
        val visit = FollowUpVisit(
            id = 502L, memberId = elder.id, doctorOrClinic = "Dr. Clinic",
            date = visitDate.toString(),
        )

        val events = CalendarEngine.generate(
            members = members, medicines = listOf(elderMed), doseLogs = emptyList(),
            visits = listOf(visit), immunizations = emptyMap(), today = today,
            from = today, to = today.plusDays(5),
        )

        val indicators = CalendarEngine.buildIndicators(events)
        assertTrue(indicators[today]?.hasDose == true)
        assertFalse(indicators[today]?.hasVisit == true)

        assertTrue(indicators[visitDate]?.hasVisit == true)
        assertTrue(indicators[visitDate]?.hasDose == true)
    }
}
