package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask

internal class LibrarySnapshot(
    tasks: List<DownloadTask>,
) {
    val videos =
        tasks
            .filter { it.status == DownloadStatus.COMPLETED && it.localFilePath != null }
            .sortedByDescending(DownloadTask::updatedAtMillis)
    val ids = videos.mapTo(hashSetOf(), DownloadTask::id)
    val totalBytes = videos.sumOf(DownloadTask::downloadedBytes)
}
