package com.kutumbam.app

import com.kutumbam.app.locker.LabRecord
import com.kutumbam.app.locker.MedRecord
import com.kutumbam.app.locker.Source
import com.kutumbam.app.locker.SourceKind
import com.kutumbam.app.locker.SupplyInfo
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.visit.GrowthPoint
import com.kutumbam.app.visit.PrepInput
import com.kutumbam.app.visit.VisitPrep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class VisitPrepTest {
    private val today = LocalDate.of(2026, 9, 21)
    private fun rx(doc: Long, date: LocalDate) = Source(SourceKind.PRESCRIPTION, "Prescription · ${date.dayOfMonth} ${date.month.name.take(3)}", doc)
    private fun med(name: String, strength: String?, times: Int, doc: Long, start: LocalDate, days: Int? = 30, sos: Boolean = false, supply: SupplyInfo? = null) =
        MedRecord(name, strength, List(times) { LocalTime.of(8 + it * 6, 0) }, sos, MealTiming.UNSPECIFIED, days, start, rx(doc, start), supply)
    private fun lab(test: String, key: String, v: Double, low: Double?, high: Double?, date: LocalDate, std: Boolean = false) =
        LabRecord(test, key, v, "mg/dL", low, high, std, date, Source(SourceKind.LAB_REPORT, "Lab report · ${date.dayOfMonth} ${date.month.name.take(3)}", date.toEpochDay()))

    private fun adult(meds: List<MedRecord> = emptyList(), labs: List<LabRecord> = emptyList()) =
        PrepInput("Amma", false, LocalDate.of(1962, 3, 1), today, meds, labs, null, emptyList())

    @Test fun flaggedLabValueBecomesAQuestionWithItsHistory() {
        val sheet = VisitPrep.build(adult(labs = listOf(
            lab("Fasting Blood Glucose", "fg", 112.0, 70.0, 100.0, LocalDate.of(2026, 8, 14)),
            lab("Fasting Blood Glucose", "fg", 118.0, 70.0, 100.0, LocalDate.of(2026, 9, 12)),
            lab("Total Cholesterol", "tc", 190.0, null, 200.0, LocalDate.of(2026, 9, 12)),
        )))
        val labs = sheet.sections.first { it.heading == "Lab results to discuss" }
        assertEquals(1, labs.items.size)   // cholesterol is in range
        val q = labs.items[0].text
        assertTrue(q.startsWith("Amma's Fasting Blood Glucose was 118 mg/dL on 12 Sep 2026, above the range printed on the report (70 – 100 mg/dL)."))
        assertTrue(q.contains("Before that it was 112 mg/dL on 14 Aug 2026."))
        assertTrue(q.endsWith("What does this mean for Amma, and is any follow-up test or change needed?"))
        assertEquals("Lab report · 12 SEP", labs.items[0].source)
    }

    @Test fun inRangeOldOrUnjudgedValuesAreNotRaised() {
        val sheet = VisitPrep.build(adult(labs = listOf(
            lab("TSH", "tsh", 6.8, null, null, LocalDate.of(2026, 9, 12)),                    // no range: no judgement
            lab("Old", "old", 300.0, 0.0, 100.0, LocalDate.of(2025, 1, 1)),                   // long ago
            lab("Fine", "fine", 90.0, 70.0, 100.0, LocalDate.of(2026, 9, 12)),
        )))
        assertNull(sheet.sections.firstOrNull { it.heading == "Lab results to discuss" })
    }

    @Test fun standardReferenceIsSaidOutLoud() {
        val q = VisitPrep.build(adult(labs = listOf(lab("TSH", "tsh", 6.8, 0.4, 4.0, LocalDate.of(2026, 9, 12), std = true))))
            .sections.first { it.heading == "Lab results to discuss" }.items[0].text
        assertTrue(q.contains("a standard reference range (the report printed none)"))
        assertTrue(q.contains("It is the only reading saved."))
    }

    @Test fun medicineChangesBetweenTheLastTwoPrescriptions() {
        val old = LocalDate.of(2026, 8, 14); val new = LocalDate.of(2026, 9, 14)
        val sheet = VisitPrep.build(adult(meds = listOf(
            med("Amlodipine", "5 mg", 1, 1, old), med("Metformin", "500 mg", 2, 1, old), med("Paracetamol", "650 mg", 0, 1, old, null, sos = true),
            med("Amlodipine", "10 mg", 1, 2, new), med("Metformin", "500 mg", 3, 2, new), med("Telmisartan", "40 mg", 1, 2, new),
        )))
        val m = sheet.sections.first { it.heading == "Medicines" }.items.map { it.text }
        assertTrue(m.any { it.startsWith("Telmisartan 40 mg is new on the 14 Sep 2026 prescription.") })
        assertTrue(m.any { it.startsWith("Paracetamol 650 mg was on the 14 Aug 2026 prescription but is not on the latest one.") })
        assertTrue(m.any { it.startsWith("Amlodipine's strength changed from 5 mg to 10 mg.") })
        assertTrue(m.any { it.startsWith("Metformin changed from twice a day to three times a day.") })
    }

    @Test fun brandSwapIsNotReportedAsANewMedicine() {
        val sheet = VisitPrep.build(adult(meds = listOf(
            med("Glycomet", "500 mg", 2, 1, LocalDate.of(2026, 8, 14)), med("Metformin", "500 mg", 2, 2, LocalDate.of(2026, 9, 14)),
        )))
        val s = sheet.sections.first { it.heading == "Medicines" }
        assertTrue(s.items.none { it.text.contains("is new on") || it.text.contains("not on the latest") })
        assertNotNull(s.note)
    }

    @Test fun oneOrNoPrescriptionsHasNothingToCompare() {
        val one = VisitPrep.build(adult(meds = listOf(med("Metformin", "500 mg", 2, 1, LocalDate.of(2026, 9, 14)))))
        assertEquals("Only one prescription is saved, so there are no changes to compare.", one.sections.first { it.heading == "Medicines" }.note)
        val none = VisitPrep.build(adult())
        assertEquals("No medicines are saved yet.", none.sections.first { it.heading == "Medicines" }.note)
    }

    @Test fun endedCoursesDuplicatesAndLowSupplyAreRaised() {
        val supply = SupplyInfo("runs out in 3 days (24 Sep)", "about 6 tablets left", today, true)
        val sheet = VisitPrep.build(adult(meds = listOf(
            med("Pantoprazole", "40 mg", 2, 1, LocalDate.of(2026, 8, 1), days = 14),
            med("Metformin", "500 mg", 2, 1, LocalDate.of(2026, 9, 14), supply = supply),
            med("Glycomet", "500 mg", 2, 1, LocalDate.of(2026, 9, 14)),
        )))
        val m = sheet.sections.first { it.heading == "Medicines" }.items.map { it.text }
        assertTrue(m.any { it.startsWith("The 14-day course of Pantoprazole 40 mg ended on 14 Aug 2026.") })
        assertTrue(m.any { it.contains("both contain metformin. Is Amma meant to take both?") })
        assertTrue(m.any { it.startsWith("Metformin 500 mg runs out in 3 days (24 Sep). Can Amma get a new prescription") })
    }

    // ---- child

    private val dob = LocalDate.of(2026, 7, 20)
    private fun child(growth: List<GrowthPoint>, given: Map<String, LocalDate> = emptyMap(), meds: List<MedRecord> = emptyList()) =
        PrepInput("Aarav", true, dob, today, meds, emptyList(), ImmunizationEngine.plan(dob, given, today), growth)

    @Test fun childWithoutMeasurementsIsAskedToBeMeasured() {
        val g = VisitPrep.build(child(emptyList())).sections.first { it.heading == "Growth" }
        assertEquals(1, g.items.size)
        assertTrue(g.items[0].text.startsWith("No weight or length has been recorded for Aarav."))
    }

    @Test fun growthTrendsAreStatedAsFactsAndAskedAbout() {
        val g = VisitPrep.build(child(listOf(
            GrowthPoint(LocalDate.of(2026, 8, 10), 4.2, 54.0, null), GrowthPoint(LocalDate.of(2026, 9, 18), 5.1, 57.5, null),
        ), meds = listOf(med("Vitamin D drops", null, 1, 1, LocalDate.of(2026, 9, 1))))).sections.first { it.heading == "Growth" }.items.map { it.text }
        assertTrue(g.any { it == "Weight went from 4.2 kg (10 Aug) to 5.1 kg (18 Sep). Is this on track for Aarav's age?" })
        assertTrue(g.any { it.startsWith("Height went from 54 cm (10 Aug) to 57.5 cm (18 Sep).") })
        assertTrue(g.any { it.contains("weight was last recorded as 5.1 kg on 18 Sep 2026. Can the doctor confirm each medicine's dose still suits that weight?") })
        assertFalse(g.any { it.startsWith("The last recorded measurement") })   // measured 3 days ago
    }

    @Test fun weightLossAndStaleMeasurementsAreFlaggedForTheDoctor() {
        val input = child(listOf(
            GrowthPoint(LocalDate.of(2026, 6, 10), 5.0, null, null), GrowthPoint(LocalDate.of(2026, 7, 20), 4.8, null, null),
        )).copy(dob = LocalDate.of(2026, 3, 1))
        val g = VisitPrep.build(input).sections.first { it.heading == "Growth" }.items.map { it.text }
        assertTrue(g.any { it.startsWith("Weight went down from 5 kg (10 Jun) to 4.8 kg (20 Jul).") && it.endsWith("Should this be checked?") })
        assertTrue(g.any { it.startsWith("The last recorded measurement was on 20 Jul 2026 (2 months ago).") })
    }

    @Test fun vaccinationsListOverdueDueAndNext() {
        val v = VisitPrep.build(child(emptyList(), given = mapOf("BCG" to dob, "OPV-0" to dob, "HEPB-0" to dob))).sections.first { it.heading == "Vaccinations" }
        assertTrue(v.items.isNotEmpty())
        assertTrue(v.items.all { it.text.endsWith("?") })
    }

    @Test fun textVersionIsNumberedAndSayItIsNotAdvice() {
        val text = VisitPrep.build(child(emptyList())).asText()
        assertTrue(text.startsWith("Pediatrician visit prep: Aarav"))
        assertTrue(text.contains("not medical advice"))
        assertTrue(text.contains("\n1. "))
        assertTrue(text.contains("vaccination card"))
    }
}
