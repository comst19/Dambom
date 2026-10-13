package com.comst19.dambom.core.database.tag

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.comst19.dambom.core.database.download.DownloadTaskEntity

@Entity(tableName = "video_tags", indices = [Index(value = ["name"], unique = true)])
data class VideoTagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "video_tag_assignments",
    primaryKeys = ["videoId", "tagId"],
    indices = [Index(value = ["tagId"])],
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VideoTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VideoTagAssignmentEntity(
    val videoId: String,
    val tagId: String,
)
