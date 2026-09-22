package com.kutumbam.app.eldermode

import com.kutumbam.app.data.DoseLog
import com.kutumbam.app.data.LabValueEntity
import com.kutumbam.app.data.MedicineEntity
import com.kutumbam.app.eldermode.adapters.ElderDataAdapter
import com.kutumbam.app.eldermode.models.DoseStatus
import com.kutumbam.app.eldermode.models.ElderDose
import com.kutumbam.app.eldermode.models.ElderHealthItem
import com.kutumbam.app.eldermode.models.ElderStrings
import com.kutumbam.app.eldermode.voice.ElderVoiceScripts
import com.kutumbam.app.locker.Retrieval
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.speech.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ElderModeTest {

    @Test
    fun testElderDataAdapterBuildsDosesAndTracksStatus() {
        val today = LocalDate.now()
        val meds = listOf(
            MedicineEntity(
                id = 1L,
                documentId = 10L,
                memberId = 100L,
                name = "Metformin",
                strength = "500 mg",
                form = "tablet",
                frequencyCode = "BD",
                timesCsv = "08:00,20:00",
                mealTiming = "AFTER_FOOD",
                durationDays = 30,
                startDate = today.toString(),
                confirmedByUser = true,
            ),
        )

        val logs = listOf(
            DoseLog(
                id = 1L,
                medicineId = 1L,
                date = today.toString(),
                time = "08:00",
                status = "taken",
                loggedAt = System.currentTimeMillis(),
            )
        )

        val doses = ElderDataAdapter.buildElderDoses(meds, logs, today)
        assertEquals(2, doses.size)

        // 8:00 AM dose should be marked TAKEN
        val morningDose = doses.first { it.time == LocalTime.of(8, 0) }
        assertEquals("Metformin", morningDose.name)
        assertEquals("500 mg", morningDose.strength)
        assertEquals(DoseStatus.TAKEN, morningDose.status)
        assertTrue(morningDose.isTaken)

        // 8:00 PM dose should be NOT_TAKEN
        val nightDose = doses.first { it.time == LocalTime.of(20, 0) }
        assertEquals(DoseStatus.NOT_TAKEN, nightDose.status)
        assertFalse(nightDose.isTaken)
    }

    @Test
    fun testFindNextDoseCalculatesUpcomingCorrectly() {
        val doses = listOf(
            ElderDose(1L, LocalTime.of(8, 0), "Metformin", "500 mg", MealTiming.AFTER_FOOD, "", DoseStatus.TAKEN),
            ElderDose(2L, LocalTime.of(14, 0), "Paracetamol", "650 mg", MealTiming.AFTER_FOOD, "", DoseStatus.NOT_TAKEN),
            ElderDose(3L, LocalTime.of(20, 0), "Amlodipine", "5 mg", MealTiming.BEFORE_FOOD, "", DoseStatus.NOT_TAKEN),
        )

        // Given current time is 13:30, next pending dose should be 14:00 (Paracetamol)
        val (next, countdown) = ElderDataAdapter.findNextDose(doses, LocalTime.of(13, 30))
        assertNotNull(next)
        assertEquals("Paracetamol", next?.name)
        assertEquals("In 30 mins", countdown)
    }

    @Test
    fun testElderDataAdapterBuildsHealthItemsAndFlags() {
        val labs = listOf(
            LabValueEntity(
                id = 1L,
                documentId = 10L,
                memberId = 100L,
                testName = "Fasting Blood Sugar",
                value = 145.0,
                unit = "mg/dL",
                printedRangeLow = 70.0,
                printedRangeHigh = 100.0,
                rangeText = "70 - 100 mg/dL",
                flagged = true,
                date = "2026-09-12",
                rangeSource = "printed",
            )
        )

        val items = ElderDataAdapter.buildHealthItems(labs)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Fasting Blood Sugar", item.testName)
        assertTrue(item.isFlagged)
        assertEquals("145 mg/dL", item.valueText)
    }

    @Test
    fun testVoiceScriptsMyDayTeluguKeepsLatinNames() {
        val doses = listOf(
            ElderDose(1L, LocalTime.of(8, 30), "Metformin", "500 mg", MealTiming.AFTER_FOOD, "", DoseStatus.NOT_TAKEN),
        )
        val script = ElderVoiceScripts.buildMyDay(
            lang = AppLanguage.TELUGU,
            memberName = "అమ్మ",
            doses = doses,
            nextDose = doses.first(),
        )

        assertTrue(script.contains("శుభోదయం, అమ్మ గారు."))
        assertTrue(script.contains("Metformin 500 mg"))
        assertTrue(script.contains("ఉదయం 8 గంటల 30 నిమిషాలకు"))
        assertTrue(script.contains("భోజనం తర్వాత"))
    }

    @Test
    fun testVoiceScriptsMyDayHindi() {
        val doses = listOf(
            ElderDose(1L, LocalTime.of(20, 0), "Amlodipine", "5 mg", MealTiming.BEFORE_FOOD, "", DoseStatus.NOT_TAKEN),
        )
        val script = ElderVoiceScripts.buildMyDay(
            lang = AppLanguage.HINDI,
            memberName = "माताजी",
            doses = doses,
            nextDose = doses.first(),
        )

        assertTrue(script.contains("नमस्ते, माताजी जी।"))
        assertTrue(script.contains("Amlodipine 5 mg"))
        assertTrue(script.contains("खाने से पहले"))
    }

    @Test
    fun testSingleDoseSpokenEnglish() {
        val dose = ElderDose(1L, LocalTime.of(8, 0), "Metformin", "500 mg", MealTiming.AFTER_FOOD, "", DoseStatus.NOT_TAKEN)
        val script = ElderVoiceScripts.buildSingleDose(AppLanguage.ENGLISH, dose)
        assertEquals("Metformin 500 mg. Take at 8 AM, after food.", script)
    }

    @Test
    fun testLabExplanationDoctorAdvisory() {
        val flagged = ElderHealthItem(
            testName = "Blood Sugar",
            value = 160.0,
            valueText = "160 mg/dL",
            unit = "mg/dL",
            rangeText = "70-100",
            isFlagged = true,
            date = "2026-09-12",
        )
        val englishExplanation = ElderVoiceScripts.buildLabExplanation(AppLanguage.ENGLISH, flagged)
        assertTrue(englishExplanation.contains("outside the range shown on the report"))
        assertTrue(englishExplanation.contains("Please discuss this with your doctor"))

        val teluguExplanation = ElderVoiceScripts.buildLabExplanation(AppLanguage.TELUGU, flagged)
        assertTrue(teluguExplanation.contains("సాధారణ పరిధికి వెలుపల ఉంది"))
        assertTrue(teluguExplanation.contains("మీ వైద్యుడితో మాట్లాడండి"))
    }

    @Test
    fun testMedicalAdviceQuestionsTriggerRefusal() {
        assertTrue(Retrieval.isAdviceQuestion("Should I stop taking Metformin?"))
        assertTrue(Retrieval.isAdviceQuestion("Can I double my dose?"))
        assertTrue(Retrieval.isAdviceQuestion("Is it dangerous?"))
        assertFalse(Retrieval.isAdviceQuestion("What medicines do I have today?"))
        assertFalse(Retrieval.isAdviceQuestion("When do I take Metformin?"))
    }

    @Test
    fun testStringsDictionaryCompleteness() {
        for (lang in AppLanguage.entries) {
            assertTrue(ElderStrings.today(lang).isNotBlank())
            assertTrue(ElderStrings.myMedicines(lang).isNotBlank())
            assertTrue(ElderStrings.askKutumbam(lang).isNotBlank())
            assertTrue(ElderStrings.myHealth(lang).isNotBlank())
            assertTrue(ElderStrings.myDay(lang).isNotBlank())
            assertTrue(ElderStrings.emergency(lang).isNotBlank())
            assertTrue(ElderStrings.readMyDay(lang).isNotBlank())
            assertTrue(ElderStrings.taken(lang).isNotBlank())
            assertTrue(ElderStrings.notTaken(lang).isNotBlank())
            assertTrue(ElderStrings.voiceListeningTitle(lang).isNotBlank())
            assertTrue(ElderStrings.voiceProcessingTitle(lang).isNotBlank())
            assertTrue(ElderStrings.doctorDiscussionAdvice(lang).isNotBlank())
        }
    }
}
