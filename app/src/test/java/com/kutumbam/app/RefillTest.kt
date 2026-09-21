package com.kutumbam.app

import com.kutumbam.app.parse.PackQuantityParser
import com.kutumbam.app.parse.PrescriptionParser
import com.kutumbam.app.parse.RefillPredictor
import com.kutumbam.app.parse.RefillStatus
import com.kutumbam.app.parse.RefillText
import com.kutumbam.app.parse.DoseSlot
import com.kutumbam.app.parse.DoseUnits
import com.kutumbam.app.parse.FrequencyParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefillTest {
    private val day = LocalDate.of(2026, 9, 21)
    private val start = day.atStartOfDay()
    private val morning = LocalTime.of(8, 0)
    private val night = LocalTime.of(20, 0)
    private fun bd(a: Double = 1.0, b: Double = 1.0) = listOf(DoseSlot(morning, a), DoseSlot(night, b))
    private fun at(days: Long, hour: Int = 9) = day.plusDays(days).atTime(hour, 0)

    @Test fun readsLabelledPackSizes() {
        assertEquals(10, PackQuantityParser.parse("Strip of 10 tablets"))
        assertEquals(30, PackQuantityParser.parse("Qty: 30"))
        assertEquals(60, PackQuantityParser.parse("Dispense 60"))
        assertEquals(15, PackQuantityParser.parse("15 tabs"))
    }

    @Test fun aDoseIsNotAPack() {
        assertNull(PackQuantityParser.parse("Take 1 tablet twice daily"))
        assertNull(PackQuantityParser.parse("2 tablets at night"))
        assertNull(PackQuantityParser.parse("x 30 days"))
    }

    @Test fun prescriptionCarriesQuantityAndKeepsTheName() {
        val m = PrescriptionParser.parse("Tab Metformin 500mg 1-0-1 after food x 30 days Qty 60").single()
        assertEquals("Metformin", m.name)
        assertEquals(60, m.quantity)
        assertEquals(30, m.durationDays)
        val n = PrescriptionParser.parse("Tab Amlodipine 5mg\n1-0-0 x 30 days\nStrip of 15").single()
        assertEquals("Amlodipine", n.name)
        assertEquals(15, n.quantity)
    }

    @Test fun readsTabletsPerDoseFromTheDosingPattern() {
        assertEquals(listOf(1.0, 2.0), FrequencyParser.parse("1-0-2")!!.units)
        assertEquals(listOf(0.5, 0.5), FrequencyParser.parse("½-0-½")!!.units)
        assertEquals(listOf(1.0, 1.0), FrequencyParser.parse("1-0-1")!!.units)
        assertEquals("2 tablets", DoseUnits.phrase(2.0, "Tablet"))
        assertEquals("½ tablet", DoseUnits.phrase(0.5, null))
        assertNull(DoseUnits.phrase(1.0, "Tablet"))
        assertNull(DoseUnits.phrase(2.0, "Syrup"))
        assertEquals(listOf(1.0, 2.0), DoseUnits.fromCsv("1,2", 2))
        assertEquals(listOf(1.0, 1.0, 1.0), DoseUnits.fromCsv("1,2", 3))
    }

    @Test fun runOutDateFollowsTheSchedule() {
        val e = RefillPredictor.estimate(30, start, bd(), null, start)!!
        assertEquals(day.plusDays(15), e.runOut)
        assertEquals(15, e.daysLeft)
        assertEquals(RefillStatus.OK, e.status)
    }

    @Test fun tabletsPerDoseShortenTheSupply() {
        // 1 in the morning and 2 at night is 3 a day: 30 tablets last 10 days, not 15.
        assertEquals(day.plusDays(10), RefillPredictor.estimate(30, start, bd(1.0, 2.0), null, start)!!.runOut)
        // Half tablets stretch it.
        assertEquals(day.plusDays(20), RefillPredictor.estimate(10, start, listOf(DoseSlot(morning, 0.5)), null, start)!!.runOut)
    }

    @Test fun countsFromTheMomentOfTheCountNotMidnight() {
        // Counted at 2 pm with 4 tablets: 8 pm today, 8 am and 8 pm tomorrow use three, the last is gone by 8 am on the 23rd.
        val counted = day.atTime(14, 0)
        val e = RefillPredictor.estimate(4, counted, bd(), null, counted)!!
        assertEquals(day.plusDays(2), e.runOut)
        val later = RefillPredictor.estimate(4, counted, bd(), null, at(1))!!
        assertEquals(2.0, later.tabletsLeft, 0.0)   // 8 pm and 8 am have passed
    }

    @Test fun remindsWithinLeadTimeAndAfter() {
        fun status(daysIn: Long) = RefillPredictor.estimate(20, start, bd(), null, at(daysIn))!!.status
        assertEquals(RefillStatus.OK, status(0))
        assertEquals(RefillStatus.OK, status(6))
        assertEquals(RefillStatus.SOON, status(7))
        assertEquals(RefillStatus.SOON, status(9))
        assertEquals(RefillStatus.TODAY, status(10))
        assertEquals(RefillStatus.OUT, status(11))
    }

    @Test fun noRefillWhenTheCourseEndsFirst() {
        val e = RefillPredictor.estimate(30, start, listOf(DoseSlot(morning, 1.0)), day.plusDays(10), at(9))!!
        assertEquals(RefillStatus.COURSE_ENDS, e.status)
        assertFalse(e.needsReminder)
    }

    @Test fun asNeededMedicinesAreNotPredicted() {
        assertTrue(RefillPredictor.slots("SOS", emptyList(), emptyList()).isEmpty())
        assertNull(RefillPredictor.estimate(10, start, emptyList(), null, start))
        assertEquals(3, RefillPredictor.slots("TDS", listOf(morning, LocalTime.NOON, night), emptyList()).size)
    }

    @Test fun suggestsTheCourseAsAStartingCount() {
        assertEquals(60, RefillPredictor.courseSupply(bd(), 30))
        assertEquals(90, RefillPredictor.courseSupply(bd(1.0, 2.0), 30))
        assertNull(RefillPredictor.courseSupply(bd(), null))
    }

    @Test fun oldCountsAskForARecount() {
        val e = RefillPredictor.estimate(200, start, bd(), null, start)!!
        assertFalse(e.countIsStale(at(29)))
        assertTrue(e.countIsStale(at(30)))
    }

    @Test fun digestNamesTheDateAndSortsByUrgency() {
        val soon = RefillPredictor.estimate(6, start, bd(), null, at(0))!!   // runs out in 3 days
        val out = RefillPredictor.estimate(2, start, listOf(DoseSlot(morning, 1.0)), null, at(5))!!
        val fine = RefillPredictor.estimate(60, start, listOf(DoseSlot(morning, 1.0)), null, at(0))!!
        val text = RefillText.digest(listOf("Metformin 500 mg" to soon, "Pantoprazole" to out, "Amlodipine" to fine))!!
        assertEquals(
            "Pantoprazole should have run out on 23 Sep.\nMetformin 500 mg runs out in 3 days (24 Sep).\nTime to get a refill.",
            text,
        )
        assertNull(RefillText.digest(listOf("Amlodipine" to fine)))
    }
}
