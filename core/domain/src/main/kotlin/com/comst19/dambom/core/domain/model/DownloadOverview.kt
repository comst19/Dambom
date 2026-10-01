package com.comst19.dambom.core.domain.model

data class DownloadOverview(
    val activeCount: Int = 0,
    val pausedCount: Int = 0,
    val failedCount: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val hasUnknownSize: Boolean = false,
) {
    val progress: Float?
        get() =
            if (hasUnknownSize || totalBytes <= 0L) {
                null
            } else {
                (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            }
}

data class DownloadStatusSnapshot(
    val id: String,
    val title: String,
    val status: DownloadStatus,
    val deletePending: Boolean = false,
)

fun List<DownloadTask>.toDownloadOverview(): DownloadOverview {
    var activeCount = 0
    var pausedCount = 0
    var failedCount = 0
    var downloadedBytes = 0L
    var totalBytes = 0L
    var hasUnknownSize = false
    for (task in this) {
        if (task.deletePending) continue
        when (task.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                activeCount++
                val expectedBytes = task.expectedBytes ?: 0L
                if (expectedBytes > 0L) {
                    downloadedBytes += task.downloadedBytes
                    totalBytes += expectedBytes
                } else {
                    hasUnknownSize = true
                }
            }

            DownloadStatus.PAUSED -> {
                pausedCount++
            }

            DownloadStatus.FAILED -> {
                failedCount++
            }

            DownloadStatus.COMPLETED -> {
                Unit
            }
        }
    }
    return DownloadOverview(activeCount, pausedCount, failedCount, downloadedBytes, totalBytes, hasUnknownSize)
}
