package com.comst19.dambom.feature.downloads.contract

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap

@Immutable
internal data class DownloadsUiState(
    val tasks: PersistentList<DownloadTask> = persistentListOf(),
    val viewMode: DownloadsViewMode = DownloadsViewMode.GRID,
) {
    val tasksByStatus: PersistentMap<DownloadStatus, PersistentList<DownloadTask>> =
        tasks.groupBy(DownloadTask::status).mapValues { (_, group) -> group.toPersistentList() }.toPersistentMap()

    val activeCount: Int
        get() = tasksByStatus[DownloadStatus.DOWNLOADING]?.size ?: 0

    val totalCount: Int
        get() = tasks.size

    val canPauseAll: Boolean
        get() = activeCount > 0 || !tasksByStatus[DownloadStatus.QUEUED].isNullOrEmpty()

    val canResumeAll: Boolean
        get() = !tasksByStatus[DownloadStatus.PAUSED].isNullOrEmpty()

    val progress: Float = downloadProgress(tasks)
}

private fun downloadProgress(tasks: List<DownloadTask>): Float {
    var downloadedBytes = 0L
    var totalBytes = 0L
    for (task in tasks) {
        val expectedBytes = task.expectedBytes ?: 0L
        if (task.status != DownloadStatus.FAILED && expectedBytes > 0L) {
            downloadedBytes += task.downloadedBytes
            totalBytes += expectedBytes
        }
    }
    return if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

internal enum class DownloadsViewMode {
    GRID,
    LIST,
}
