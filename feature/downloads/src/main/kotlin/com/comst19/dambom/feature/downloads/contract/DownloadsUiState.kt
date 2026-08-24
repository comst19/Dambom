package com.comst19.dambom.feature.downloads.contract

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask

internal data class DownloadsUiState(
    val tasks: List<DownloadTask> = emptyList(),
) {
    val activeCount: Int
        get() = tasks.count { it.status == DownloadStatus.DOWNLOADING }

    val totalCount: Int
        get() = tasks.size

    val canPauseAll: Boolean
        get() = tasks.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }

    val canResumeAll: Boolean
        get() = tasks.any { it.status == DownloadStatus.PAUSED }

    val progress: Float
        get() {
            val relevant = tasks.filter { it.status != DownloadStatus.FAILED }
            val totalBytes = relevant.mapNotNull { it.expectedBytes }.sum()
            return if (totalBytes > 0L) {
                (relevant.sumOf { it.downloadedBytes }.toFloat() / totalBytes).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
}
