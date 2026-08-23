package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryUiStateTest {
    @Test
    fun `only completed files are shown and selected`() {
        val completed = task("completed", DownloadStatus.COMPLETED, "/video/completed.mp4")
        val state =
            toLibraryUiState(
                tasks =
                    listOf(
                        completed,
                        task("missing", DownloadStatus.COMPLETED, null),
                        task("active", DownloadStatus.DOWNLOADING, "/video/active.mp4"),
                    ),
                selectedId = completed.id,
            )

        assertEquals(listOf(completed), state.videos)
        assertEquals(completed, state.selectedVideo)
    }

    @Test
    fun `missing selected id leaves detail empty`() {
        val state =
            toLibraryUiState(
                tasks = listOf(task("completed", DownloadStatus.COMPLETED, "/video/completed.mp4")),
                selectedId = "missing",
            )

        assertNull(state.selectedVideo)
    }
}

private fun task(
    id: String,
    status: DownloadStatus,
    path: String?,
) = DownloadTask(
    id = id,
    url = "https://example.com/$id.mp4",
    sourcePageUrl = "https://example.com",
    title = id,
    mimeType = "video/mp4",
    expectedBytes = 100L,
    downloadedBytes = 100L,
    quality = "원본",
    status = status,
    failureReason = null,
    localFileName = "$id.mp4",
    localFilePath = path,
    createdAtMillis = 1L,
    updatedAtMillis = 1L,
)
