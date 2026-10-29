package com.comst19.dambom.feature.library.subtitle.model

internal enum class SubtitleLanguagePair(
    val source: String,
    val target: String,
) {
    ENGLISH_KOREAN("en-US", "ko-KR"),
    JAPANESE_KOREAN("ja-JP", "ko-KR"),
    JAPANESE_ENGLISH("ja-JP", "en-US"),
}
