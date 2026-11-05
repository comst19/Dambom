package com.comst19.dambom.feature.library.subtitle.inference

import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair

internal data class RecognizedSubtitleSpeech(
    val sourceLanguage: String,
    val cues: List<SubtitleCue>,
)

internal suspend fun recognizeSubtitleWithFallback(
    selectedLanguage: String,
    supportedLanguages: List<String> =
        listOf(
            SubtitleLanguagePair.ENGLISH_KOREAN.source,
            SubtitleLanguagePair.JAPANESE_KOREAN.source,
        ),
    recognize: suspend (String) -> List<SubtitleCue>,
): RecognizedSubtitleSpeech {
    for (language in (listOf(selectedLanguage) + supportedLanguages).distinct()) {
        val cues = recognize(language)
        if (cues.isNotEmpty()) return RecognizedSubtitleSpeech(language, cues)
    }
    return RecognizedSubtitleSpeech(selectedLanguage, emptyList())
}
