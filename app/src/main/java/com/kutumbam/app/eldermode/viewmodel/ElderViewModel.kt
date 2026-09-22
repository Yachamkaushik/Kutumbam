package com.kutumbam.app.eldermode.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kutumbam.app.KutumbamApp
import com.kutumbam.app.data.DoseLog
import com.kutumbam.app.data.FamilyMember
import com.kutumbam.app.eldermode.adapters.ElderDataAdapter
import com.kutumbam.app.eldermode.models.DoseStatus
import com.kutumbam.app.eldermode.models.ElderDestination
import com.kutumbam.app.eldermode.models.ElderDose
import com.kutumbam.app.eldermode.models.ElderHealthItem
import com.kutumbam.app.eldermode.models.ElderState
import com.kutumbam.app.eldermode.models.ElderStrings
import com.kutumbam.app.eldermode.models.VoiceUiState
import com.kutumbam.app.eldermode.voice.ElderVoiceController
import com.kutumbam.app.eldermode.voice.ElderVoiceScripts
import com.kutumbam.app.speech.AppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Dedicated, isolated ViewModel powering Elder Mode.
 * Reuses existing Repository, Speaker, VoiceInput, and Retrieval subsystems
 * without modifying any existing application files.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ElderViewModel(application: Application) : AndroidViewModel(application) {

    private val kApp = application as KutumbamApp
    private val repo = kApp.repo

    val voiceController = ElderVoiceController(kApp.speaker, kApp.voice, viewModelScope)

    private val _uiState = MutableStateFlow(ElderState())
    val uiState: StateFlow<ElderState> = _uiState.asStateFlow()

    private val selectedMemberId = MutableStateFlow<Long?>(null)

    init {
        // Observe members, medicines, doseLogs, labs, and vitals. Re-picks whenever the member list
        // changes or selectMember() is called, so Elder Mode opens on whoever the family had selected.
        // flatMapLatest cancels the previous member's medicine/lab collector instead of leaking it.
        viewModelScope.launch {
            combine(repo.members(), selectedMemberId) { members, currentId -> members to currentId }
                .flatMapLatest { (members, currentId) ->
                    if (members.isEmpty()) return@flatMapLatest flowOf(null)
                    val target = members.firstOrNull { it.id == currentId }
                        ?: members.firstOrNull { !it.isSelf } // elder member preferred
                        ?: members.first()
                    if (target.id != currentId) selectedMemberId.value = target.id
                    memberDataFlow(target)
                }
                .collect { transform -> transform?.let { _uiState.update(it) } }
        }

        // Keep voice controller states synced with UI state
        viewModelScope.launch {
            voiceController.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }
        viewModelScope.launch {
            voiceController.voiceState.collect { vState ->
                _uiState.update { it.copy(voiceState = vState) }
            }
        }
    }

    /** One state update per change to this member's medicines, doses or labs. Caller collects with flatMapLatest so switching members cancels the previous one's collector instead of leaking it. */
    private fun memberDataFlow(member: FamilyMember): Flow<(ElderState) -> ElderState> {
        val today = LocalDate.now()
        val lang = AppLanguage.fromCode(member.preferredLanguage)

        return combine(
            repo.medicines(member.id),
            repo.doseLogs(today),
            repo.labHistory(member.id),
        ) { meds, logs, labs ->
            val doses = ElderDataAdapter.buildElderDoses(meds, logs, today)
            val (next, countdown) = ElderDataAdapter.findNextDose(doses, LocalTime.now())
            val healthItems = ElderDataAdapter.buildHealthItems(labs)
            val flagged = healthItems.filter { it.isFlagged }
            val latestReport = labs.lastOrNull()?.date

            { state: ElderState ->
                state.copy(
                    memberId = member.id,
                    memberName = member.name,
                    relation = member.relation,
                    isSelf = member.isSelf,
                    language = lang,
                    doses = doses,
                    nextDose = next,
                    nextDoseCountdown = countdown,
                    latestReportDate = latestReport,
                    flaggedItems = flagged,
                    recentHealthItems = healthItems,
                    bloodGroup = member.bloodGroup,
                    allergies = member.allergies,
                    conditions = member.conditions,
                    emergencyName = member.emergencyName,
                    emergencyPhone = member.emergencyPhone,
                )
            }
        }
    }

    /** Opens Elder Mode on this member, matching whoever the family had selected before entering. Safe to call more than once. */
    fun selectMember(id: Long) {
        if (id > 0 && id != selectedMemberId.value) selectedMemberId.value = id
    }

    fun navigate(dest: ElderDestination) {
        voiceController.stopSpeaking()
        voiceController.stopListening()
        _uiState.update { it.copy(currentDest = dest, actionMessage = null) }
    }

    fun setLanguage(lang: AppLanguage) {
        val memberId = _uiState.value.memberId
        voiceController.stopSpeaking()
        _uiState.update { it.copy(language = lang) }
        if (memberId > 0) {
            viewModelScope.launch {
                repo.setLanguage(memberId, lang.code)
            }
        }
    }

    /** Plays full spoken summary of today's schedule. */
    fun speakMyDay() {
        val state = _uiState.value
        if (state.isSpeaking) {
            voiceController.stopSpeaking()
            return
        }
        val script = ElderVoiceScripts.buildMyDay(
            lang = state.language,
            memberName = state.memberName,
            doses = state.doses,
            nextDose = state.nextDose,
            flaggedLab = state.flaggedItems.firstOrNull(),
        )
        _uiState.update { it.copy(currentSpeechText = script) }
        voiceController.speak(
            text = script,
            lang = state.language,
            onError = { err -> showMessage(err) },
        )
    }

    /** Plays spoken instructions for an individual dose. */
    fun speakDose(dose: ElderDose) {
        val state = _uiState.value
        if (state.isSpeaking) {
            voiceController.stopSpeaking()
            return
        }
        val script = ElderVoiceScripts.buildSingleDose(state.language, dose)
        _uiState.update { it.copy(currentSpeechText = script) }
        voiceController.speak(
            text = script,
            lang = state.language,
            onError = { err -> showMessage(err) },
        )
    }

    /** Marks a dose taken in the local Room database and gives spoken + visual confirmation. */
    fun markDoseTaken(dose: ElderDose) {
        val state = _uiState.value
        val today = LocalDate.now()
        viewModelScope.launch {
            repo.setDose(dose.medicineId, today, dose.time.toString(), true)
            val confirm = ElderVoiceScripts.buildDoseTakenConfirmation(state.language, dose.name)
            showMessage(confirm)
            voiceController.speak(confirm, state.language)
        }
    }

    /** Marks a dose skipped in the local database and gives audible feedback. */
    fun markDoseSkipped(dose: ElderDose) {
        val state = _uiState.value
        val today = LocalDate.now()
        viewModelScope.launch {
            repo.setDose(dose.medicineId, today, dose.time.toString(), false)
            val confirm = ElderVoiceScripts.buildDoseSkippedConfirmation(state.language, dose.name)
            showMessage(confirm)
            voiceController.speak(confirm, state.language)
        }
    }

    /** Postpones reminder by marking later. */
    fun markDoseLater(dose: ElderDose) {
        val state = _uiState.value
        val confirm = ElderVoiceScripts.buildDoseLaterConfirmation(state.language, dose.name)
        showMessage(confirm)
        voiceController.speak(confirm, state.language)
    }

    /** Explains a lab report reading aloud with non-diagnostic safety framing. */
    fun speakLab(item: ElderHealthItem) {
        val state = _uiState.value
        if (state.isSpeaking) {
            voiceController.stopSpeaking()
            return
        }
        val script = ElderVoiceScripts.buildLabExplanation(state.language, item)
        _uiState.update { it.copy(currentSpeechText = script) }
        voiceController.speak(
            text = script,
            lang = state.language,
            onError = { err -> showMessage(err) },
        )
    }

    /** Starts voice question recording in Ask Kutumbam. */
    fun startVoiceQuestion() {
        val state = _uiState.value
        voiceController.stopSpeaking()

        if (!voiceController.isVoiceInputAvailable()) {
            _uiState.update {
                it.copy(
                    voiceState = VoiceUiState.ERROR,
                    voiceErrorMessage = ElderStrings.voiceUnavailable(state.language),
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                voiceState = VoiceUiState.LISTENING,
                recognizedText = "",
                voiceErrorMessage = "",
            )
        }

        voiceController.startListening(
            lang = state.language,
            onPartial = { partial ->
                _uiState.update { it.copy(recognizedText = partial) }
            },
            onResult = { result ->
                _uiState.update { it.copy(recognizedText = result) }
                processVoiceQuestion(result)
            },
            onError = { errorMsg ->
                _uiState.update {
                    it.copy(
                        voiceState = VoiceUiState.ERROR,
                        voiceErrorMessage = errorMsg,
                    )
                }
            },
        )
    }

    fun stopListening() {
        voiceController.stopListening()
    }

    /** Deterministically processes question over stored local health records. */
    fun processVoiceQuestion(query: String) {
        val state = _uiState.value
        val memberId = state.memberId
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    voiceState = VoiceUiState.ERROR,
                    voiceErrorMessage = ElderStrings.voiceErrorTitle(state.language),
                )
            }
            return
        }

        _uiState.update { it.copy(voiceState = VoiceUiState.PROCESSING) }

        viewModelScope.launch {
            val member = repo.member(memberId) ?: return@launch
            val (answer, source) = ElderDataAdapter.answerQuestionFromLocker(query, member, repo)

            _uiState.update {
                it.copy(
                    voiceState = VoiceUiState.ANSWER,
                    voiceAnswerText = answer,
                    voiceAnswerSource = source,
                )
            }

            voiceController.speak(
                text = answer,
                lang = state.language,
                onError = { err -> showMessage(err) },
            )
        }
    }

    fun repeatSpokenAnswer() {
        val state = _uiState.value
        if (state.voiceAnswerText.isNotBlank()) {
            voiceController.speak(state.voiceAnswerText, state.language)
        }
    }

    fun stopSpeaking() {
        voiceController.stopSpeaking()
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(actionMessage = msg) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }
}
