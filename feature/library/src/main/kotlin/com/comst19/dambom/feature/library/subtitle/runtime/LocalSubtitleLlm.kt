package com.comst19.dambom.feature.library.subtitle.runtime

interface LocalSubtitleLlm {
    suspend fun open(modelPath: String): LocalSubtitleLlmSession
}

interface LocalSubtitleLlmSession : AutoCloseable {
    suspend fun translate(prompt: String): String
}
