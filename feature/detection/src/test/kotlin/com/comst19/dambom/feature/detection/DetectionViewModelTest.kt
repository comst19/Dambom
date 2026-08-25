package com.comst19.dambom.feature.detection

import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.DownloadsKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.WebKey
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.detection.contract.DetectionUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetectionViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `selected candidates are queued and downloads screen replaces detection`() =
        runTest(mainDispatcherRule.dispatcher) {
            val downloads = RecordingDownloadRepository()
            val navigation = SpyNavigationDispatcher()
            val viewModel = DetectionViewModel(SuccessfulDetectionRepository, downloads, navigation)

            viewModel.detect(SOURCE_URL)
            advanceUntilIdle()
            viewModel.downloadSelected()
            advanceUntilIdle()

            assertEquals(listOf(MEDIA_URL), downloads.enqueued.single().map(DownloadRequest::url))
            assertEquals(listOf(MEDIA_QUALITY), downloads.enqueued.single().map(DownloadRequest::quality))
            assertEquals(NavigationEvent.Replace(DownloadsKey), navigation.dispatched.last())
        }

    @Test
    fun `multiple detected videos require an explicit selection`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                DetectionViewModel(
                    MultipleVideoDetectionRepository,
                    RecordingDownloadRepository(),
                    SpyNavigationDispatcher(),
                )

            viewModel.detect(SOURCE_URL)
            advanceUntilIdle()

            val content = viewModel.uiState.value as DetectionUiState.Content
            assertTrue(content.selectedIds.isEmpty())
        }

    @Test
    fun `unsupported page can continue in web with the same URL`() =
        runTest(mainDispatcherRule.dispatcher) {
            val navigation = SpyNavigationDispatcher()
            val viewModel =
                DetectionViewModel(
                    UnsupportedDetectionRepository,
                    RecordingDownloadRepository(),
                    navigation,
                )

            viewModel.detect(SOURCE_URL)
            advanceUntilIdle()
            viewModel.openInWeb()
            advanceUntilIdle()

            assertEquals(NavigationEvent.Replace(WebKey(SOURCE_URL)), navigation.dispatched.last())
        }
}

private object SuccessfulDetectionRepository : MediaDetectionRepository {
    override suspend fun detect(url: String): MediaDetectionResult =
        MediaDetectionResult.Success(
            pageTitle = "테스트 영상",
            candidates =
                listOf(
                    MediaCandidate(
                        id = "video-1",
                        url = MEDIA_URL,
                        title = "video",
                        mimeType = "video/mp4",
                        contentLength = 1024L,
                        quality = MEDIA_QUALITY,
                    ),
                ),
        )
}

private object MultipleVideoDetectionRepository : MediaDetectionRepository {
    override suspend fun detect(url: String): MediaDetectionResult =
        MediaDetectionResult.Success(
            pageTitle = "여러 영상",
            candidates =
                listOf(
                    MediaCandidate("video-1", "$MEDIA_URL?item=1", "video 1", "video/mp4", null),
                    MediaCandidate("video-2", "$MEDIA_URL?item=2", "video 2", "video/mp4", null),
                ),
        )
}

private object UnsupportedDetectionRepository : MediaDetectionRepository {
    override suspend fun detect(url: String): MediaDetectionResult =
        MediaDetectionResult.Unsupported(com.comst19.dambom.core.domain.model.UnsupportedReason.NO_MEDIA)
}

private class RecordingDownloadRepository : DownloadRepository {
    override val downloads: Flow<List<DownloadTask>> = flowOf(emptyList())
    val enqueued = mutableListOf<List<DownloadRequest>>()

    override suspend fun enqueue(requests: List<DownloadRequest>): EnqueueDownloadsResult {
        enqueued += requests
        return EnqueueDownloadsResult(requests.size, 0)
    }

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

private const val SOURCE_URL = "https://example.com/page"
private const val MEDIA_URL = "https://example.com/video.mp4"
private const val MEDIA_QUALITY = "720×1280 · 2176 kbps"
