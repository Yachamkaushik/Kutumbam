package com.kutumbam.app.eldermode.models

import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.speech.AppLanguage
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Top-level navigation destinations within Elder Mode. */
enum class ElderDestination {
    HOME,
    MY_DAY,
    MEDICINES,
    ASK,
    HEALTH,
    EMERGENCY
}

/** Explicit visual and functional states for voice interaction. */
enum class VoiceUiState {
    IDLE,
    LISTENING,
    PROCESSING,
    ANSWER,
    ERROR
}

/** Status of a medicine dose for the day. */
enum class DoseStatus {
    NOT_TAKEN,
    TAKEN,
    SKIPPED,
    REMIND_LATER
}

/** One medicine dose displayed in Elder Mode. */
data class ElderDose(
    val medicineId: Long,
    val time: LocalTime,
    val name: String,
    val strength: String,
    val meal: MealTiming,
    val instruction: String,
    val status: DoseStatus,
    val units: Double = 1.0,
    val form: String? = null,
) {
    val isTaken: Boolean get() = status == DoseStatus.TAKEN
    val timeFormatted: String get() = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}

/** Simplified health/lab reading for elder review. */
data class ElderHealthItem(
    val testName: String,
    val value: Double,
    val valueText: String,
    val unit: String?,
    val rangeText: String?,
    val isFlagged: Boolean,
    val date: String,
    val previousValueText: String? = null,
    val trendText: String? = null,
)

/** Comprehensive UI state for Elder Mode. */
data class ElderState(
    val memberId: Long = 0L,
    val memberName: String = "",
    val relation: String = "",
    val isSelf: Boolean = false,
    val language: AppLanguage = AppLanguage.TELUGU,
    val currentDest: ElderDestination = ElderDestination.HOME,
    val doses: List<ElderDose> = emptyList(),
    val nextDose: ElderDose? = null,
    val nextDoseCountdown: String = "",
    val latestReportDate: String? = null,
    val flaggedItems: List<ElderHealthItem> = emptyList(),
    val recentHealthItems: List<ElderHealthItem> = emptyList(),
    val bloodGroup: String? = null,
    val allergies: String? = null,
    val conditions: String? = null,
    val emergencyName: String? = null,
    val emergencyPhone: String? = null,
    val voiceState: VoiceUiState = VoiceUiState.IDLE,
    val recognizedText: String = "",
    val voiceAnswerText: String = "",
    val voiceAnswerSource: String = "",
    val voiceErrorMessage: String = "",
    val isSpeaking: Boolean = false,
    val currentSpeechText: String = "",
    val actionMessage: String? = null,
) {
    val totalDoses: Int get() = doses.size
    val takenDosesCount: Int get() = doses.count { it.isTaken }
    val pendingDosesCount: Int get() = doses.count { !it.isTaken }
    val allTaken: Boolean get() = totalDoses > 0 && takenDosesCount == totalDoses
    val hasEmergencyInfo: Boolean get() = !emergencyPhone.isNullOrBlank() || !bloodGroup.isNullOrBlank() || !allergies.isNullOrBlank()
}
