package com.comst19.dambom.feature.downloads.contract

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class DownloadsUiState(
    val tasks: PersistentList<DownloadTask> = persistentListOf(),
    val viewMode: DownloadsViewMode = DownloadsViewMode.GRID,
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
            val measurable = relevant.filter { (it.expectedBytes ?: 0L) > 0L }
            val totalBytes = measurable.sumOf { it.expectedBytes ?: 0L }
            return if (totalBytes > 0L) {
                (measurable.sumOf { it.downloadedBytes }.toFloat() / totalBytes).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
}

internal enum class DownloadsViewMode {
    GRID,
    LIST,
}
