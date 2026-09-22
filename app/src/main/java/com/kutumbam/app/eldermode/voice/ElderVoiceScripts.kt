package com.kutumbam.app.eldermode.voice

import com.kutumbam.app.eldermode.models.ElderDose
import com.kutumbam.app.eldermode.models.ElderHealthItem
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.speech.AppLanguage
import java.time.LocalTime

/**
 * Generates clear, predictable spoken sentences for TTS in Telugu, Hindi, and English.
 * Pure deterministic templates over stored confirmed data: zero hallucination,
 * strict non-diagnostic framing, and medicine names always preserved in Latin script.
 */
object ElderVoiceScripts {

    fun buildMyDay(
        lang: AppLanguage,
        memberName: String,
        doses: List<ElderDose>,
        nextDose: ElderDose?,
        flaggedLab: ElderHealthItem? = null,
    ): String {
        val lines = mutableListOf<String>()
        val total = doses.size
        val pending = doses.filterNot { it.isTaken }

        // 1. Greeting & Day Summary
        when (lang) {
            AppLanguage.TELUGU -> {
                lines += "శుభోదయం, $memberName గారు."
                if (total == 0) {
                    lines += "ఈ రోజుకు తీసుకోవాల్సిన మందులు ఏమీ లేవు."
                } else if (pending.isEmpty()) {
                    lines += "ఈ రోజుకు సంబంధించిన అన్ని మందులు పూర్తయ్యాయి."
                } else {
                    lines += "ఈ రోజు మీకు మొత్తం $total మందులు ఉన్నాయి. ఇంకా ${pending.size} మందులు తీసుకోవాలి."
                }
            }
            AppLanguage.HINDI -> {
                lines += "नमस्ते, $memberName जी।"
                if (total == 0) {
                    lines += "आज के लिए कोई दवा नहीं है।"
                } else if (pending.isEmpty()) {
                    lines += "आज की सभी दवाइयाँ पूरी हो चुकी हैं।"
                } else {
                    lines += "आज आपकी कुल $total दवाइयाँ हैं। अभी ${pending.size} दवाइयाँ लेनी बाकी हैं।"
                }
            }
            AppLanguage.ENGLISH -> {
                lines += "Good morning, $memberName."
                if (total == 0) {
                    lines += "You have no medicines scheduled for today."
                } else if (pending.isEmpty()) {
                    lines += "All your medicines for today are completed."
                } else {
                    lines += "You have $total medicines today. ${pending.size} are remaining."
                }
            }
        }

        // 2. Next Medicine
        if (nextDose != null && !nextDose.isTaken) {
            lines += formatNextDose(lang, nextDose)
        }

        // 3. Flagged Lab Reminder (if present)
        if (flaggedLab != null && flaggedLab.isFlagged) {
            when (lang) {
                AppLanguage.TELUGU -> {
                    lines += "గమనిక: మీ ఇటీవలి ${flaggedLab.testName} రీడింగ్ సాధారణ పరిధికి భిన్నంగా ఉంది. మీ వైద్యుడితో మాట్లాడండి."
                }
                AppLanguage.HINDI -> {
                    lines += "ध्यान दें: आपकी हाल की ${flaggedLab.testName} रिपोर्ट सामान्य सीमा से अलग है। कृपया डॉक्टर से चर्चा करें।"
                }
                AppLanguage.ENGLISH -> {
                    lines += "Note: Your recent ${flaggedLab.testName} reading is outside the report's normal range. Please discuss this with your doctor."
                }
            }
        }

        return lines.joinToString("\n\n")
    }

    fun buildSingleDose(lang: AppLanguage, dose: ElderDose): String {
        val meal = mealPhrase(lang, dose.meal)
        val time = formatTime(lang, dose.time)
        val nameWithStrength = "${dose.name} ${dose.strength}".trim()

        return when (lang) {
            AppLanguage.TELUGU -> "$nameWithStrength. $time కు తీసుకోండి$meal."
            AppLanguage.HINDI -> "$nameWithStrength. $time पर लीजिए$meal।"
            AppLanguage.ENGLISH -> "$nameWithStrength. Take at $time$meal."
        }
    }

    fun buildDoseTakenConfirmation(lang: AppLanguage, medicineName: String): String = when (lang) {
        AppLanguage.TELUGU -> "సరే. $medicineName వేసుకున్నట్లు గుర్తించబడింది."
        AppLanguage.HINDI -> "ठीक है। $medicineName ले ली गई दर्ज कर ली गई है।"
        AppLanguage.ENGLISH -> "Okay. Marked $medicineName as taken."
    }

