package com.comst19.dambom.feature.web

import androidx.lifecycle.SavedStateHandle
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaResourceRequest
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.domain.repository.MediaDetectionSnapshots
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebMediaMetadataTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `manifest filename uses page title while DOM poster survives the probe`() =
        runTest(mainDispatcherRule.dispatcher) {
            val mediaUrl = "https://example.com/hls/6B1BD4F27A3A40B5A77DE952CBCEBD44.m3u8"
            val candidate =
                MediaCandidate("stream", mediaUrl, "6B1BD4F27A3A40B5A77DE952CBCEBD44", "application/x-mpegURL", null)
            val repository =
                object : MediaDetectionRepository {
                    override suspend fun detect(url: String): MediaDetectionResult =
                        MediaDetectionResult.Success(candidate.title, listOf(candidate))
                }
            val navigation = SpyNavigationDispatcher()
            val snapshots = MediaDetectionSnapshots()
            val viewModel = WebViewModel(repository, navigation, SavedStateHandle(), snapshots)
            val id = viewModel.uiState.value.currentTabId
            viewModel.onPageStarted(id, "https://example.com/page", "Actual page title", 1L)
            viewModel.onMediaRequest(
                id,
                1L,
                MediaResourceRequest(mediaUrl, thumbnailUrl = "https://example.com/poster.png"),
            )
            advanceUntilIdle()
            viewModel.openDetectedMedia()
            advanceUntilIdle()
            val key = (navigation.dispatched.single() as NavigationEvent.Navigate).key as DetectionResultKey
            val detected = snapshots.get(checkNotNull(key.snapshotId), key.url)?.candidates?.single()
            assertEquals("Actual page title", detected?.title)
            assertEquals("https://example.com/poster.png", detected?.thumbnailUrl)
        }
}
