package com.kutumbam.app

import com.kutumbam.app.parse.PackQuantityParser
import com.kutumbam.app.parse.PrescriptionParser
import com.kutumbam.app.parse.RefillPredictor
import com.kutumbam.app.parse.RefillStatus
import com.kutumbam.app.parse.RefillText
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RefillTest {
    private val day = LocalDate.of(2026, 9, 21)

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

    @Test fun runOutDateFollowsDosesPerDay() {
        val e = RefillPredictor.estimate(30, day, 2, null, day)!!
        assertEquals(day.plusDays(15), e.runOut)
        assertEquals(15, e.daysLeft)
        assertEquals(RefillStatus.OK, e.status)
    }

    @Test fun remindsWithinLeadTimeAndAfter() {
        fun status(daysIn: Long) = RefillPredictor.estimate(20, day, 2, null, day.plusDays(daysIn))!!.status
        assertEquals(RefillStatus.OK, status(0))
        assertEquals(RefillStatus.OK, status(6))
        assertEquals(RefillStatus.SOON, status(7))
        assertEquals(RefillStatus.SOON, status(9))
        assertEquals(RefillStatus.TODAY, status(10))
        assertEquals(RefillStatus.OUT, status(11))
    }

    @Test fun noRefillWhenTheCourseEndsFirst() {
        val e = RefillPredictor.estimate(30, day, 1, day.plusDays(10), day.plusDays(9))!!
        assertEquals(RefillStatus.COURSE_ENDS, e.status)
        assertFalse(e.needsReminder)
    }

    @Test fun asNeededMedicinesAreNotPredicted() {
        assertNull(RefillPredictor.dosesPerDay("SOS", ""))
        assertEquals(3, RefillPredictor.dosesPerDay("TDS", "08:00,14:00,20:00"))
        assertNull(RefillPredictor.estimate(10, day, 0, null, day))
    }

    @Test fun digestNamesTheDateAndSortsByUrgency() {
        val soon = RefillPredictor.estimate(6, day, 2, null, day)!!   // runs out in 3 days
        val out = RefillPredictor.estimate(2, day, 1, null, day.plusDays(5))!!
        val fine = RefillPredictor.estimate(60, day, 1, null, day)!!
        val text = RefillText.digest(listOf("Metformin 500 mg" to soon, "Pantoprazole" to out, "Amlodipine" to fine))!!
        assertEquals(
            "Pantoprazole should have run out on 23 Sep.\nMetformin 500 mg runs out in 3 days (24 Sep).\nTime to get a refill.",
            text,
        )
        assertNull(RefillText.digest(listOf("Amlodipine" to fine)))
    }
}
