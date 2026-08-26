package com.comst19.dambom.feature.downloads

import androidx.lifecycle.SavedStateHandle
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.downloads.contract.DownloadsUiState
import com.comst19.dambom.feature.downloads.contract.DownloadsViewMode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DownloadsUiStateTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `summary counts active queue and weighted progress`() {
        val state =
            DownloadsUiState(
                tasks =
                    persistentListOf(
                        task("active", DownloadStatus.DOWNLOADING, 50L, 100L),
                        task("queued", DownloadStatus.QUEUED, 0L, 100L),
                        task("done", DownloadStatus.COMPLETED, 100L, 100L),
                    ),
            )

        assertEquals(1, state.activeCount)
        assertEquals(3, state.totalCount)
        assertEquals(0.5f, state.progress)
        assertTrue(state.canPauseAll)
        assertEquals(DownloadsViewMode.GRID, state.viewMode)
    }

    @Test
    fun `view mode restores from saved state`() {
        val savedStateHandle = SavedStateHandle()
        DownloadsViewModel(EmptyDownloadRepository, SpyNavigationDispatcher(), savedStateHandle)
            .setViewMode(DownloadsViewMode.LIST)

        val restored = DownloadsViewModel(EmptyDownloadRepository, SpyNavigationDispatcher(), savedStateHandle)

        assertEquals(DownloadsViewMode.LIST, restored.uiState.value.viewMode)
    }

    @Test
    fun `thumbnails load only from completed local files`() {
        assertNull(task("paused", DownloadStatus.PAUSED, 0L, 100L).thumbnailSource())
        assertNull(task("downloading", DownloadStatus.DOWNLOADING, 50L, 100L).thumbnailSource())
        assertEquals(
            "/videos/completed.mp4",
            task(
                id = "completed",
                status = DownloadStatus.COMPLETED,
                downloadedBytes = 100L,
                expectedBytes = 100L,
                localFilePath = "/videos/completed.mp4",
            ).thumbnailSource(),
        )
    }
}

private object EmptyDownloadRepository : DownloadRepository {
    override val downloads: Flow<List<DownloadTask>> = flowOf(emptyList())

    override suspend fun enqueue(requests: List<DownloadRequest>): EnqueueDownloadsResult =
        EnqueueDownloadsResult(addedCount = 0, duplicateCount = 0)

    override suspend fun pause(id: String) = Unit

    override suspend fun resume(id: String) = Unit

    override suspend fun cancel(id: String) = Unit

    override suspend fun rename(
        id: String,
        title: String,
    ) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun retry(id: String) = Unit

    override suspend fun pauseAll() = Unit

    override suspend fun resumeAll() = Unit

    override suspend fun refreshNetworkPolicy() = Unit
}

private fun task(
    id: String,
    status: DownloadStatus,
    downloadedBytes: Long,
    expectedBytes: Long,
    localFilePath: String? = null,
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
    localFilePath = localFilePath,
    createdAtMillis = 1L,
    updatedAtMillis = 1L,
)
