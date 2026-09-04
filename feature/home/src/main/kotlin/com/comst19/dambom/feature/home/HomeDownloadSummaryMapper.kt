package com.comst19.dambom.feature.home

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.home.contract.HomeDownloadSummary

internal fun toHomeDownloadSummary(tasks: List<DownloadTask>): HomeDownloadSummary {
    var activeCount = 0
    var pausedCount = 0
    var failedCount = 0
    var downloadedBytes = 0L
    var totalBytes = 0L
    for (task in tasks) {
        when (task.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                activeCount++
                val expectedBytes = task.expectedBytes ?: 0L
                if (expectedBytes > 0L) {
                    downloadedBytes += task.downloadedBytes
                    totalBytes += expectedBytes
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
    return HomeDownloadSummary(
        activeCount = activeCount,
        pausedCount = pausedCount,
        failedCount = failedCount,
        progress = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f,
    )
}
