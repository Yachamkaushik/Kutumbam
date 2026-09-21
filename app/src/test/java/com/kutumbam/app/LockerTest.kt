package com.kutumbam.app

import com.kutumbam.app.locker.AnswerRules
import com.kutumbam.app.locker.LabRecord
import com.kutumbam.app.locker.LockerData
import com.kutumbam.app.locker.MedRecord
import com.kutumbam.app.locker.Retrieval
import com.kutumbam.app.locker.Source
import com.kutumbam.app.locker.SourceKind
import com.kutumbam.app.locker.Topic
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.MealTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LockerTest {
    private val today = LocalDate.of(2026, 9, 21)
    private val rx = Source(SourceKind.PRESCRIPTION, "Prescription · 14 Sep 2026")

    private fun lab(test: String, key: String, v: Double, unit: String, low: Double?, high: Double?, date: LocalDate, std: Boolean = false) =
        LabRecord(test, key, v, unit, low, high, std, date, Source(SourceKind.LAB_REPORT, "Lab report · ${date.dayOfMonth} ${date.month.name.take(3)}", date.toEpochDay()))

    private val data = LockerData(
        person = "Amma", today = today,
        medicines = listOf(
            MedRecord("Metformin", "500 mg", listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), false, MealTiming.AFTER_FOOD, 30, LocalDate.of(2026, 9, 14), rx),
            MedRecord("Amlodipine", "5 mg", listOf(LocalTime.of(21, 0)), false, MealTiming.UNSPECIFIED, 30, LocalDate.of(2026, 9, 14), rx),
            MedRecord("Paracetamol", "650 mg", emptyList(), true, MealTiming.UNSPECIFIED, null, LocalDate.of(2026, 9, 14), rx),
            MedRecord("Old Syrup", null, listOf(LocalTime.of(9, 0)), false, MealTiming.UNSPECIFIED, 5, LocalDate.of(2026, 8, 1), rx),
        ),
        labs = listOf(
            lab("Fasting Blood Glucose", "fasting_glucose", 90.0, "mg/dL", 70.0, 100.0, LocalDate.of(2026, 6, 12)),
            lab("Fasting Blood Glucose", "fasting_glucose", 112.0, "mg/dL", 70.0, 100.0, LocalDate.of(2026, 8, 14)),
            lab("Fasting Blood Glucose", "fasting_glucose", 118.0, "mg/dL", 70.0, 100.0, LocalDate.of(2026, 9, 12)),
            lab("HbA1c", "hba1c", 7.8, "%", null, 6.5, LocalDate.of(2026, 9, 12)),
            lab("TSH", "tsh", 6.8, "uIU/mL", 0.4, 4.0, LocalDate.of(2026, 9, 12), std = true),
        ),
        immunization = null,
    )

    @Test fun latestSugarReadingComesFromTheReportWithItsSource() {
        val r = Retrieval.retrieve("What was Amma's last sugar reading?", data)
        assertTrue(Topic.LAB in r.topics)
        assertTrue(r.directAnswer.startsWith("Amma's latest Fasting Blood Glucose was 118 mg/dL on 12 Sep 2026, above the range printed on the report (70 – 100 mg/dL)."))
        assertTrue(r.directAnswer.contains("Earlier: 90 (12 Jun), 112 (14 Aug)."))
        assertTrue(r.directAnswer.contains("talk to a doctor"))
        assertTrue(r.sources.all { it.kind == SourceKind.LAB_REPORT })
        assertFalse(r.facts.any { it.text.contains("HbA1c") }) // "sugar" means fasting glucose, not HbA1c
    }

    @Test fun aNamedTestFindsOnlyThatTest() {
        val r = Retrieval.retrieve("what is her hba1c", data)
        assertEquals(1, r.facts.size)
        assertTrue(r.directAnswer.contains("7.8 %"))
    }

    @Test fun standardReferenceIsLabelledAsSuch() {
        val r = Retrieval.retrieve("thyroid result?", data)
        assertTrue(r.directAnswer.contains("standard reference range"))
    }

    @Test fun morningMedicinesFilterByTime() {
        val r = Retrieval.retrieve("What does Amma take in the morning?", data)
        assertTrue(Topic.MEDICINE in r.topics)
        assertTrue(r.directAnswer.contains("Metformin 500 mg: 8:00 AM and 8:00 PM, after food."))
        assertFalse(r.directAnswer.contains("Amlodipine"))
        assertFalse(r.directAnswer.contains("Old Syrup")) // that course has ended
    }

    @Test fun nightMedicinesAndNamedMedicines() {
        assertTrue(Retrieval.retrieve("what tablets at night", data).directAnswer.contains("Amlodipine 5 mg: 9:00 PM."))
        val named = Retrieval.retrieve("When does she take metformin?", data)
        assertEquals(1, named.facts.size)
        assertTrue(named.facts[0].text.contains("for 30 days from 14 Sep 2026"))
    }

    @Test fun sosMedicinesSayOnlyWhenNeeded() {
        assertTrue(Retrieval.retrieve("what is paracetamol for", data).directAnswer.contains("Paracetamol 650 mg: only when needed."))
    }

    @Test fun oldCoursesCanStillBeAskedAboutByName() {
        val r = Retrieval.retrieve("what about the old syrup", data)
        assertTrue(r.facts.single().text.contains("course ended"))
    }

    @Test fun unknownQuestionsFindNothingAndSaySo() {
        val r = Retrieval.retrieve("What is the capital of France?", data)
        assertTrue(r.facts.isEmpty())
        assertTrue(r.directAnswer.startsWith("I couldn't find that in Amma's stored records"))
    }

    @Test fun adviceQuestionsAreRecognised() {
        listOf("Should I stop metformin?", "Can I double the dose", "is it safe to skip amlodipine", "What does high HbA1c mean?", "does she have diabetes", "any side effects?")
            .forEach { assertTrue(it, Retrieval.isAdviceQuestion(it)) }
        listOf("What was Amma's last sugar reading?", "When does she take metformin?", "which vaccines are due").forEach { assertFalse(it, Retrieval.isAdviceQuestion(it)) }
        assertTrue(AnswerRules.refusal("Amma").contains("ask the doctor"))
    }

    @Test fun vaccineQuestionsUseTheScheduleEngine() {
        val dob = LocalDate.of(2026, 7, 20)
        val plan = ImmunizationEngine.plan(dob, mapOf("BCG" to dob.plusDays(1), "OPV-0" to dob.plusDays(1)), today)
        val child = data.copy(person = "Aarav", medicines = emptyList(), labs = emptyList(), immunization = plan)
        val due = Retrieval.retrieve("Which vaccines are due for Aarav?", child)
        assertTrue(Topic.VACCINE in due.topics)
        assertTrue(due.directAnswer.contains("Overdue: Hepatitis B (birth dose)"))
        assertTrue(due.directAnswer.contains("Due now:"))
        val bcg = Retrieval.retrieve("did he get bcg", child)
        assertTrue(bcg.directAnswer.contains("BCG: given on 21 Jul 2026."))
        assertTrue(bcg.sources.single().label.startsWith("Vaccination schedule"))
    }

    @Test fun groundingRejectsInventedNumbers() {
        val r = Retrieval.retrieve("What was Amma's last sugar reading?", data)
        val facts = r.factsText
        val q = "What was Amma's last sugar reading?"
        assertTrue(AnswerRules.isGrounded("Her fasting glucose was 118 mg/dL on 12 Sep 2026, above the printed range of 70 – 100.", facts, q, "21 Sep 2026"))
        assertFalse(AnswerRules.isGrounded("Her fasting glucose was 125 mg/dL on 12 Sep 2026.", facts, q, "21 Sep 2026"))
        assertFalse(AnswerRules.isGrounded("", facts, q, "21 Sep 2026"))
        assertTrue(AnswerRules.isGrounded("It was taken once, about 3 times a month.", facts, q, "21 Sep 2026")) // single digits are not treated as facts
    }

    @Test fun promptNumbersEveryRecord() {
        val r = Retrieval.retrieve("what does she take in the morning", data)
        val p = AnswerRules.userPrompt(r, "what does she take in the morning")
        assertTrue(p.startsWith("Records:\n1. Metformin 500 mg"))
        assertTrue(p.endsWith("Question: what does she take in the morning"))
    }
}
