package com.kutumbam.app

import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.speech.AppLanguage
import com.kutumbam.app.speech.ElderScript
import com.kutumbam.app.speech.ScriptDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ElderScriptTest {
    private val doses = listOf(
        ScriptDose(LocalTime.of(21, 0), "Amlodipine 5 mg", MealTiming.UNSPECIFIED),
        ScriptDose(LocalTime.of(8, 30), "Metformin 500 mg", MealTiming.AFTER_FOOD),
    )

    @Test fun englishIsOrderedByTimeAndKeepsMedicineNames() {
        val lines = ElderScript.build(AppLanguage.ENGLISH, "Amma", doses).lines()
        assertEquals("Hello. Here are Amma's medicines for today.", lines[0])
        assertEquals("At 8 30 AM, take Metformin 500 mg, after food.", lines[1])
        assertEquals("At 9 PM, take Amlodipine 5 mg.", lines[2])
        assertEquals("That is all.", lines[3])
    }

    @Test fun teluguSpeaksTimeMealAndKeepsLatinName() {
        val text = ElderScript.build(AppLanguage.TELUGU, "Amma", doses)
        assertTrue(text.contains("ఉదయం 8 గంటల 30 నిమిషాలకు, Metformin 500 mg తీసుకోండి, భోజనం తర్వాత."))
        assertTrue(text.contains("రాత్రి 9 గంటలకు, Amlodipine 5 mg తీసుకోండి."))
    }

    @Test fun hindiSpeaksTimeAndMeal() {
        val text = ElderScript.build(AppLanguage.HINDI, "Amma", doses)
        assertTrue(text.contains("सुबह 8 बजकर 30 मिनट पर, Metformin 500 mg लीजिए, खाने के बाद।"))
        assertTrue(text.contains("रात 9 बजे, Amlodipine 5 mg लीजिए।"))
    }

    @Test fun nothingLeftSaysSo() {
        assertTrue(ElderScript.build(AppLanguage.ENGLISH, "Amma", emptyList()).contains("no more medicines left today"))
        assertEquals(2, ElderScript.build(AppLanguage.TELUGU, "Amma", emptyList()).lines().size)
    }

    @Test fun noonAndMidnightUseTwelve() {
        val text = ElderScript.build(AppLanguage.ENGLISH, "Nanna", listOf(ScriptDose(LocalTime.NOON, "X", MealTiming.UNSPECIFIED), ScriptDose(LocalTime.MIDNIGHT, "Y", MealTiming.UNSPECIFIED)))
        assertTrue(text.contains("At 12 AM, take Y."))
        assertTrue(text.contains("At 12 PM, take X."))
    }
}
