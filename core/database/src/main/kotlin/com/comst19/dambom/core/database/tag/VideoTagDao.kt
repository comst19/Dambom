package com.comst19.dambom.core.database.tag

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class VideoTagDao {
    @Query("SELECT * FROM video_tags ORDER BY name COLLATE NOCASE, id")
    abstract fun observeTags(): Flow<List<VideoTagEntity>>

    @Query("SELECT * FROM video_tag_assignments")
    abstract fun observeAssignments(): Flow<List<VideoTagAssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(tag: VideoTagEntity)

    @Query("UPDATE video_tags SET name = :name WHERE id = :id")
    abstract suspend fun rename(
        id: String,
        name: String,
    )

    @Query("DELETE FROM video_tags WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query(
        "INSERT OR IGNORE INTO video_tag_assignments(videoId, tagId) " +
            "SELECT id, :tagId FROM download_tasks WHERE id IN (:videoIds) " +
            "AND status = 'COMPLETED' AND deletePending = 0 AND localFileName IS NOT NULL " +
            "AND EXISTS (SELECT 1 FROM video_tags WHERE id = :tagId)",
    )
    protected abstract suspend fun addAssignments(
        videoIds: Set<String>,
        tagId: String,
    )

    @Query("DELETE FROM video_tag_assignments WHERE videoId IN (:videoIds) AND tagId = :tagId")
    protected abstract suspend fun removeAssignments(
        videoIds: Set<String>,
        tagId: String,
    )

    @Transaction
    open suspend fun assign(
        videoIds: Set<String>,
        tagId: String,
        assigned: Boolean,
    ) {
        if (assigned) addAssignments(videoIds, tagId) else removeAssignments(videoIds, tagId)
    }
}
