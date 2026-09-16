package com.comst19.dambom.feature.home

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.home.contract.HomeDownloadSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDownloadSummaryMapperTest {
    @Test
    fun `only active known size downloads contribute to progress`() {
        val active = task(DownloadStatus.DOWNLOADING, 25L, 100L)
        val summary =
            toHomeDownloadSummary(
                listOf(
                    active,
                    task(DownloadStatus.QUEUED, 0L, 100L),
                    task(DownloadStatus.DOWNLOADING, 1_000L, null),
                    task(DownloadStatus.PAUSED, 100L, 100L),
                    task(DownloadStatus.FAILED, 100L, 100L),
                    task(DownloadStatus.COMPLETED, 100L, 100L),
                ),
            )

        assertEquals(HomeDownloadSummary(activeCount = 3, pausedCount = 1, failedCount = 1, progress = 0.125f), summary)
        assertEquals(HomeDownloadSummary(), toHomeDownloadSummary(emptyList()))
    }
}

private fun task(
    status: DownloadStatus,
    downloadedBytes: Long,
    expectedBytes: Long?,
) = DownloadTask(
    id = status.name,
    url = "https://example.com/video.mp4",
    sourcePageUrl = "https://example.com",
    title = "Video",
    mimeType = "video/mp4",
    expectedBytes = expectedBytes,
    downloadedBytes = downloadedBytes,
    quality = "original",
    status = status,
    failureReason = null,
    localFileName = null,
    createdAtMillis = 0L,
    updatedAtMillis = 0L,
)
