package com.comst19.dambom.core.database.playback

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.comst19.dambom.core.database.download.DownloadTaskEntity

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaybackHistoryEntity(
    @PrimaryKey val downloadId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val lastPlayedAtMillis: Long,
)
