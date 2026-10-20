package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.PlaybackHistory
import kotlinx.coroutines.flow.Flow

interface PlaybackHistoryRepository {
    fun observeHistory(): Flow<Map<String, PlaybackHistory>>

    suspend fun getHistory(downloadId: String): PlaybackHistory?

    suspend fun saveProgress(
        downloadId: String,
        positionMillis: Long,
        durationMillis: Long,
        lastPlayedAtMillis: Long,
    )
}
