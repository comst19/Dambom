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

    @Query("SELECT * FROM download_tasks WHERE status = 'COMPLETED' ORDER BY updatedAtMillis DESC")
    fun observeCompleted(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status = 'QUEUED' AND deletePending = 0 ORDER BY createdAtMillis ASC")
    suspend fun getQueued(): List<DownloadTaskEntity>

    @Query("SELECT COUNT(*) FROM download_tasks WHERE url = :url AND quality = :quality")
    suspend fun countBySource(
        url: String,
        quality: String,
    ): Int

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING') AND deletePending = 0")
    suspend fun countSchedulable(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DownloadTaskEntity): Long

    @Query(
        """
        UPDATE download_tasks
        SET status = :nextStatus, failureReason = NULL, updatedAtMillis = :updatedAtMillis
        WHERE id = :id AND status = :expectedStatus AND deletePending = 0
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
        WHERE id = :id AND status = 'DOWNLOADING' AND deletePending = 0
        """,
    )
    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        expectedBytes: Long?,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'COMPLETED', downloadedBytes = :downloadedBytes,
            expectedBytes = :downloadedBytes, localFileName = :localFileName,
            failureReason = NULL, retryCount = 0, updatedAtMillis = :updatedAtMillis
        WHERE id = :id AND status = 'DOWNLOADING' AND deletePending = 0
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
        WHERE id = :id AND status = 'DOWNLOADING' AND deletePending = 0
        """,
    )
    suspend fun markFailed(
        id: String,
        reason: String,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET retryCount = retryCount + 1, updatedAtMillis = :updatedAtMillis
        WHERE id = :id AND status = 'DOWNLOADING' AND retryCount < :maxRetries
            AND deletePending = 0
        """,
    )
    suspend fun incrementNetworkRetry(
        id: String,
        maxRetries: Int,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'FAILED', failureReason = :reason, retryCount = retryCount + 1,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id AND status = 'DOWNLOADING' AND retryCount >= :maxRetries
            AND deletePending = 0
        """,
    )
    suspend fun markNetworkFailed(
        id: String,
        reason: String,
        maxRetries: Int,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'PAUSED', updatedAtMillis = :updatedAtMillis
        WHERE id = :id AND status IN ('QUEUED', 'DOWNLOADING') AND deletePending = 0
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
        WHERE id = :id AND status IN ('PAUSED', 'FAILED') AND deletePending = 0
        """,
    )
    suspend fun queueAgain(
        id: String,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = 'QUEUED', failureReason = NULL, retryCount = 0, updatedAtMillis = :updatedAtMillis
        WHERE id = :id AND status = 'FAILED' AND deletePending = 0
        """,
    )
    suspend fun retry(
        id: String,
        updatedAtMillis: Long,
    ): Int

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
        WHERE status IN ('QUEUED', 'DOWNLOADING') AND deletePending = 0
        """,
    )
    suspend fun pauseAll(updatedAtMillis: Long)

    @Query(
        """
        UPDATE download_tasks
        SET status = 'QUEUED', failureReason = NULL, updatedAtMillis = :updatedAtMillis
        WHERE status = 'PAUSED' AND deletePending = 0
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

    @Query(
        """
        UPDATE download_tasks
        SET deletePending = 1, updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """,
    )
    suspend fun claimForDeletion(
        id: String,
        updatedAtMillis: Long,
    ): Int

    @Query("UPDATE download_tasks SET deletePending = 0 WHERE id = :id AND deletePending = 1")
    suspend fun releaseDeletionClaim(id: String)

    @Query("DELETE FROM download_tasks WHERE id = :id AND deletePending = 1")
    suspend fun deleteClaimed(id: String): Int

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun delete(id: String)
}
