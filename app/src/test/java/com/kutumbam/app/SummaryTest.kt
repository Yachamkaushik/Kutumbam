package com.kutumbam.app

import com.kutumbam.app.export.SummaryBuilder
import com.kutumbam.app.locker.LabRecord
import com.kutumbam.app.locker.MedRecord
import com.kutumbam.app.locker.Source
import com.kutumbam.app.locker.SourceKind
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.visit.PrepInput
import com.kutumbam.app.visit.VisitPrep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SummaryTest {
    private val today = LocalDate.of(2026, 9, 21)
    private val rx = Source(SourceKind.PRESCRIPTION, "Prescription · 14 Sep 2026", 1)
    private fun lab(test: String, key: String, v: Double, low: Double?, high: Double?, std: Boolean = false) =
        LabRecord(test, key, v, "mg/dL", low, high, std, LocalDate.of(2026, 9, 12), Source(SourceKind.LAB_REPORT, "Lab report · 12 Sep 2026", 2))

    private val input = PrepInput(
        "Amma", false, LocalDate.of(1962, 3, 1), today,
        medicines = listOf(
            MedRecord("Metformin", "500 mg", listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), false, MealTiming.AFTER_FOOD, 30, LocalDate.of(2026, 9, 14), rx, units = listOf(1.0, 2.0)),
            MedRecord("Paracetamol", "650 mg", emptyList(), true, MealTiming.UNSPECIFIED, null, LocalDate.of(2026, 9, 14), rx),
            MedRecord("Old Syrup", null, listOf(LocalTime.of(9, 0)), false, MealTiming.UNSPECIFIED, 5, LocalDate.of(2026, 8, 1), rx),
        ),
        labs = listOf(
            lab("Fasting Blood Glucose", "fg", 118.0, 70.0, 100.0), lab("Total Cholesterol", "tc", 190.0, null, 200.0), lab("TSH", "tsh", 6.8, null, null),
        ),
        immunization = null, growth = emptyList(),
    )

    @Test fun summaryListsCurrentMedicinesWithTheirDoses() {
        val c = SummaryBuilder.build(input, VisitPrep.build(input))
        assertEquals("Health summary: Amma", c.title)
        val meds = c.sections.first { it.heading == "Medicines now" }.lines
        assertEquals(2, meds.size)   // the finished syrup course is left out
        assertEquals("Metformin 500 mg · 8:00 AM, 8:00 PM (2) · after food · until 13 Oct 2026", meds[0])
        assertEquals("Paracetamol 650 mg · only when needed", meds[1])
    }

    @Test fun labsShowFlaggedFirstAndNeverJudgeAMissingRange() {
        val labs = SummaryBuilder.build(input, VisitPrep.build(input)).sections.first { it.heading == "Latest lab values" }.lines
        assertTrue(labs[0].startsWith("Fasting Blood Glucose: 118 mg/dL (12 Sep 2026) · above range on report 70 – 100 mg/dL"))
        assertTrue(labs.any { it.startsWith("Total Cholesterol") && it.contains("within range on report") })
        assertTrue(labs.any { it.startsWith("TSH") && it.endsWith("no range printed") })
    }

    @Test fun footerLeavesRoomToWriteAndSaysNotAdvice() {
        val c = SummaryBuilder.build(input, VisitPrep.build(input))
        assertTrue(c.footer[0].contains("Allergies"))
        assertTrue(c.footer.last().contains("Not medical advice"))
        assertTrue(c.sections.last().heading == "Questions for this visit")
    }
}
