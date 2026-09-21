package com.kutumbam.app

import com.kutumbam.app.parse.TestNames
import com.kutumbam.app.parse.TrendPoint
import com.kutumbam.app.parse.TrendSummary
import com.kutumbam.app.parse.describeRange
import com.kutumbam.app.parse.formatNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrendTest {
    private fun pts(vararg v: Double) = v.mapIndexed { i, x -> TrendPoint(LocalDate.of(2026, 6 + i, 12), x) }

    @Test fun sameTestUnderDifferentNamesSharesAKey() {
        val k = TestNames.key("Fasting Blood Glucose")
        assertEquals("fasting_glucose", k)
        assertEquals(k, TestNames.key("Glucose - Fasting"))
        assertEquals(k, TestNames.key("FBS"))
        assertEquals("hba1c", TestNames.key("HbA1c"))
        assertEquals("hba1c", TestNames.key("Glycosylated Haemoglobin"))
        assertEquals("total_cholesterol", TestNames.key("Total Cholesterol"))
        assertEquals("hdl_cholesterol", TestNames.key("HDL Cholesterol"))
        assertEquals("tsh", TestNames.key("TSH"))
        assertEquals("glucose_random", TestNames.key("Glucose Random"))
    }

    @Test fun fallbackNeedsAMatchingUnit() {
        assertNotNull(TestNames.fallback("Fasting Blood Glucose", "mg/dL"))
        assertNull(TestNames.fallback("Fasting Blood Glucose", "mmol/L"))
        assertNull(TestNames.fallback("Fasting Blood Glucose", null))
        assertNotNull(TestNames.fallback("TSH", "µIU/mL"))
        assertNotNull(TestNames.fallback("TSH", "mIU/L"))
        assertNotNull(TestNames.fallback("TSH", "ulU/mL"))
        assertNull(TestNames.fallback("Haemoglobin", "g/dL"))
    }

    @Test fun directionUsesAThreePercentBand() {
        assertEquals("UP", TrendSummary.direction(pts(90.0, 105.0, 118.0)).name)
        assertEquals("DOWN", TrendSummary.direction(pts(118.0, 100.0)).name)
        assertEquals("STEADY", TrendSummary.direction(pts(100.0, 102.0)).name)
        assertEquals("STEADY", TrendSummary.direction(pts(100.0)).name)
    }

    @Test fun templateRestatesOnlyStoredNumbers() {
        val text = TrendSummary.template("Amma", "fasting glucose", pts(90.0, 105.0, 112.0, 118.0), "mg/dL", 70.0, 100.0)
        assertEquals(
            "Amma's fasting glucose has risen over the last four reports, from 90 to 118 mg/dL. " +
                "The latest reading is above the range printed on the report (70 – 100 mg/dL). Please talk to a doctor about it.",
            text,
        )
    }

    @Test fun templateHandlesOneReportAndNoRange() {
        val one = TrendSummary.template("Amma", "TSH", pts(2.0), "uIU/mL", 0.4, 4.0)
        assertTrue(one.contains("Only one report so far"))
        assertTrue(one.contains("within the range"))
        assertFalse(one.contains("talk to a doctor"))
        val none = TrendSummary.template("Amma", "X", pts(1.0, 2.0), null, null, null)
        assertTrue(none.contains("No range was printed"))
    }

    @Test fun modelAnswersMustStateTheLatestValue() {
        val p = pts(90.0, 118.0)
        assertTrue(TrendSummary.isFaithful("Amma's glucose rose from 90 to 118 mg/dL, above the printed range.", p))
        assertFalse(TrendSummary.isFaithful("Amma's glucose rose a lot and is high.", p))
        assertFalse(TrendSummary.isFaithful("118", p))
    }

    @Test fun rangeFormatting() {
        assertEquals("70 – 100 mg/dL", describeRange(70.0, 100.0, "mg/dL"))
        assertEquals("below 200 mg/dL", describeRange(null, 200.0, "mg/dL"))
        assertEquals("above 40 mg/dL", describeRange(40.0, null, "mg/dL"))
        assertNull(describeRange(null, null, "mg/dL"))
        assertEquals("7.8", formatNumber(7.8))
        assertEquals("118", formatNumber(118.0))
    }
}
