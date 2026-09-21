package com.kutumbam.app

import com.kutumbam.app.vitals.Reading
import com.kutumbam.app.vitals.VitalKind
import com.kutumbam.app.vitals.VitalRules
import com.kutumbam.app.vitals.VitalSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class VitalsTest {
    private val today = LocalDate.of(2026, 9, 21)
    private var next = 1L
    private fun bp(day: Int, s: Double, d: Double) = Reading(next++, LocalDate.of(2026, 9, day), LocalTime.of(8, 0), VitalKind.BP, s, d, null)
    private fun sugar(day: Int, v: Double, ctx: String) = Reading(next++, LocalDate.of(2026, 9, day), LocalTime.of(7, 30), VitalKind.SUGAR, v, null, ctx)
    private fun weight(y: Int, m: Int, d: Int, v: Double) = Reading(next++, LocalDate.of(y, m, d), LocalTime.of(7, 0), VitalKind.WEIGHT, v, null, null)

    @Test fun readingsAreCheckedBeforeTheyAreSaved() {
        assertNull(VitalRules.validateBp(120.0, 80.0))
        assertTrue(VitalRules.validateBp(80.0, 120.0)!!.contains("larger"))
        assertTrue(VitalRules.validateBp(null, 80.0)!!.contains("both numbers"))
        assertTrue(VitalRules.validateBp(400.0, 80.0) != null)
        assertNull(VitalRules.validateSugar(112.0)); assertTrue(VitalRules.validateSugar(5.0) != null)
        assertNull(VitalRules.validateWeight(68.4)); assertTrue(VitalRules.validateWeight(0.4) != null)
    }

    @Test fun nothingIsJudgedWithoutALimitTheUserSet() {
        val r = bp(20, 150.0, 95.0)
        assertEquals(VitalRules.Status.NO_LIMIT, VitalRules.status(r, emptyMap()))
        assertEquals(VitalRules.Status.ABOVE, VitalRules.status(r, mapOf(VitalRules.BP_SYSTOLIC to 130.0, VitalRules.BP_DIASTOLIC to 80.0)))
        assertEquals(VitalRules.Status.WITHIN, VitalRules.status(bp(20, 118.0, 76.0), mapOf(VitalRules.BP_SYSTOLIC to 130.0, VitalRules.BP_DIASTOLIC to 80.0)))
        // a limit for one number only judges that number
        assertEquals(VitalRules.Status.WITHIN, VitalRules.status(bp(20, 118.0, 99.0), mapOf(VitalRules.BP_SYSTOLIC to 130.0)))
    }

    @Test fun sugarLimitsAreKeptPerContext() {
        val limits = mapOf(VitalRules.SUGAR_FASTING to 100.0)
        assertEquals(VitalRules.Status.ABOVE, VitalRules.status(sugar(20, 118.0, "Fasting"), limits))
        assertEquals(VitalRules.Status.NO_LIMIT, VitalRules.status(sugar(20, 190.0, "After meal"), limits))
        assertEquals(VitalRules.Status.NO_LIMIT, VitalRules.status(sugar(20, 190.0, "Random"), limits))
    }

    @Test fun summaryStatesLatestRangeAndHowManyWereAboveTheLimit() {
        val readings = listOf(bp(15, 118.0, 76.0), bp(18, 138.0, 86.0), bp(20, 142.0, 90.0), bp(1, 160.0, 100.0))   // the 1 Sep one is outside 14 days
        val lines = VitalSummary.lines(readings, mapOf(VitalRules.BP_SYSTOLIC to 130.0, VitalRules.BP_DIASTOLIC to 80.0), today)
        assertEquals("Blood pressure: latest 142/90 mmHg (20 Sep). 3 readings in 14 days, from 118/76 to 142/90; 2 above the limit set (130/80 mmHg).", lines.single())
        val noLimit = VitalSummary.lines(listOf(bp(20, 142.0, 90.0)), emptyMap(), today).single()
        assertEquals("Blood pressure: latest 142/90 mmHg (20 Sep). 1 reading in 14 days. No limit set.", noLimit)
    }

    @Test fun weightChangeIsReportedOnlyWhenItIsAtLeastAKilo() {
        val lines = VitalSummary.lines(listOf(weight(2026, 8, 20, 74.0), weight(2026, 9, 18, 72.4)), emptyMap(), today)
        assertEquals("Weight: 72.4 kg (18 Sep), 1.6 kg lower than 74 kg on 20 Aug.", lines.single())
        assertEquals("Weight: 72.4 kg (18 Sep).", VitalSummary.lines(listOf(weight(2026, 9, 1, 72.0), weight(2026, 9, 18, 72.4)), emptyMap(), today).single())
    }

    @Test fun questionsAskTheDoctorWithoutInterpreting() {
        val readings = listOf(bp(18, 138.0, 86.0), bp(20, 118.0, 76.0), sugar(19, 118.0, "Fasting"))
        val limits = mapOf(VitalRules.BP_SYSTOLIC to 130.0, VitalRules.BP_DIASTOLIC to 80.0)
        val q = VitalSummary.questions(readings, limits, today, self = true, person = "Kaushik")
        assertEquals("1 of 2 home blood pressure readings were above the limit set. Does anything need to change for me?", q[0])
        assertEquals("What blood sugar range should I aim for at home, before and after meals?", q[1])
        assertTrue(VitalSummary.questions(emptyList(), limits, today, false, "Amma").isEmpty())
    }
}
