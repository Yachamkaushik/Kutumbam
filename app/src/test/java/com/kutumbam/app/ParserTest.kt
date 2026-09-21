package com.kutumbam.app

import com.kutumbam.app.parse.DocumentClassifier
import com.kutumbam.app.parse.DocumentDates
import com.kutumbam.app.parse.DocumentParser
import com.kutumbam.app.parse.DocumentType
import com.kutumbam.app.parse.DurationParser
import com.kutumbam.app.parse.FrequencyCode
import com.kutumbam.app.parse.FrequencyParser
import com.kutumbam.app.parse.LabReportParser
import com.kutumbam.app.parse.MealParser
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.parse.PrescriptionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ParserTest {

    private val prescription = """
        Dr. R. Sharma MBBS, MD
        City Care Clinic, Hyderabad
        Date: 14/08/2026
        Name: Lakshmi Devi   Age: 62 Y  F
        Dx: Type 2 DM, HTN
        Rx
        1. Tab. Metformin 500 mg  1-0-1  after food  x 30 days
        2. Tab Amlodipine 5mg OD morning x 30 days
        3) Cap. Pantoprazole 40 mg  BD  before food  for 14 days
        4. Tab. Paracetamol 650mg SOS
        Syp. Cough Relief 5 ml TDS x 5 days
        Advice: Low salt diet. Review after 1 month
    """.trimIndent()

    @Test fun prescriptionMedicines() {
        val meds = PrescriptionParser.parse(prescription)
        assertEquals(5, meds.size)

        val metformin = meds[0]
        assertEquals("Metformin", metformin.name)
        assertEquals("500 mg", metformin.strength)
        assertEquals("Tablet", metformin.form)
        assertEquals(FrequencyCode.BD, metformin.frequency?.code)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), metformin.frequency?.times)
        assertEquals(MealTiming.AFTER_FOOD, metformin.meal)
        assertEquals(30, metformin.durationDays)

        val amlodipine = meds[1]
        assertEquals("Amlodipine", amlodipine.name)
        assertEquals("5 mg", amlodipine.strength)
        assertEquals(FrequencyCode.OD, amlodipine.frequency?.code)
        assertEquals(30, amlodipine.durationDays)

        val panto = meds[2]
        assertEquals("Pantoprazole", panto.name)
        assertEquals(FrequencyCode.BD, panto.frequency?.code)
        assertEquals(MealTiming.BEFORE_FOOD, panto.meal)
        assertEquals(14, panto.durationDays)

        val para = meds[3]
        assertEquals("Paracetamol", para.name)
        assertEquals("650 mg", para.strength)
        assertEquals(FrequencyCode.SOS, para.frequency?.code)
        assertTrue(para.frequency!!.times.isEmpty())

        assertEquals(FrequencyCode.TDS, meds[4].frequency?.code)
        assertEquals("Syrup", meds[4].form)
    }

    @Test fun headerAndAdviceLinesAreNotMedicines() {
        val names = PrescriptionParser.parse(prescription).map { it.name }
        assertTrue(names.none { it.contains("Sharma") || it.contains("Advice") || it.contains("Lakshmi") })
    }

    @Test fun dosingOnNextLineIsMerged() {
        val meds = PrescriptionParser.parse("Tab. Telmisartan 40 mg\n1-0-0 before food x 1 month")
        assertEquals(1, meds.size)
        assertEquals(FrequencyCode.OD, meds[0].frequency?.code)
        assertEquals(MealTiming.BEFORE_FOOD, meds[0].meal)
        assertEquals(30, meds[0].durationDays)
    }

    @Test fun doseGridShapes() {
        fun code(s: String) = FrequencyParser.parse(s)?.code
        assertEquals(FrequencyCode.OD, code("1-0-0"))
        assertEquals(FrequencyCode.OD, code("0-1-0"))
        assertEquals(FrequencyCode.HS, code("0-0-1"))
        assertEquals(FrequencyCode.BD, code("1-0-1"))
        assertEquals(FrequencyCode.TDS, code("1-1-1"))
        assertEquals(FrequencyCode.QID, code("1-1-1-1"))
        assertEquals(FrequencyCode.OD, code("½-0-0"))
        assertNull(FrequencyParser.parse("visit on 10-12-2024"))
        assertNull(FrequencyParser.parse("0-0-0"))
    }

    @Test fun shorthandWords() {
        fun code(s: String) = FrequencyParser.parse(s)?.code
        assertEquals(FrequencyCode.BD, code("twice daily"))
        assertEquals(FrequencyCode.TDS, code("Tab X thrice a day"))
        assertEquals(FrequencyCode.QID, code("QID"))
        assertEquals(FrequencyCode.HS, code("at bedtime"))
        assertEquals(FrequencyCode.HS, code("HS"))
        assertEquals(FrequencyCode.OD, code("once daily"))
        assertEquals(FrequencyCode.SOS, code("SOS for pain"))
        assertEquals(FrequencyCode.TDS, FrequencyParser.parse("every 8 hours")?.code)
        assertEquals(FrequencyCode.QID, FrequencyParser.parse("Q6H")?.code)
    }

    @Test fun mealAndDuration() {
        assertEquals(MealTiming.BEFORE_FOOD, MealParser.parse("A/C"))
        assertEquals(MealTiming.AFTER_FOOD, MealParser.parse("P.C."))
        assertEquals(MealTiming.AFTER_FOOD, MealParser.parse("after meals"))
        assertEquals(MealTiming.EMPTY_STOMACH, MealParser.parse("on empty stomach"))
        assertEquals(MealTiming.WITH_FOOD, MealParser.parse("with milk"))
        assertEquals(MealTiming.UNSPECIFIED, MealParser.parse("Tab Dolo 650"))
        assertEquals(30, DurationParser.parse("x 30 days"))
        assertEquals(14, DurationParser.parse("for 2 weeks"))
        assertEquals(90, DurationParser.parse("x 3 months"))
        assertEquals(5, DurationParser.parse("x5/7"))
        assertNull(DurationParser.parse("Tab Dolo 650"))
        assertNull(DurationParser.parse("date 5/7/2026"))
    }

    private val labReport = """
        SUNRISE DIAGNOSTIC LABORATORY
        Patient Name : Ramesh Kumar        Age/Sex : 64 Y / M
        Collected on : 02-Sep-2026    Reported on : 02-Sep-2026
        Test Name        Result     Unit        Reference Range
        HAEMATOLOGY
        Haemoglobin  11.2 L  g/dL  13.0 - 17.0
        Total WBC Count  12,500 H  cells/cumm  4,000 - 11,000
        Platelet Count  2.5  lakhs/cumm  1.5 - 4.5
        BIOCHEMISTRY
        Fasting Blood Glucose  142  mg/dL  70 - 100
        HbA1c  7.4  %  4.0 - 5.6
        Total Cholesterol  212  mg/dL  Desirable: < 200
        HDL Cholesterol  38  mg/dL  > 40
        Serum Creatinine  1.1  mg/dL  0.7 - 1.3
        TSH  6.8  uIU/mL  0.4 - 4.0
        Vitamin D 25 Hydroxy  28.5  ng/mL  30 - 100
        Page 1 of 2
    """.trimIndent()

    @Test fun labRowsWithPrintedRanges() {
        val v = LabReportParser.parse(labReport).associateBy { it.testName }

        val hb = v.getValue("Haemoglobin")
        assertEquals(11.2, hb.value, 0.0)
        assertEquals("g/dL", hb.unit)
        assertEquals(13.0, hb.rangeLow!!, 0.0)
        assertEquals(17.0, hb.rangeHigh!!, 0.0)

        val wbc = v.getValue("Total WBC Count")
        assertEquals(12500.0, wbc.value, 0.0)
        assertEquals(4000.0, wbc.rangeLow!!, 0.0)
        assertEquals(11000.0, wbc.rangeHigh!!, 0.0)

        assertEquals(2.5, v.getValue("Platelet Count").value, 0.0)
        assertEquals("lakhs/cumm", v.getValue("Platelet Count").unit)

        val glucose = v.getValue("Fasting Blood Glucose")
        assertEquals(142.0, glucose.value, 0.0)
        assertEquals(70.0, glucose.rangeLow!!, 0.0)

        assertEquals("%", v.getValue("HbA1c").unit)
        assertEquals(5.6, v.getValue("HbA1c").rangeHigh!!, 0.0)

        val chol = v.getValue("Total Cholesterol")
        assertNull(chol.rangeLow)
        assertEquals(200.0, chol.rangeHigh!!, 0.0)

        val hdl = v.getValue("HDL Cholesterol")
        assertEquals(40.0, hdl.rangeLow!!, 0.0)
        assertNull(hdl.rangeHigh)

        assertEquals(6.8, v.getValue("TSH").value, 0.0)
        assertEquals("uIU/mL", v.getValue("TSH").unit)
        assertEquals(28.5, v.getValue("Vitamin D 25 Hydroxy").value, 0.0)
    }

    @Test fun labParserIgnoresHeaderNoise() {
        val names = LabReportParser.parse(labReport).map { it.testName }
        assertTrue(names.none { it.startsWith("Patient") || it.startsWith("Collected") || it.startsWith("Page") || it.startsWith("Test Name") })
        assertEquals(10, names.size)
    }

    @Test fun classifiesDocuments() {
        assertEquals(DocumentType.PRESCRIPTION, DocumentClassifier.classify(prescription))
        assertEquals(DocumentType.LAB_REPORT, DocumentClassifier.classify(labReport))
        assertEquals(DocumentType.VACCINATION_CARD, DocumentClassifier.classify("Child Immunization Card\nBCG  OPV 0  Hepatitis B birth dose\nPentavalent 1"))
        assertEquals(DocumentType.UNKNOWN, DocumentClassifier.classify("Grocery list: milk, eggs"))
    }

    @Test fun documentDatePrefersVisitOverBirthDate() {
        assertEquals(LocalDate.of(2026, 8, 14), DocumentDates.find(prescription))
        assertEquals(LocalDate.of(2026, 9, 2), DocumentDates.find(labReport))
        assertEquals(LocalDate.of(2026, 9, 2), DocumentDates.find("DOB: 01/01/1962\nCollected: 2 Sep 2026"))
    }

    @Test fun endToEnd() {
        val doc = DocumentParser.parse(prescription)
        assertEquals(DocumentType.PRESCRIPTION, doc.type)
        assertNotNull(doc.date)
        assertEquals(5, doc.medicines.size)
    }

    @Test fun commonOcrSlipsInUnitsAreCorrected() {
        assertEquals("uIU/mL", LabReportParser.parseLine("TSH  6.8  ulU/mL")?.unit)
        assertEquals("mg/dL", LabReportParser.parseLine("Fasting Blood Glucose  118  mg/dl  70 - 100")?.unit)
        assertEquals("mIU/L", LabReportParser.parseLine("TSH  2.1  mlU/L  0.4 - 4.0")?.unit)
    }
}
