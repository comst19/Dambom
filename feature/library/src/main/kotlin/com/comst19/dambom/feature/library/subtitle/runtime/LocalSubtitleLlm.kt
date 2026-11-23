package com.comst19.dambom.feature.library.subtitle.runtime

import android.content.Context

interface LocalSubtitleLlm {
    suspend fun open(modelPath: String): LocalSubtitleLlmSession

    fun openSpeechClassifier(context: Context): LocalSubtitleSpeechClassifier
}

interface LocalSubtitleLlmSession : AutoCloseable {
    suspend fun translate(prompt: String): String
}

interface LocalSubtitleSpeechClassifier : AutoCloseable {
    fun speechScore(samples: ShortArray): Float
}
