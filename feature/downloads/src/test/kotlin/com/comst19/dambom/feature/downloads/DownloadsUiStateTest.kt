package com.comst19.dambom.feature.downloads

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsUiStateTest {
    @Test
    fun `summary counts active queue and weighted progress`() {
        val state =
            DownloadsUiState(
                tasks =
                    listOf(
                        task("active", DownloadStatus.DOWNLOADING, 50L, 100L),
                        task("queued", DownloadStatus.QUEUED, 0L, 100L),
                        task("done", DownloadStatus.COMPLETED, 100L, 100L),
                    ),
            )

        assertEquals(1, state.activeCount)
        assertEquals(3, state.totalCount)
        assertEquals(0.5f, state.progress)
        assertTrue(state.canPauseAll)
    }
}

private fun task(
    id: String,
    status: DownloadStatus,
    downloadedBytes: Long,
    expectedBytes: Long,
) = DownloadTask(
    id = id,
    url = "https://example.com/$id.mp4",
    sourcePageUrl = "https://example.com",
    title = id,
    mimeType = "video/mp4",
    expectedBytes = expectedBytes,
    downloadedBytes = downloadedBytes,
    quality = "원본",
    status = status,
    failureReason = null,
    localFileName = null,
    createdAtMillis = 1L,
    updatedAtMillis = 1L,
)
