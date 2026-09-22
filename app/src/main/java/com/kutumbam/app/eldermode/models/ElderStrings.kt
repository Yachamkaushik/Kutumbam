package com.kutumbam.app.eldermode.models

import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.speech.AppLanguage
import java.time.LocalTime

/**
 * Complete multilingual dictionary for Elder Mode.
 * Telugu is the primary language, with Hindi and English as secondary.
 * Medicine names always remain in Latin script as printed on strips.
 */
object ElderStrings {

    fun greeting(lang: AppLanguage, name: String, time: LocalTime = LocalTime.now()): String {
        val hour = time.hour
        return when (lang) {
            AppLanguage.TELUGU -> when (hour) {
                in 4..11 -> "శుభోదయం, $name"
                in 12..15 -> "శుభ మధ్యాహ్నం, $name"
                in 16..20 -> "శుభ సాయంత్రం, $name"
                else -> "నమస్కారం, $name"
            }
            AppLanguage.HINDI -> when (hour) {
                in 4..11 -> "शुभ प्रभात, $name"
                in 12..15 -> "शुभ दोपहर, $name"
                in 16..20 -> "शुभ संध्या, $name"
                else -> "नमस्ते, $name"
            }
            AppLanguage.ENGLISH -> when (hour) {
                in 4..11 -> "Good Morning, $name"
                in 12..15 -> "Good Afternoon, $name"
                in 16..20 -> "Good Evening, $name"
                else -> "Hello, $name"
            }
        }
    }

    // Section Titles
    fun today(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "ఈ రోజు"
        AppLanguage.HINDI -> "आज"
        AppLanguage.ENGLISH -> "TODAY"
    }

    fun nextMedicine(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "తదుపరి మందు"
        AppLanguage.HINDI -> "अगली दवा"
        AppLanguage.ENGLISH -> "NEXT MEDICINE"
    }

    fun myMedicines(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "నా మందులు"
        AppLanguage.HINDI -> "मेरी दवाइयाँ"
        AppLanguage.ENGLISH -> "My Medicines"
    }

    fun askKutumbam(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "కుటుంబం ను అడగండి"
        AppLanguage.HINDI -> "कुटुम्बम से पूछें"
        AppLanguage.ENGLISH -> "Ask Kutumbam"
    }

    fun myHealth(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "నా ఆరోగ్యం"
        AppLanguage.HINDI -> "मेरा स्वास्थ्य"
        AppLanguage.ENGLISH -> "My Health"
    }

    fun myDay(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "ఈ రోజు వివరాలు"
        AppLanguage.HINDI -> "मेरा दिन"
        AppLanguage.ENGLISH -> "My Day"
    }

    fun emergency(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "అత్యవసరం"
        AppLanguage.HINDI -> "आपातकालीन"
        AppLanguage.ENGLISH -> "Emergency"
    }

    // Button Labels
    fun readMyDay(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "రోజు వివరాలు వినండి"
        AppLanguage.HINDI -> "मेरा दिन सुनें"
        AppLanguage.ENGLISH -> "Read my day"
    }

    fun hear(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "వినండి"
        AppLanguage.HINDI -> "सुनें"
        AppLanguage.ENGLISH -> "Hear"
    }

    fun taken(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "వేసుకున్నాను"
        AppLanguage.HINDI -> "ले ली"
        AppLanguage.ENGLISH -> "Taken"
    }

    fun notTaken(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "ఇంకా వేసుకోలేదు"
        AppLanguage.HINDI -> "अभी नहीं ली"
        AppLanguage.ENGLISH -> "Not taken"
    }

    fun remindLater(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "తర్వాత గుర్తుచేయి"
        AppLanguage.HINDI -> "बाद में याद दिलाएं"
        AppLanguage.ENGLISH -> "Remind later"
    }

    fun skip(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "వదిలేయి"
        AppLanguage.HINDI -> "छोड़ें"
        AppLanguage.ENGLISH -> "Skip"
    }

    fun hearAgain(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "మళ్లీ వినండి"
        AppLanguage.HINDI -> "फिर से सुनें"
        AppLanguage.ENGLISH -> "Hear again"
    }

    fun stopSpeaking(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "ఆపండి"
        AppLanguage.HINDI -> "रोकें"
        AppLanguage.ENGLISH -> "Stop"
    }

