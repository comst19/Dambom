package com.comst19.dambom.core.domain.model

data class PlaybackHistory(
    val downloadId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val lastPlayedAtMillis: Long,
)
