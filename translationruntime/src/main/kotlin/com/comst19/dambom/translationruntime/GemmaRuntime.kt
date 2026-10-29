package com.comst19.dambom.translationruntime

import com.comst19.dambom.feature.library.subtitle.runtime.LocalSubtitleLlm
import com.comst19.dambom.feature.library.subtitle.runtime.LocalSubtitleLlmSession
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class GemmaRuntime : LocalSubtitleLlm {
    override suspend fun open(modelPath: String): LocalSubtitleLlmSession {
        val engine = Engine(EngineConfig(modelPath, Backend.CPU(), maxNumTokens = MODEL_CONTEXT))
        var transferred = false
        try {
            engine.initialize()
            currentCoroutineContext().ensureActive()
            transferred = true
            return GemmaSession(engine)
        } finally {
            if (!transferred) engine.close()
        }
    }
}

private class GemmaSession(
    private val engine: Engine,
) : LocalSubtitleLlmSession {
    override suspend fun translate(prompt: String): String =
        withContext(Dispatchers.IO) {
            val text = StringBuilder()
            val config = ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0))
            engine.createConversation(config).use { conversation ->
                try {
                    val completed =
                        withTimeoutOrNull(INFERENCE_TIMEOUT_MILLIS) {
                            conversation.sendMessageAsync(prompt).collect { message ->
                                message.contents.contents
                                    .filterIsInstance<Content.Text>()
                                    .forEach { text.append(it.text) }
                            }
                            true
                        }
                    check(completed == true) { "Translation timed out" }
                } finally {
                    conversation.cancelProcess()
                }
            }
            text.toString().trim().also { check(it.isNotEmpty()) }
        }

    override fun close() {
        engine.close()
    }
}

private const val MODEL_CONTEXT = 2048
private const val INFERENCE_TIMEOUT_MILLIS = 120_000L