    fun askAnother(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "మరో ప్రశ్న అడగండి"
        AppLanguage.HINDI -> "दूसरा सवाल पूछें"
        AppLanguage.ENGLISH -> "Ask another"
    }

    fun backToHome(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "← హోమ్"
        AppLanguage.HINDI -> "← होम"
        AppLanguage.ENGLISH -> "← HOME"
    }

    fun exitElderMode(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "సాధారణ వీక్షణ"
        AppLanguage.HINDI -> "सामान्य दृश्य"
        AppLanguage.ENGLISH -> "Standard View"
    }

    // Medicine Counts & Summaries
    fun medicinesCount(lang: AppLanguage, count: Int): String = when (lang) {
        AppLanguage.TELUGU -> "$count మందులు"
        AppLanguage.HINDI -> "$count दवाइयाँ"
        AppLanguage.ENGLISH -> "$count medicines"
    }

    fun nextMedAt(lang: AppLanguage, timeText: String): String = when (lang) {
        AppLanguage.TELUGU -> "తదుపరి మందు: $timeText"
        AppLanguage.HINDI -> "अगली दवा: $timeText"
        AppLanguage.ENGLISH -> "Next medicine: $timeText"
    }

    fun allDosesTaken(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "ఈ రోజు మందులన్నీ పూర్తయ్యాయి! ✓"
        AppLanguage.HINDI -> "आज की सभी दवाइयाँ पूरी हो चुकी हैं! ✓"
        AppLanguage.ENGLISH -> "All medicines taken for today! ✓"
    }

    // Meal Instructions
    fun mealInstruction(lang: AppLanguage, meal: MealTiming): String = when (lang) {
        AppLanguage.TELUGU -> when (meal) {
            MealTiming.BEFORE_FOOD -> "భోజనానికి ముందు"
            MealTiming.AFTER_FOOD -> "భోజనం తర్వాత"
            MealTiming.WITH_FOOD -> "భోజనంతో పాటు"
            MealTiming.EMPTY_STOMACH -> "ఖాళీ కడుపుతో"
            MealTiming.UNSPECIFIED -> ""
        }
        AppLanguage.HINDI -> when (meal) {
            MealTiming.BEFORE_FOOD -> "खाने से पहले"
            MealTiming.AFTER_FOOD -> "खाने के बाद"
            MealTiming.WITH_FOOD -> "खाने के साथ"
            MealTiming.EMPTY_STOMACH -> "खाली पेट"
            MealTiming.UNSPECIFIED -> ""
        }
        AppLanguage.ENGLISH -> when (meal) {
            MealTiming.BEFORE_FOOD -> "Before food"
            MealTiming.AFTER_FOOD -> "After food"
            MealTiming.WITH_FOOD -> "With food"
            MealTiming.EMPTY_STOMACH -> "Empty stomach"
            MealTiming.UNSPECIFIED -> ""
        }
    }