    fun buildDoseSkippedConfirmation(lang: AppLanguage, medicineName: String): String = when (lang) {
        AppLanguage.TELUGU -> "$medicineName వదిలేసినట్లు గుర్తించబడింది."
        AppLanguage.HINDI -> "$medicineName छोड़ दी गई दर्ज की गई है।"
        AppLanguage.ENGLISH -> "Marked $medicineName as skipped."
    }

    fun buildDoseLaterConfirmation(lang: AppLanguage, medicineName: String): String = when (lang) {
        AppLanguage.TELUGU -> "$medicineName కొరకు తర్వాత గుర్తుచేస్తాను."
        AppLanguage.HINDI -> "$medicineName के लिए बाद में याद दिलाऊंगा।"
        AppLanguage.ENGLISH -> "I will remind you later for $medicineName."
    }

    fun buildLabExplanation(lang: AppLanguage, item: ElderHealthItem): String {
        val test = item.testName
        val valUnit = item.valueText
        val range = item.rangeText?.let { " ($it)" }.orEmpty()

        return if (item.isFlagged) {
            when (lang) {
                AppLanguage.TELUGU -> {
                    "ఇటీవలి $test ఫలితం $valUnit$range. ఈ రీడింగ్ నివేదికలోని సాధారణ పరిధికి వెలుపల ఉంది. దయచేసి మీ వైద్యుడితో మాట్లాడండి."
                }
                AppLanguage.HINDI -> {
                    "हालिया $test परिणाम $valUnit$range है। यह मान रिपोर्ट की सामान्य सीमा से बाहर है। कृपया अपने डॉक्टर से बात करें।"
                }
                AppLanguage.ENGLISH -> {
                    "Recent $test result is $valUnit$range. This reading is outside the range shown on the report. Please discuss this with your doctor."
                }
            }
        } else {
            when (lang) {
                AppLanguage.TELUGU -> "ఇటీవలి $test ఫలితం $valUnit$range. ఇది నివేదికలోని సాధారణ పరిధిలోనే ఉంది."
                AppLanguage.HINDI -> "हालिया $test परिणाम $valUnit$range है। यह रिपोर्ट की सामान्य सीमा के भीतर है।"
                AppLanguage.ENGLISH -> "Recent $test result is $valUnit$range. This is within the printed normal range."
            }
        }
    }

    private fun formatNextDose(lang: AppLanguage, d: ElderDose): String {
        val meal = mealPhrase(lang, d.meal)
        val time = formatTime(lang, d.time)
        val nameWithStrength = "${d.name} ${d.strength}".trim()

        return when (lang) {
            AppLanguage.TELUGU -> "మీ తదుపరి మందు $nameWithStrength, $time కు$meal."
            AppLanguage.HINDI -> "आपकी अगली दवा $nameWithStrength, $time पर$meal।"
            AppLanguage.ENGLISH -> "Your next medicine is $nameWithStrength at $time$meal."
        }
    }

    private fun mealPhrase(l: AppLanguage, m: MealTiming): String = when (l) {
        AppLanguage.TELUGU -> when (m) {
            MealTiming.BEFORE_FOOD -> ", భోజనానికి ముందు"
            MealTiming.AFTER_FOOD -> ", భోజనం తర్వాత"
            MealTiming.WITH_FOOD -> ", భోజనంతో పాటు"
            MealTiming.EMPTY_STOMACH -> ", ఖాళీ కడుపుతో"
            MealTiming.UNSPECIFIED -> ""
        }
        AppLanguage.HINDI -> when (m) {
            MealTiming.BEFORE_FOOD -> ", खाने से पहले"
            MealTiming.AFTER_FOOD -> ", खाने के बाद"
            MealTiming.WITH_FOOD -> ", खाने के साथ"
            MealTiming.EMPTY_STOMACH -> ", खाली पेट"
            MealTiming.UNSPECIFIED -> ""
        }
        AppLanguage.ENGLISH -> when (m) {
            MealTiming.BEFORE_FOOD -> ", before food"
            MealTiming.AFTER_FOOD -> ", after food"
            MealTiming.WITH_FOOD -> ", with food"
            MealTiming.EMPTY_STOMACH -> ", on an empty stomach"
            MealTiming.UNSPECIFIED -> ""
        }
    }

    private fun formatTime(l: AppLanguage, t: LocalTime): String {
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
