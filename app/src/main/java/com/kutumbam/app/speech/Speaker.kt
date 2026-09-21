package com.kutumbam.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeakResult { STARTED, VOICE_MISSING, ENGINE_UNAVAILABLE }

/** Android's built-in TextToSpeech: offline once the language voice is installed. */
class Speaker(context: Context) {
    private var ready = false
    private var failed = false
    private var pending: (() -> Unit)? = null

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) { ready = true; pending?.invoke(); pending = null } else failed = true
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) { _speaking.value = false }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { _speaking.value = false }
        })
    }

    /** Runs [onResult] with the outcome once the engine is ready (immediately if it already is). */
    fun speak(text: String, lang: AppLanguage, onResult: (SpeakResult) -> Unit) {
        val go = {
            val avail = tts.setLanguage(lang.locale)
            if (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "No ${lang.voiceName} voice: setLanguage=$avail")
                onResult(SpeakResult.VOICE_MISSING)
            } else {
                tts.setSpeechRate(0.85f) // a little slower for elderly listeners
                val code = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kutumbam-${System.nanoTime()}")
                Log.i(TAG, "speak(${lang.code}) -> $code, voice=${tts.voice?.name}, ${text.length} chars")
                onResult(if (code == TextToSpeech.SUCCESS) SpeakResult.STARTED else SpeakResult.ENGINE_UNAVAILABLE)
            }
        }
        when {
            failed -> onResult(SpeakResult.ENGINE_UNAVAILABLE)
            ready -> go()
            else -> pending = go
        }
    }

    fun stop() { tts.stop(); _speaking.value = false }
    fun shutdown() { tts.shutdown() }

    private companion object { const val TAG = "KutumbamTTS" }
}
