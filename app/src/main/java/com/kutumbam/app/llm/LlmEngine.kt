package com.kutumbam.app.llm

import kotlinx.coroutines.flow.Flow

enum class LlmBackend { NPU, GPU, CPU }

/** Outcome of trying to bring a model up: which backend actually ran, and what failed on the way. */
data class LoadReport(
    val backend: LlmBackend?,
    val loadMillis: Long,
    val failures: List<Pair<LlmBackend, String>>,
) {
    val ok get() = backend != null
}

data class GenerationStats(
    val text: String,
    val firstTokenMillis: Long,
    val totalMillis: Long,
    val chunks: Int,
)

/**
 * The only thing the rest of the app knows about the language model. Implementations pick the
 * accelerator; callers only supply an instruction and already-structured, already-confirmed data.
 */
interface LlmEngine {
    val activeBackend: LlmBackend?

    /** Tries [order] in turn and keeps the first backend that initialises. */
    suspend fun load(modelPath: String, order: List<LlmBackend>): LoadReport

    /** Streams text deltas. */
    fun stream(system: String, user: String): Flow<String>

    fun close()
}

/** Collects a stream and records latency, for the lab screen and for sanity checks in the demo. */
suspend fun LlmEngine.generate(system: String, user: String, onDelta: (String) -> Unit = {}): GenerationStats {
    val start = System.nanoTime()
    var first = -1L
    var chunks = 0
    val sb = StringBuilder()
    stream(system, user).collect { delta ->
        if (first < 0) first = (System.nanoTime() - start) / 1_000_000
        chunks++
        sb.append(delta)
        onDelta(sb.toString())
    }
    return GenerationStats(sb.toString(), maxOf(first, 0), (System.nanoTime() - start) / 1_000_000, chunks)
}
