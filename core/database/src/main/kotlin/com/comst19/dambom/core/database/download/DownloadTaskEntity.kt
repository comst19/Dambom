package com.comst19.dambom.core.database.download

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_tasks",
    indices = [Index(value = ["url", "quality"], unique = true)],
)
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val sourcePageUrl: String,
    val host: String,
    val title: String,
    val mimeType: String?,
    val expectedBytes: Long?,
    val downloadedBytes: Long,
    val quality: String,
    val status: String,
    val failureReason: String?,
    val retryCount: Int,
    val deletePending: Boolean,
    val localFileName: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
