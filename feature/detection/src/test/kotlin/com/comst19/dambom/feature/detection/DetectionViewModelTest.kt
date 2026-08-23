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
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            assertEquals(NavigationEvent.Replace(DownloadsKey), navigation.dispatched.last())
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
                    ),
                ),
        )
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

    override suspend fun retry(id: String) = Unit

    override suspend fun pauseAll() = Unit

    override suspend fun resumeAll() = Unit
}

private const val SOURCE_URL = "https://example.com/page"
private const val MEDIA_URL = "https://example.com/video.mp4"
