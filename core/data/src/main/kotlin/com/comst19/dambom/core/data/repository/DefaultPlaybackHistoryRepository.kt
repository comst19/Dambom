package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.coroutine.DefaultDispatcher
import com.comst19.dambom.core.database.playback.PlaybackHistoryDao
import com.comst19.dambom.core.database.playback.PlaybackHistoryEntity
import com.comst19.dambom.core.domain.model.PlaybackHistory
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultPlaybackHistoryRepository
    @Inject
    constructor(
        private val dao: PlaybackHistoryDao,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : PlaybackHistoryRepository {
        override fun observeHistory() =
            dao
                .observeHistory()
                .map { rows -> rows.associate { row -> row.downloadId to row.toDomain() } }
                .flowOn(defaultDispatcher)

        override suspend fun getHistory(downloadId: String): PlaybackHistory? = dao.getHistory(downloadId)?.toDomain()

        override suspend fun saveProgress(
            downloadId: String,
            positionMillis: Long,
            durationMillis: Long,
            lastPlayedAtMillis: Long,
        ) {
            val duration = durationMillis.coerceAtLeast(0)
            val position = positionMillis.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
            dao.saveProgress(downloadId, position, duration, lastPlayedAtMillis)
        }
    }

private fun PlaybackHistoryEntity.toDomain() =
    PlaybackHistory(
        downloadId = downloadId,
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        lastPlayedAtMillis = lastPlayedAtMillis,
    )
