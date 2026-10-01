package com.comst19.dambom.core.database.download

data class DownloadOverviewRow(
    val activeCount: Int,
    val pausedCount: Int,
    val failedCount: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val hasUnknownSize: Boolean,
)

data class DownloadStatusRow(
    val id: String,
    val title: String,
    val status: String,
)
