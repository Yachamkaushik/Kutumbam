package com.kutumbam.app.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtLmEngine(private val context: Context) : LlmEngine {

    private var engine: Engine? = null
    private val lock = Mutex()

    @Volatile
    override var activeBackend: LlmBackend? = null
        private set

    override suspend fun load(modelPath: String, order: List<LlmBackend>): LoadReport =
        withContext(Dispatchers.Default) {
            lock.withLock {
                close()
                val started = System.currentTimeMillis()
                val failures = mutableListOf<Pair<LlmBackend, String>>()
                val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }.absolutePath
                for (backend in order) {
                    try {
                        val config = EngineConfig(
                            modelPath = modelPath,
                            backend = backend.toSdk(),
                            maxNumTokens = MAX_TOKENS,
                            cacheDir = cacheDir,
                        )
                        val candidate = Engine(config)
                        candidate.initialize()
                        engine = candidate
                        activeBackend = backend
                        return@withContext LoadReport(backend, System.currentTimeMillis() - started, failures)
                    } catch (t: Throwable) {
                        failures += backend to (t.message ?: t.javaClass.simpleName)
                    }
                }
                LoadReport(null, System.currentTimeMillis() - started, failures)
            }
        }

    override fun stream(system: String, user: String): Flow<String> = flow {
        val eng = checkNotNull(engine) { "Model not loaded" }
        lock.withLock {
            val config = ConversationConfig(
                systemInstruction = Contents.of(system),
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.3, seed = 0),
            )
            eng.createConversation(config).use { conversation ->
                emitAll(conversation.sendMessageAsync(user).map { it.text() })
            }
        }
    }.flowOn(Dispatchers.Default)

    override fun close() {
        runCatching { engine?.close() }
        engine = null
        activeBackend = null
    }

    private fun LlmBackend.toSdk(): Backend = when (this) {
        LlmBackend.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        LlmBackend.GPU -> Backend.GPU()
        LlmBackend.CPU -> Backend.CPU()
    }

    private fun Message.text(): String =
        contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private companion object {
        const val MAX_TOKENS = 2048
    }
}
