package com.kutumbam.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kutumbam.app.llm.LiteRtLmEngine
import com.kutumbam.app.llm.LlmBackend
import com.kutumbam.app.llm.ModelStore
import com.kutumbam.app.llm.Prompts
import com.kutumbam.app.llm.generate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LabState(
    val models: List<File> = emptyList(),
    val selected: File? = null,
    val order: List<LlmBackend> = listOf(LlmBackend.NPU, LlmBackend.GPU, LlmBackend.CPU),
    val status: String = "No model loaded",
    val busy: Boolean = false,
    val loaded: Boolean = false,
    val output: String = "",
    val stats: String = "",
)

class LlmLabViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ModelStore(app)
    private val engine = LiteRtLmEngine(app)

    private val _state = MutableStateFlow(LabState())
    val state = _state.asStateFlow()

    val pushHint get() = store.pushDirHint()

    init { refresh() }

    fun refresh() {
        val models = store.list()
        _state.update { it.copy(models = models, selected = it.selected ?: models.firstOrNull()) }
    }

    fun select(file: File) = _state.update { it.copy(selected = file) }

    fun setOrder(order: List<LlmBackend>) = _state.update { it.copy(order = order) }

    fun import(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(busy = true, status = "Copying model…") }
        val file = withContext(Dispatchers.IO) {
            store.import(uri) { bytes ->
                if (bytes % (64L shl 20) < (1L shl 20)) _state.update { s -> s.copy(status = "Copying model… ${bytes shr 20} MB") }
            }
        }
        refresh()
        _state.update { it.copy(busy = false, selected = file, status = "Imported ${file.name}") }
    }

    fun load() = viewModelScope.launch {
        val file = _state.value.selected ?: return@launch
        _state.update { it.copy(busy = true, loaded = false, status = "Loading ${file.name}…") }
        val report = engine.load(file.absolutePath, _state.value.order)
        val failures = report.failures.joinToString("\n") { (b, m) -> "  ${b.name} failed: ${m.take(160)}" }
        _state.update {
            it.copy(
                busy = false,
                loaded = report.ok,
                status = if (report.ok) "Running on ${report.backend} (loaded in ${report.loadMillis} ms)" +
                    (if (failures.isNotEmpty()) "\n$failures" else "")
                else "All backends failed:\n$failures",
            )
        }
    }

    fun runSample() = viewModelScope.launch {
        _state.update { it.copy(busy = true, output = "", stats = "") }
        val (system, user) = Prompts.explainMedicine(
            language = "English",
            medicineLines = "- Metformin 500 mg, twice a day (BD), after food, for 30 days\n" +
                "- Amlodipine 5 mg, once a day (OD), morning, for 30 days\n" +
                "- Pantoprazole 40 mg, once a day (OD), before food, for 14 days",
        )
        try {
            val r = engine.generate(system, user) { partial -> _state.update { it.copy(output = partial) } }
            _state.update {
                it.copy(
                    busy = false,
                    stats = "first token ${r.firstTokenMillis} ms · total ${r.totalMillis} ms · ${r.chunks} chunks · " +
                        "${"%.1f".format(r.chunks * 1000.0 / maxOf(r.totalMillis, 1))} chunks/s",
                )
            }
        } catch (t: Throwable) {
            _state.update { it.copy(busy = false, output = "Error: ${t.message}") }
        }
    }

    override fun onCleared() = engine.close()
}
