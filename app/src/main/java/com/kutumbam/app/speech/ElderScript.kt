package com.kutumbam.app.speech

import com.kutumbam.app.parse.MealTiming
import java.time.LocalTime
import java.util.Locale

enum class AppLanguage(val code: String, val locale: Locale, val label: String, val voiceName: String) {
    TELUGU("te", Locale.forLanguageTag("te-IN"), "తెలుగు", "Telugu"),
    HINDI("hi", Locale.forLanguageTag("hi-IN"), "हिंदी", "Hindi"),
    ENGLISH("en", Locale.forLanguageTag("en-IN"), "English", "English");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

data class ScriptDose(val time: LocalTime, val medicine: String, val meal: MealTiming)

/**
 * What the phone says aloud. Plain templates over already-confirmed data, no model involved, so the
 * wording is predictable and instant. Medicine names stay in Latin script, as printed on the strip.
 */
object ElderScript {

    fun build(lang: AppLanguage, memberName: String, pending: List<ScriptDose>): String {
        val lines = mutableListOf<String>()
        lines += greeting(lang, memberName)
        if (pending.isEmpty()) lines += none(lang)
        else {
            pending.sortedBy { it.time }.forEach { lines += dose(lang, it) }
            lines += closing(lang)
        }
        return lines.joinToString("\n")
    }

    private fun greeting(l: AppLanguage, name: String) = when (l) {
        AppLanguage.TELUGU -> "నమస్కారం. $name గారికి ఈ రోజు తీసుకోవాల్సిన మందులు."
        AppLanguage.HINDI -> "नमस्ते। $name जी की आज की दवाइयाँ।"
        AppLanguage.ENGLISH -> "Hello. Here are $name's medicines for today."
    }

    private fun none(l: AppLanguage) = when (l) {
        AppLanguage.TELUGU -> "ఈ రోజుకు ఇంకా మందులు ఏమీ లేవు."
        AppLanguage.HINDI -> "आज के लिए कोई और दवा नहीं है।"
        AppLanguage.ENGLISH -> "There are no more medicines left today."
    }

    private fun closing(l: AppLanguage) = when (l) {
        AppLanguage.TELUGU -> "అంతే."
        AppLanguage.HINDI -> "बस इतना ही।"
        AppLanguage.ENGLISH -> "That is all."
    }

    private fun dose(l: AppLanguage, d: ScriptDose): String {
        val meal = meal(l, d.meal)
        val tail = if (meal.isEmpty()) "" else ", $meal"
        return when (l) {
            AppLanguage.TELUGU -> "${time(l, d.time)}, ${d.medicine} తీసుకోండి$tail."
            AppLanguage.HINDI -> "${time(l, d.time)}, ${d.medicine} लीजिए$tail।"
            AppLanguage.ENGLISH -> "At ${time(l, d.time)}, take ${d.medicine}$tail."
        }
    }

    private fun meal(l: AppLanguage, m: MealTiming): String = when (l) {
        AppLanguage.TELUGU -> when (m) {
            MealTiming.BEFORE_FOOD -> "భోజనానికి ముందు"
            MealTiming.AFTER_FOOD -> "భోజనం తర్వాత"
            MealTiming.WITH_FOOD -> "భోజనంతో పాటు"
            MealTiming.EMPTY_STOMACH -> "ఖాళీ కడుపుతో"
            MealTiming.UNSPECIFIED -> ""
        }
        AppLanguage.HINDI -> when (m) {
            MealTiming.BEFORE_FOOD -> "खाने से पहले"
            MealTiming.AFTER_FOOD -> "खाने के बाद"
            MealTiming.WITH_FOOD -> "खाने के साथ"
            MealTiming.EMPTY_STOMACH -> "खाली पेट"
            MealTiming.UNSPECIFIED -> ""
        }
        AppLanguage.ENGLISH -> when (m) {
            MealTiming.BEFORE_FOOD -> "before food"
            MealTiming.AFTER_FOOD -> "after food"
            MealTiming.WITH_FOOD -> "with food"
            MealTiming.EMPTY_STOMACH -> "on an empty stomach"
            MealTiming.UNSPECIFIED -> ""
        }
    }

    private fun time(l: AppLanguage, t: LocalTime): String {
        val h12 = (t.hour % 12).let { if (it == 0) 12 else it }
        val m = t.minute
        return when (l) {
            AppLanguage.TELUGU -> {
                val period = when (t.hour) { in 4..11 -> "ఉదయం"; in 12..15 -> "మధ్యాహ్నం"; in 16..19 -> "సాయంత్రం"; else -> "రాత్రి" }
                if (m == 0) "$period $h12 గంటలకు" else "$period $h12 గంటల $m నిమిషాలకు"
            }
            AppLanguage.HINDI -> {
                val period = when (t.hour) { in 4..11 -> "सुबह"; in 12..15 -> "दोपहर"; in 16..19 -> "शाम"; else -> "रात" }
                if (m == 0) "$period $h12 बजे" else "$period $h12 बजकर $m मिनट पर"
            }
            AppLanguage.ENGLISH -> {
                val suffix = if (t.hour < 12) "AM" else "PM"
                if (m == 0) "$h12 $suffix" else "$h12 ${"%02d".format(m)} $suffix"
            }
        }
    }
}
