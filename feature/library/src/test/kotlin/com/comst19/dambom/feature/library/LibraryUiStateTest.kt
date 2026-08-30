package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import com.comst19.dambom.feature.library.file.suggestedFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `newest completed video is shown first`() {
        val older = task("older", DownloadStatus.COMPLETED, "/video/older.mp4").copy(updatedAtMillis = 100L)
        val newer = task("newer", DownloadStatus.COMPLETED, "/video/newer.mp4").copy(updatedAtMillis = 300L)
        val middle = task("middle", DownloadStatus.COMPLETED, "/video/middle.mp4").copy(updatedAtMillis = 200L)

        val state = toLibraryUiState(listOf(older, newer, middle), selectedId = null)

        assertEquals(listOf(newer, middle, older), state.videos)
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
    fun `selection state supports individual all and clear actions`() {
        val individual = LibrarySelectionState(isActive = true).toggle("one")
        val all = individual.selectAll(listOf("one", "two"))

        assertTrue(individual.isActive)
        assertEquals(setOf("one"), individual.selectedIds)
        assertEquals(setOf("one", "two"), all.selectedIds)
        assertFalse(all.clear().isActive)
        assertTrue(all.clear().selectedIds.isEmpty())
    }

    @Test
    fun `source filter shows all X or web videos independently`() {
        val web = task("web", DownloadStatus.COMPLETED, "/video/web.mp4")
        val x = task("x", DownloadStatus.COMPLETED, "/video/x.mp4").copy(sourcePageUrl = "https://x.com/user/status/1")

        val all = toLibraryUiState(listOf(web, x), selectedId = null, sourceFilter = LibrarySourceFilter.ALL)
        val xOnly = toLibraryUiState(listOf(web, x), selectedId = null, sourceFilter = LibrarySourceFilter.X)
        val webOnly = toLibraryUiState(listOf(web, x), selectedId = null, sourceFilter = LibrarySourceFilter.WEB)

        assertEquals(listOf(web, x), all.videos)
        assertEquals(listOf(x), xOnly.videos)
        assertEquals(listOf(web), webOnly.videos)
        assertEquals(2, xOnly.totalVideoCount)
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
