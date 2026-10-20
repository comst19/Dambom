package com.comst19.dambom.core.database.playback

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history")
    fun observeHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE downloadId = :downloadId")
    suspend fun getHistory(downloadId: String): PlaybackHistoryEntity?

    @Transaction
    suspend fun saveProgress(
        downloadId: String,
        positionMillis: Long,
        durationMillis: Long,
        lastPlayedAtMillis: Long,
    ) {
        insertIfPlayable(downloadId, positionMillis, durationMillis, lastPlayedAtMillis)
        updateProgress(downloadId, positionMillis, durationMillis, lastPlayedAtMillis)
    }

    @Query(
        """
        INSERT OR IGNORE INTO playback_history (downloadId, positionMillis, durationMillis, lastPlayedAtMillis)
        SELECT id, :positionMillis, :durationMillis, :lastPlayedAtMillis FROM download_tasks
        WHERE id = :downloadId AND status = 'COMPLETED' AND deletePending = 0 AND localFileName IS NOT NULL
        """,
    )
    suspend fun insertIfPlayable(
        downloadId: String,
        positionMillis: Long,
        durationMillis: Long,
        lastPlayedAtMillis: Long,
    )

    @Query(
        """
        UPDATE playback_history SET positionMillis = :positionMillis, durationMillis = :durationMillis,
            lastPlayedAtMillis = :lastPlayedAtMillis
        WHERE downloadId = :downloadId AND lastPlayedAtMillis <= :lastPlayedAtMillis
            AND EXISTS (SELECT 1 FROM download_tasks WHERE id = :downloadId
                AND status = 'COMPLETED' AND deletePending = 0 AND localFileName IS NOT NULL)
        """,
    )
    suspend fun updateProgress(
        downloadId: String,
        positionMillis: Long,
        durationMillis: Long,
        lastPlayedAtMillis: Long,
    )
}
