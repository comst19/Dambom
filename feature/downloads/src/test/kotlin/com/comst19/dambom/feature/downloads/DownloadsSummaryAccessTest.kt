package com.comst19.dambom.feature.downloads

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.downloads.contract.DownloadsUiState
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsSummaryAccessTest {
    @Test
    fun `reading summary repeatedly does not rescan task snapshot`() {
        val values = List(1_000) { task(it) }.toPersistentList()
        var traversals = 0
        val tasks =
            object : PersistentList<DownloadTask> by values {
                override fun iterator(): Iterator<DownloadTask> {
                    traversals++
                    return values.iterator()
                }
            }
        val state = DownloadsUiState(tasks = tasks)
        val traversalsAtCreation = traversals

        repeat(20) {
            assertEquals(1_000, state.activeCount)
            assertEquals(true, state.canPauseAll)
            assertEquals(false, state.canResumeAll)
            assertEquals(0.25f, state.progress)
        }

        assertEquals("Reading a snapshot must not traverse its tasks again", traversalsAtCreation, traversals)
    }
}

private fun task(index: Int) =
    DownloadTask(
        id = "video-$index",
        url = "https://example.com/$index.mp4",
        sourcePageUrl = "https://example.com",
        title = "Video $index",
        mimeType = "video/mp4",
        expectedBytes = 100L,
        downloadedBytes = 25L,
        quality = "original",
        status = DownloadStatus.DOWNLOADING,
        failureReason = null,
        localFileName = null,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )
