package com.kutumbam.app

import android.app.Application
import com.kutumbam.app.data.Repository
import com.kutumbam.app.llm.LiteRtLmEngine
import com.kutumbam.app.llm.LlmEngine
import com.kutumbam.app.llm.ModelStore
import com.kutumbam.app.ocr.TextOcr
import com.kutumbam.app.speech.Speaker

/** Process-wide singletons. The LLM engine is expensive to load, so every screen shares one. */
class KutumbamApp : Application() {
    val llm: LlmEngine by lazy { LiteRtLmEngine(this) }
    val modelStore: ModelStore by lazy { ModelStore(this) }
    val ocr: TextOcr by lazy { TextOcr(this) }
    val repo: Repository by lazy { Repository(this) }
    val speaker: Speaker by lazy { Speaker(this) }
}
