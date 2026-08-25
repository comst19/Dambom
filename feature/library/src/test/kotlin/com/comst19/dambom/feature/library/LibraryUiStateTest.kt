package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.contract.LibraryViewMode
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

    @Test
    fun `search filters titles without hiding the selected detail`() {
        val selected = task("selected", DownloadStatus.COMPLETED, "/video/selected.mp4")
        val matching =
            task("matching", DownloadStatus.COMPLETED, "/video/matching.mp4").copy(title = "Travel Film")

        val state =
            toLibraryUiState(
                tasks = listOf(selected, matching),
                selectedId = selected.id,
                query = "travel",
            )

        assertEquals(listOf(matching), state.videos)
        assertEquals(selected, state.selectedVideo)
        assertEquals("travel", state.query)
        assertEquals(true, state.hasVideos)
    }

    @Test
    fun `selected view mode is kept in ui state`() {
        val state =
            toLibraryUiState(
                tasks = listOf(task("completed", DownloadStatus.COMPLETED, "/video/completed.mp4")),
                selectedId = null,
                viewMode = LibraryViewMode.LIST,
            )

        assertEquals(LibraryViewMode.LIST, state.viewMode)
    }

    @Test
    fun `suggested file name removes invalid characters and keeps extension`() {
        val task =
            task("video", DownloadStatus.COMPLETED, "/video/source.mp4")
                .copy(title = "여행:서울/2026.mp4")

        assertEquals("여행_서울_2026.mp4", task.suggestedFileName())
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
