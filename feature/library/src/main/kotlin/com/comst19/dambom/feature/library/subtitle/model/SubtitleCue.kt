package com.comst19.dambom.feature.library.subtitle.model

internal data class SubtitleCue(
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
)
