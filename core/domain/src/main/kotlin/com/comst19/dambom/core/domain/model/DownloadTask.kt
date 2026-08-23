package com.comst19.dambom.core.domain.model

data class DownloadRequest(
    val id: String,
    val url: String,
    val sourcePageUrl: String,
    val title: String,
    val mimeType: String?,
    val expectedBytes: Long?,
    val quality: String = ORIGINAL_QUALITY,
)

data class DownloadTask(
    val id: String,
    val url: String,
    val sourcePageUrl: String,
    val title: String,
    val mimeType: String?,
    val expectedBytes: Long?,
    val downloadedBytes: Long,
    val quality: String,
    val status: DownloadStatus,
    val failureReason: String?,
    val localFileName: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val progress: Float
        get() =
            expectedBytes
                ?.takeIf { it > 0L }
                ?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
                ?: 0f
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
}

data class EnqueueDownloadsResult(
    val addedCount: Int,
    val duplicateCount: Int,
)

const val ORIGINAL_QUALITY = "원본"