    // Voice States
    fun voiceIdleTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "ప్రశ్న అడగడానికి మైక్ నొక్కండి"
        AppLanguage.HINDI -> "सवाल पूछने के लिए माइक दबाएं"
        AppLanguage.ENGLISH -> "Tap to speak your question"
    }

    fun voiceListeningTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "వింటున్నాను... దయచేసి మాట్లాడండి"
        AppLanguage.HINDI -> "सुन रहा हूँ... कृपया बोलिए"
        AppLanguage.ENGLISH -> "Listening... Please speak"
    }

    fun voiceProcessingTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "మీ ఆరోగ్య రికార్డులను పరిశీలిస్తున్నాను..."
        AppLanguage.HINDI -> "आपके स्वास्थ्य रिकॉर्ड देख रहा हूँ..."
        AppLanguage.ENGLISH -> "Checking your saved records..."
    }

    fun voiceSpeakingTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "సమాధానం చెబుతున్నాను..."
        AppLanguage.HINDI -> "उत्तर दे रहा हूँ..."
        AppLanguage.ENGLISH -> "Speaking answer..."
    }

    fun voiceErrorTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "అర్థం కాలేదు. దయచేసి మళ్లీ ప్రయత్నించండి."
        AppLanguage.HINDI -> "समझ नहीं आया। कृपया दोबारा कोशिश करें।"
        AppLanguage.ENGLISH -> "I couldn't understand that. Please try again."
    }

    fun voiceNotFound(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "మీ భద్రపరచిన ఆరోగ్య రికార్డులలో ఆ సమాచారం కనిపించలేదు."
        AppLanguage.HINDI -> "आपके सहेजे गए स्वास्थ्य रिकॉर्ड में यह जानकारी नहीं मिली।"
        AppLanguage.ENGLISH -> "I could not find that information in your saved health records."
    }

    // Health Screen & Safety
    fun explainReport(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "నివేదిక వివరణ వినండి"
        AppLanguage.HINDI -> "रिपोर्ट का विवरण सुनें"
        AppLanguage.ENGLISH -> "Explain my report"
    }

    fun reportDate(lang: AppLanguage, date: String): String = when (lang) {
        AppLanguage.TELUGU -> "తాజా నివేదిక: $date"
        AppLanguage.HINDI -> "नवीनतम रिपोर्ट: $date"
        AppLanguage.ENGLISH -> "Latest report: $date"
    }

    fun outsideRange(lang: AppLanguage, count: Int): String = when (lang) {
        AppLanguage.TELUGU -> "$count రీడింగ్‌లు సాధారణ పరిధికి వెలుపల ఉన్నాయి"
        AppLanguage.HINDI -> "$count मान सामान्य सीमा से बाहर हैं"
        AppLanguage.ENGLISH -> "$count values outside normal range"
    }

    fun allLabsNormal(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "అన్ని పరీక్షల ఫలితాలు సాధారణ పరిధిలోనే ఉన్నాయి"
        AppLanguage.HINDI -> "सभी परीक्षण परिणाम सामान्य सीमा में हैं"
        AppLanguage.ENGLISH -> "All test results are within normal range"
    }

    fun doctorDiscussionAdvice(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "ఈ రీడింగ్ నివేదికలోని సాధారణ పరిధికి వెలుపల ఉంది. దయచేసి మీ వైద్యుడితో మాట్లాడండి."
        AppLanguage.HINDI -> "यह रीडिंग रिपोर्ट में दी गई सामान्य सीमा से बाहर है। कृपया अपने डॉक्टर से बात करें।"
        AppLanguage.ENGLISH -> "This reading is outside the range shown on the report. Please discuss this with your doctor."
    }

    // Emergency Screen
    fun callEmergencyContact(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "అత్యవసర సహాయానికి కాల్ చేయండి"
        AppLanguage.HINDI -> "आपातकालीन संपर्क को कॉल करें"
        AppLanguage.ENGLISH -> "Call emergency contact"
    }

    fun bloodGroupLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "రక్త గ్రూప్:"
        AppLanguage.HINDI -> "रक्त समूह:"
        AppLanguage.ENGLISH -> "Blood Group:"
    }

    fun allergiesLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "అలెర్జీలు:"
        AppLanguage.HINDI -> "एलर्जी:"
        AppLanguage.ENGLISH -> "Allergies:"
    }

    fun conditionsLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "పరిస్థితులు:"
        AppLanguage.HINDI -> "बीमारियाँ:"
        AppLanguage.ENGLISH -> "Conditions:"
    }

    // Empty States
    fun emptyMedicines(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "ఈ రోజుకు మందులు ఏమీ లేవు."
        AppLanguage.HINDI -> "आज के लिए कोई दवा निर्धारित नहीं है।"
        AppLanguage.ENGLISH -> "No medicines are scheduled for today."
    }

    fun emptyReports(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "ఆరోగ్య నివేదికలు ఏవీ భద్రపరచబడలేదు."
        AppLanguage.HINDI -> "कोई स्वास्थ्य रिपोर्ट अभी तक सहेजी नहीं गई है।"
        AppLanguage.ENGLISH -> "No health reports are saved yet."
    }

    fun micPermissionNeeded(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "వాయిస్ ద్వారా అడగడానికి మైక్రోఫోన్ అనుమతి అవసరం."
        AppLanguage.HINDI -> "आवाज़ से पूछने के लिए माइक्रोफ़ोन अनुमति की आवश्यकता है।"
        AppLanguage.ENGLISH -> "Microphone permission is needed to ask by voice."
    }

    fun voiceUnavailable(lang: AppLanguage): String = when (lang) {
        AppLanguage.TELUGU -> "ఈ పరికరంలో వాయిస్ అందుబాటులో లేదు."
        AppLanguage.HINDI -> "इस डिवाइस पर आवाज़ उपलब्ध नहीं है।"
        AppLanguage.ENGLISH -> "Voice is not available on this device."
    }
}
