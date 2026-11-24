package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.PlaybackHistory
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
    fun `recently played uses playback time and excludes downloaded but never played videos`() {
        val oldDownload = task("old", DownloadStatus.COMPLETED, "/old.mp4").copy(updatedAtMillis = 1L)
        val newDownload = oldDownload.copy(id = "new", updatedAtMillis = 100L)
        val unplayed = newDownload.copy(id = "unplayed", updatedAtMillis = 200L)
        val snapshot = LibrarySnapshot(listOf(newDownload, oldDownload, unplayed))
        val history =
            mapOf(
                "old" to PlaybackHistory("old", 10L, 100L, 300L),
                "new" to PlaybackHistory("new", 50L, 100L, 200L),
                "deleted" to PlaybackHistory("deleted", 10L, 100L, 400L),
            )
        val state = toLibraryUiState(snapshot, null, recentlyPlayedOnly = true, playbackHistory = history)
        val progressUpdate = history + ("new" to history.getValue("new").copy(positionMillis = 80L))

        assertEquals(listOf(oldDownload, newDownload), state.videos)
        assertEquals(
            state.videos,
            toLibraryUiState(snapshot, null, recentlyPlayedOnly = true, playbackHistory = progressUpdate).videos,
        )
        assertEquals(
            listOf(unplayed, newDownload, oldDownload),
            toLibraryUiState(snapshot, null, playbackHistory = history).videos,
        )
        assertEquals(3, state.totalVideoCount)
    }

    @Test
    fun `recently played intersects search source and favorites while preserving detail`() {
        val matching =
            task("match", DownloadStatus.COMPLETED, "/match.mp4")
                .copy(title = "Travel", isFavorite = true, sourcePageUrl = "https://x.com/user/status/1")
        val ordinary = matching.copy(id = "ordinary", isFavorite = false)
        val web = matching.copy(id = "web", sourcePageUrl = "https://example.com")
        val other = matching.copy(id = "other", title = "Other")
        val videos = listOf(matching, ordinary, web, other)
        val history = videos.associate { it.id to PlaybackHistory(it.id, 10L, 100L, 100L) }
        val state =
            toLibraryUiState(
                LibrarySnapshot(videos),
                ordinary.id,
                query = " travel ",
                sourceFilter = LibrarySourceFilter.X,
                favoritesOnly = true,
                recentlyPlayedOnly = true,
                playbackHistory = history,
            )

        assertEquals(listOf(matching), state.videos)
        assertEquals(ordinary, state.selectedVideo)
        assertTrue(state.recentlyPlayedOnly)
    }

    @Test
    fun `favorites intersect source and search without changing totals or selected detail`() {
        val web = task("web", DownloadStatus.COMPLETED, "/video/web.mp4").copy(isFavorite = true, title = "Travel")
        val x = web.copy(id = "x", sourcePageUrl = "https://x.com/user/status/1")
        val ordinary = x.copy(id = "ordinary", isFavorite = false)
        val other = x.copy(id = "other", title = "Other")
        val snapshot = LibrarySnapshot(listOf(web, x, ordinary, other))

        val state =
            toLibraryUiState(
                snapshot,
                selectedId = ordinary.id,
                query = " travel ",
                sourceFilter = LibrarySourceFilter.X,
                favoritesOnly = true,
                selection = LibrarySelectionState().selectAll(listOf(x.id, ordinary.id)),
            )

        assertEquals(listOf(x), state.videos)
        assertEquals(ordinary, state.selectedVideo)
        assertTrue(state.favoritesOnly)
        assertEquals(4, state.totalVideoCount)
        assertEquals(400L, state.totalBytes)
        assertEquals(1, state.hiddenSelectedCount)
        assertEquals(4, toLibraryUiState(snapshot, selectedId = null).videos.size)
    }

    @Test
    fun `removing last favorite leaves a filtered empty state not an empty library`() {
        val video = task("video", DownloadStatus.COMPLETED, "/video/video.mp4")
        val state = toLibraryUiState(LibrarySnapshot(listOf(video)), selectedId = null, favoritesOnly = true)

        assertTrue(state.hasVideos)
        assertTrue(state.videos.isEmpty())
        assertEquals(1, state.totalVideoCount)
    }

    @Test
    fun `hidden selection count follows search without changing deletion selection`() {
        val first = task("first", DownloadStatus.COMPLETED, "/video/first.mp4")
        val second = task("second", DownloadStatus.COMPLETED, "/video/second.mp4")
        val state =
            toLibraryUiState(
                LibrarySnapshot(listOf(first, second)),
                selectedId = null,
                query = "first",
                selection = LibrarySelectionState().selectAll(listOf(first.id, second.id)),
            )

        assertEquals(1, state.hiddenSelectedCount)
        assertEquals(setOf(first.id, second.id), state.selectedIds)
    }

    @Test
    fun `successful deletions leave only failed items selected`() {
        val selected = LibrarySelectionState().selectAll(listOf("first", "failed", "last"))
        val partial = selected.removeDeleted("first").removeDeleted("last")

        assertEquals(setOf("failed"), partial.selectedIds)
        assertTrue(partial.isActive)
        assertEquals(LibrarySelectionState(), partial.removeDeleted("failed"))
    }

    @Test
    fun `query and selection reuse prepared snapshot without reading source again`() {
        var reads = 0
        val video = task("video", DownloadStatus.COMPLETED, "/video/video.mp4")
        val source =
            object : AbstractList<DownloadTask>() {
                override val size = 1

                override fun get(index: Int): DownloadTask {
                    reads++
                    return video
                }
            }
        val snapshot = LibrarySnapshot(source)
        val initialReads = reads

        val searching = toLibraryUiState(snapshot, selectedId = null, query = "absent")
        val selecting =
            toLibraryUiState(
                snapshot,
                selectedId = video.id,
                selection = LibrarySelectionState(isActive = true).toggle(video.id),
            )

        assertEquals(initialReads, reads)
        assertTrue(searching.videos.isEmpty())
        assertEquals(100L, searching.totalBytes)
        assertEquals(setOf(video.id), selecting.selectedIds)
        assertEquals(video, selecting.selectedVideo)
    }

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
                    ).let(::LibrarySnapshot),
                selectedId = completed.id,
            )

        assertEquals(listOf(completed), state.videos)
        assertEquals(completed, state.selectedVideo)
    }

    @Test
    fun `missing selected id leaves detail empty`() {
        val state =
            toLibraryUiState(
                tasks = LibrarySnapshot(listOf(task("completed", DownloadStatus.COMPLETED, "/video/completed.mp4"))),
                selectedId = "missing",
            )

        assertNull(state.selectedVideo)
    }

    @Test
    fun `newest completed video is shown first`() {
        val older = task("older", DownloadStatus.COMPLETED, "/video/older.mp4").copy(updatedAtMillis = 100L)
        val newer = task("newer", DownloadStatus.COMPLETED, "/video/newer.mp4").copy(updatedAtMillis = 300L)
        val middle = task("middle", DownloadStatus.COMPLETED, "/video/middle.mp4").copy(updatedAtMillis = 200L)

        val state = toLibraryUiState(LibrarySnapshot(listOf(older, newer, middle)), selectedId = null)

        assertEquals(listOf(newer, middle, older), state.videos)
    }

    @Test
    fun `search filters titles without hiding the selected detail`() {
        val selected = task("selected", DownloadStatus.COMPLETED, "/video/selected.mp4")
        val matching =
            task("matching", DownloadStatus.COMPLETED, "/video/matching.mp4").copy(title = "Travel Film")

        val state =
            toLibraryUiState(
                tasks = LibrarySnapshot(listOf(selected, matching)),
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
                tasks = LibrarySnapshot(listOf(task("completed", DownloadStatus.COMPLETED, "/video/completed.mp4"))),
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

        val snapshot = LibrarySnapshot(listOf(web, x))
        val all = toLibraryUiState(snapshot, selectedId = null, sourceFilter = LibrarySourceFilter.ALL)
        val xOnly = toLibraryUiState(snapshot, selectedId = null, sourceFilter = LibrarySourceFilter.X)
        val webOnly = toLibraryUiState(snapshot, selectedId = null, sourceFilter = LibrarySourceFilter.WEB)

        assertEquals(listOf(web, x), all.videos)
        assertEquals(listOf(x), xOnly.videos)
        assertEquals(listOf(web), webOnly.videos)
        assertEquals(2, xOnly.totalVideoCount)
        assertEquals(200L, xOnly.totalBytes)
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
