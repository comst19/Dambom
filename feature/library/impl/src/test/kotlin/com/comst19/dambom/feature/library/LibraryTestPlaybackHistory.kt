package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.PlaybackHistory
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow

internal class LibraryTestPlaybackHistory : PlaybackHistoryRepository {
    val history = MutableStateFlow<Map<String, PlaybackHistory>>(emptyMap())

    override fun observeHistory() = history

    override suspend fun getHistory(downloadId: String): PlaybackHistory? = history.value[downloadId]

    override suspend fun saveProgress(
        downloadId: String,
        positionMillis: Long,
        durationMillis: Long,
        lastPlayedAtMillis: Long,
    ) {
        val entry = PlaybackHistory(downloadId, positionMillis, durationMillis, lastPlayedAtMillis)
        history.value = history.value + (downloadId to entry)
    }
}
