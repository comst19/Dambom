package com.comst19.dambom.core.database.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAtMillis ASC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status = 'QUEUED' ORDER BY createdAtMillis ASC")
    suspend fun getQueued(): List<DownloadTaskEntity>

    @Query("SELECT COUNT(*) FROM download_tasks WHERE url = :url AND quality = :quality")
    suspend fun countBySource(
        url: String,
        quality: String,
    ): Int

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING')")
    suspend fun countSchedulable(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DownloadTaskEntity): Long

    @Query(
        """
        UPDATE download_tasks
        SET status = :nextStatus, failureReason = NULL, updatedAtMillis = :updatedAtMillis
        WHERE id = :id AND status = :expectedStatus
        """,
    )
    suspend fun compareAndSetStatus(
        id: String,
        expectedStatus: String,
        nextStatus: String,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET downloadedBytes = :downloadedBytes,
            expectedBytes = :expectedBytes,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        expectedBytes: Long?,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = 'COMPLETED', downloadedBytes = :downloadedBytes,
            expectedBytes = :downloadedBytes, localFileName = :localFileName,
            failureReason = NULL, updatedAtMillis = :updatedAtMillis
        WHERE id = :id AND status = 'DOWNLOADING'
        """,
    )
    suspend fun markCompleted(
        id: String,
        downloadedBytes: Long,
        localFileName: String,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'FAILED', failureReason = :reason, updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """,
    )
    suspend fun markFailed(
        id: String,
        reason: String,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = 'PAUSED', updatedAtMillis = :updatedAtMillis
        WHERE id = :id AND status IN ('QUEUED', 'DOWNLOADING')
        """,
    )
    suspend fun pause(
        id: String,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = 'QUEUED', failureReason = NULL, updatedAtMillis = :updatedAtMillis
        WHERE id = :id AND status IN ('PAUSED', 'FAILED')
        """,
    )
    suspend fun queueAgain(
        id: String,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE download_tasks
        SET title = :title, updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """,
    )
    suspend fun updateTitle(
        id: String,
        title: String,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = 'PAUSED', updatedAtMillis = :updatedAtMillis
        WHERE status IN ('QUEUED', 'DOWNLOADING')
        """,
    )
    suspend fun pauseAll(updatedAtMillis: Long)

    @Query(
        """
        UPDATE download_tasks
        SET status = 'QUEUED', failureReason = NULL, updatedAtMillis = :updatedAtMillis
        WHERE status = 'PAUSED'
        """,
    )
    suspend fun resumeAll(updatedAtMillis: Long)

    @Query(
        """
        UPDATE download_tasks
        SET status = 'QUEUED', updatedAtMillis = :updatedAtMillis
        WHERE status = 'DOWNLOADING'
        """,
    )
    suspend fun resetInterrupted(updatedAtMillis: Long)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun delete(id: String)
}
