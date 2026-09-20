package com.kutumbam.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kutumbam.app.KutumbamApp
import com.kutumbam.app.llm.Prompts
import com.kutumbam.app.llm.generate
import com.kutumbam.app.parse.DocumentParser
import com.kutumbam.app.parse.ParsedDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OcrLabState(
    val busy: Boolean = false,
    val imageUri: Uri? = null,
    val rawText: String = "",
    val ocrMillis: Long = 0,
    val parseMillis: Long = 0,
    val doc: ParsedDocument? = null,
    val explanation: String = "",
    val explainStats: String = "",
    val error: String? = null,
)

class OcrLabViewModel(app: Application) : AndroidViewModel(app) {
    private val kApp = app as KutumbamApp
    private val _state = MutableStateFlow(OcrLabState())
    val state = _state.asStateFlow()

    val llmReady get() = kApp.llm.activeBackend != null

    fun process(uri: Uri) = viewModelScope.launch {
        _state.value = OcrLabState(busy = true, imageUri = uri)
        try {
            val ocr = kApp.ocr.recognize(uri)
            val t0 = System.currentTimeMillis()
            val doc = DocumentParser.parse(ocr.text)
            _state.update {
                it.copy(busy = false, rawText = ocr.text, ocrMillis = ocr.millis, doc = doc, parseMillis = System.currentTimeMillis() - t0)
            }
        } catch (t: Throwable) {
            _state.update { it.copy(busy = false, error = t.message ?: t.javaClass.simpleName) }
        }
    }

    /** The model only restates the already-parsed medicines; it never sees raw OCR text. */
    fun explain(language: String) = viewModelScope.launch {
        val meds = _state.value.doc?.medicines?.takeIf { it.isNotEmpty() } ?: return@launch
        _state.update { it.copy(busy = true, explanation = "", explainStats = "") }
        val lines = meds.joinToString("\n") { m ->
            "- ${m.name}${m.strength?.let { " $it" } ?: ""}, ${m.frequency?.code ?: "as directed"}, " +
                "${m.meal.name.lowercase().replace('_', ' ')}${m.durationDays?.let { ", for $it days" } ?: ""}"
        }
        val (system, user) = Prompts.explainMedicine(language, lines)
        try {
            val r = kApp.llm.generate(system, user) { partial -> _state.update { it.copy(explanation = partial) } }
            _state.update { it.copy(busy = false, explainStats = "first token ${r.firstTokenMillis} ms · total ${r.totalMillis} ms · on ${kApp.llm.activeBackend}") }
        } catch (t: Throwable) {
            _state.update { it.copy(busy = false, explanation = "Error: ${t.message}") }
        }
    }
}
