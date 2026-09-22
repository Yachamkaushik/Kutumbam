package com.kutumbam.app.eldermode.voice

import android.util.Log
import com.kutumbam.app.eldermode.models.VoiceUiState
import com.kutumbam.app.speech.AppLanguage
import com.kutumbam.app.speech.SpeakResult
import com.kutumbam.app.speech.Speaker
import com.kutumbam.app.speech.VoiceInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages audio lifecycle and speech interaction for Elder Mode.
 * Reuses existing Speaker and VoiceInput without duplicating audio subsystems.
 * Guarantees zero overlapping speech: stops TTS before starting mic, and stops mic before speaking.
 */
class ElderVoiceController(
    private val speaker: Speaker,
    private val voiceInput: VoiceInput,
    private val scope: CoroutineScope,
) {
    private val _voiceState = MutableStateFlow(VoiceUiState.IDLE)
    val voiceState: StateFlow<VoiceUiState> = _voiceState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var speechWatchdog: Job? = null

    init {
        // Observe Speaker's active state
        scope.launch {
            speaker.speaking.collect { speaking ->
                _isSpeaking.value = speaking
                if (!speaking && _voiceState.value == VoiceUiState.ANSWER) {
                    // Finished speaking answer
                }
            }
        }
    }

    /** Speaks the given text aloud, stopping any active recording. */
    fun speak(
        text: String,
        lang: AppLanguage,
        onStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ) {
        stopListening()
        speaker.stop()
        onStart?.invoke()

        speaker.speak(text, lang) { result ->
            when (result) {
                SpeakResult.STARTED -> {
                    _isSpeaking.value = true
                    // Start a watchdog to monitor completion if utterance listener is missed
                    speechWatchdog?.cancel()
                    speechWatchdog = scope.launch {
                        val estimatedSeconds = (text.length / 15).coerceIn(2, 60)
                        delay(estimatedSeconds * 1000L)
                        onComplete?.invoke()
                    }
                }
                SpeakResult.VOICE_MISSING -> {
                    _isSpeaking.value = false
                    onError?.invoke("The ${lang.voiceName} voice pack is not installed on this phone.")
                }
                SpeakResult.ENGINE_UNAVAILABLE -> {
                    _isSpeaking.value = false
                    onError?.invoke("Text-to-speech engine is currently unavailable.")
                }
            }
        }
    }

    /** Halts any spoken readout immediately. */
    fun stopSpeaking() {
        speechWatchdog?.cancel()
        speaker.stop()
        _isSpeaking.value = false
    }

    /** Checks if speech-to-text is available on the device. */
    fun isVoiceInputAvailable(): Boolean = voiceInput.isAvailable()

    /** Starts voice recognition for a question, stopping any active TTS playback. */
    fun startListening(
        lang: AppLanguage,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        stopSpeaking()
        _voiceState.value = VoiceUiState.LISTENING

        voiceInput.start(
            lang = lang,
            onPartial = { partial ->
                _voiceState.value = VoiceUiState.LISTENING
                onPartial(partial)
            },
            onResult = { result ->
                _voiceState.value = VoiceUiState.PROCESSING
                onResult(result)
            },
            onError = { errorMsg ->
                _voiceState.value = VoiceUiState.ERROR
                onError(errorMsg)
            },
        )
    }

    /** Cancels listening and resets to IDLE. */
    fun stopListening() {
        voiceInput.stop()
        if (_voiceState.value == VoiceUiState.LISTENING) {
            _voiceState.value = VoiceUiState.IDLE
        }
    }

    /** Transitions state to ANSWER. */
    fun setAnswerState() {
        _voiceState.value = VoiceUiState.ANSWER
    }

    /** Transitions state to ERROR with message. */
    fun setErrorState() {
        _voiceState.value = VoiceUiState.ERROR
    }

    /** Resets voice state to IDLE. */
    fun resetState() {
        stopListening()
        stopSpeaking()
        _voiceState.value = VoiceUiState.IDLE
    }

    /** Complete cleanup. */
    fun release() {
        speechWatchdog?.cancel()
        stopListening()
        stopSpeaking()
    }
}
