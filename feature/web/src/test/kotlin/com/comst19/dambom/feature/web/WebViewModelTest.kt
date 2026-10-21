package com.comst19.dambom.feature.web

import androidx.lifecycle.SavedStateHandle
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaResourceRequest
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.domain.repository.MediaDetectionSnapshots
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.web.contract.MAX_WEB_TABS
import com.comst19.dambom.feature.web.contract.RecentPage
import com.comst19.dambom.feature.web.contract.WebDetectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `rescanning preserves captured headers in the result snapshot`() =
        runTest(mainDispatcherRule.dispatcher) {
            val mediaUrl = "https://example.com/video.mp4"
            val candidate = MediaCandidate("video", mediaUrl, "Video", "video/mp4", null)
            val repository =
                object : MediaDetectionRepository {
                    override suspend fun detect(url: String) = MediaDetectionResult.Success("Page", listOf(candidate))
                }
            val navigation = SpyNavigationDispatcher()
            val snapshots = MediaDetectionSnapshots()
            val viewModel = WebViewModel(repository, navigation, SavedStateHandle(), snapshots)
            val id = viewModel.uiState.value.currentTabId
            viewModel.onPageStarted(id, FIRST_PAGE_URL, "Page", 1L)
            val headers = mapOf("Cookie" to "session=test")
            viewModel.onMediaRequest(id, 1L, MediaResourceRequest(mediaUrl, headers))
            advanceUntilIdle()
            viewModel.scanCurrentTab()
            advanceUntilIdle()
            viewModel.openDetectedMedia()
            advanceUntilIdle()
            val key = (navigation.dispatched.single() as NavigationEvent.Navigate).key as DetectionResultKey
            val result = snapshots.get(checkNotNull(key.snapshotId), key.url)
            assertEquals(headers, result?.candidates?.single()?.requestHeaders)
        }

    @Test
    fun `unknown resource probe budget does not hide later video requests`() =
        runTest {
            val viewModel = createViewModel()
            val id = viewModel.uiState.value.currentTabId
            viewModel.onPageStarted(id, FIRST_PAGE_URL, "Page", 1L)
            repeat(140) { viewModel.onMediaRequest(id, 1L, "https://example.com/api/$it") }
            advanceUntilIdle()
            viewModel.onMediaRequest(id, 1L, "https://example.com/video.mp4")
            advanceUntilIdle()
            assertEquals(
                WebDetectionState.Found(1),
                viewModel.uiState.value.currentTab
                    ?.detectionState,
            )
        }

    @Test
    fun `web accepts uppercase explicit schemes and still supplies a missing scheme`() {
        assertEquals("HTTP://example.com/video.mp4", "HTTP://example.com/video.mp4".normalizeAddress())
        assertEquals("https://example.com/video.mp4", "example.com/video.mp4".normalizeAddress())
    }

    @Test
    fun `video extension is read from the uri path only`() {
        assertTrue("https://example.com/video.mp4#player".hasVideoExtension())
        assertTrue("https://example.com/video.mp4?token=x#player".hasVideoExtension())
        assertEquals(false, "https://example.com/video.jpg#fake.mp4".hasVideoExtension())
    }

    @Test
    fun `tabs can be created selected and closed without leaving an empty list`() {
        val viewModel = createViewModel()

        viewModel.applyInitialUrl("example.com")
        val firstTabId = viewModel.uiState.value.currentTabId
        viewModel.createTab("https://media.w3.org")
        val secondTabId = viewModel.uiState.value.currentTabId

        assertEquals(2, viewModel.uiState.value.tabs.size)
        assertEquals(
            "https://media.w3.org",
            viewModel
                .uiState
                .value
                .currentTab
                ?.url,
        )

        viewModel.selectTab(firstTabId)
        viewModel.closeTab(firstTabId)

        assertEquals(secondTabId, viewModel.uiState.value.currentTabId)

        viewModel.closeTab(secondTabId)

        assertEquals(1, viewModel.uiState.value.tabs.size)
        assertNull(
            viewModel
                .uiState
                .value
                .currentTab
                ?.url,
        )
    }

    @Test
    fun `tab creation is bounded while preserving the current tab`() {
        val viewModel = createViewModel()

        repeat(MAX_WEB_TABS - 1) { viewModel.createTab("https://example.com/$it") }
        val currentTabId = viewModel.uiState.value.currentTabId
        viewModel.createTab("https://example.com/overflow")

        assertEquals(MAX_WEB_TABS, viewModel.uiState.value.tabs.size)
        assertEquals(currentTabId, viewModel.uiState.value.currentTabId)
    }

    @Test
    fun `inactive web states retain only a small recent set`() {
        val viewModel = createViewModel()

        repeat(4) { tabId -> viewModel.saveWebState(tabId.toLong(), android.os.Bundle()) }

        assertNull(viewModel.savedWebState(0L))
        assertTrue(viewModel.savedWebState(1L) != null)
        assertTrue(viewModel.savedWebState(1L) != null)
    }

    @Test
    fun `media detection state belongs to its tab`() {
        val viewModel = createViewModel()
        val firstTabId = viewModel.uiState.value.currentTabId

        viewModel.onPageStarted(firstTabId, FIRST_PAGE_URL, "First", 1L)
        viewModel.onPageFinished(firstTabId, FIRST_PAGE_URL, "First", 1L)
        viewModel.onMediaRequest(firstTabId, 1L, "https://example.com/video.mp4")
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        viewModel.createTab()

        val firstTab =
            viewModel
                .uiState
                .value
                .tabs
                .first { it.id == firstTabId }
        assertEquals(WebDetectionState.Found(1), firstTab.detectionState)
        assertTrue(
            viewModel
                .uiState
                .value
                .currentTab
                ?.detectionState is WebDetectionState.Idle,
        )
    }

    @Test
    fun `x quality requests count as one detected video`() {
        val viewModel = createViewModel()
        val tabId = viewModel.uiState.value.currentTabId
        viewModel.onPageStarted(tabId, FIRST_PAGE_URL, "First", 1L)
        viewModel.onPageFinished(tabId, FIRST_PAGE_URL, "First", 1L)

        viewModel.onMediaRequest(
            tabId,
            1L,
            "https://video.twimg.com/ext_tw_video/123/pu/vid/180x320/low.mp4",
        )
        viewModel.onMediaRequest(
            tabId,
            1L,
            "https://video.twimg.com/ext_tw_video/123/pu/vid/360x640/medium.mp4",
        )
        viewModel.onMediaRequest(
            tabId,
            1L,
            "https://video.twimg.com/ext_tw_video/123/pu/vid/720x1280/high.mp4",
        )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(
            WebDetectionState.Found(1),
            viewModel.uiState.value.currentTab
                ?.detectionState,
        )
    }

    @Test
    fun `tabs current selection and recent pages restore from saved state`() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle)
        val firstTabId = viewModel.uiState.value.currentTabId
        viewModel.navigateCurrentTab("example.com")
        viewModel.updatePage(firstTabId, "https://example.com/article", "Article")
        viewModel.createTab("https://media.w3.org")

        val restored = createViewModel(savedStateHandle).uiState.value

        assertEquals(2, restored.tabs.size)
        assertEquals("https://media.w3.org", restored.currentTab?.url)
        assertEquals(RecentPage("Article", "https://example.com/article"), restored.recentPages.single())
    }

    @Test
    fun `legacy localized empty tab title restores as display placeholder`() {
        val savedStateHandle =
            SavedStateHandle(
                mapOf(
                    "web-tab-ids" to longArrayOf(1L),
                    "web-tab-titles" to arrayListOf("새 탭"),
                ),
            )

        val restored = createViewModel(savedStateHandle).uiState.value

        assertEquals("", restored.currentTab?.title)
    }

    @Test
    fun `media detection failure restores after activity recreation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val savedStateHandle = SavedStateHandle()
            val viewModel = createViewModel(savedStateHandle)
            viewModel.applyInitialUrl("https://example.com")

            viewModel.scanCurrentTab()
            advanceUntilIdle()

            val restored = createViewModel(savedStateHandle).uiState.value

            assertEquals(
                WebDetectionState.NotFound(UnsupportedReason.NO_MEDIA),
                restored.currentTab?.detectionState,
            )
        }

    @Test
    fun `shared origin is restricted to the original shared page`() =
        runTest(mainDispatcherRule.dispatcher) {
            for (page in listOf(FIRST_PAGE_URL, SECOND_PAGE_URL)) {
                val navigation = SpyNavigationDispatcher()
                val viewModel = WebViewModel(FakeMediaDetectionRepository, navigation, SavedStateHandle())
                viewModel.applyInitialUrl(FIRST_PAGE_URL, sharedLink = true)
                val tabId = viewModel.uiState.value.currentTabId
                viewModel.onPageStarted(tabId, page, "Page", 1L)
                viewModel.onPageFinished(tabId, page, "Page", 1L)
                viewModel.onMediaRequest(tabId, 1L, "https://example.com/video.mp4")
                runCurrent()

                viewModel.openDetectedMedia()
                advanceUntilIdle()

                val key = (navigation.dispatched.single() as NavigationEvent.Navigate).key as DetectionResultKey
                assertEquals(page == FIRST_PAGE_URL, key.sharedLink)
            }
        }

    @Test
    fun `navigating another tab preserves shared origin after recreation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val navigation = SpyNavigationDispatcher()
            val savedState = SavedStateHandle()
            val original = WebViewModel(FakeMediaDetectionRepository, navigation, savedState)
            original.applyInitialUrl(FIRST_PAGE_URL, sharedLink = true)
            val sharedTab = original.uiState.value.currentTabId
            original.createTab()
            original.navigateCurrentTab(SECOND_PAGE_URL)
            original.selectTab(sharedTab)
            val restored = WebViewModel(FakeMediaDetectionRepository, navigation, savedState)
            restored.onPageStarted(sharedTab, FIRST_PAGE_URL, "Shared", 1L)
            restored.onMediaRequest(sharedTab, 1L, "https://example.com/video.mp4")
            runCurrent()

            restored.openDetectedMedia()
            advanceUntilIdle()

            val key = (navigation.dispatched.single() as NavigationEvent.Navigate).key as DetectionResultKey
            assertTrue(key.sharedLink)
        }

    @Test
    fun `returning to the shared page does not revive its original consent`() =
        runTest(mainDispatcherRule.dispatcher) {
            val navigation = SpyNavigationDispatcher()
            val viewModel = WebViewModel(FakeMediaDetectionRepository, navigation, SavedStateHandle())
            viewModel.applyInitialUrl(FIRST_PAGE_URL, sharedLink = true)
            val tabId = viewModel.uiState.value.currentTabId
            viewModel.onPageStarted(tabId, SECOND_PAGE_URL, "Other", 1L)
            viewModel.onPageStarted(tabId, FIRST_PAGE_URL, "Returned", 2L)
            viewModel.onMediaRequest(tabId, 2L, "https://example.com/video.mp4")
            runCurrent()

            viewModel.openDetectedMedia()
            advanceUntilIdle()

            val key = (navigation.dispatched.single() as NavigationEvent.Navigate).key as DetectionResultKey
            assertEquals(false, key.sharedLink)
        }

    @Test
    fun `opening detected media captures the tab before dispatch`() =
        runTest(mainDispatcherRule.dispatcher) {
            val navigation = SpyNavigationDispatcher()
            val viewModel = WebViewModel(FakeMediaDetectionRepository, navigation, SavedStateHandle())
            viewModel.applyInitialUrl(FIRST_PAGE_URL, sharedLink = true)
            val tabId = viewModel.uiState.value.currentTabId
            viewModel.onPageStarted(tabId, FIRST_PAGE_URL, "Shared", 1L)
            viewModel.onMediaRequest(tabId, 1L, "https://example.com/video.mp4")
            runCurrent()

            viewModel.openDetectedMedia()
            viewModel.createTab(SECOND_PAGE_URL)
            advanceUntilIdle()

            val key = (navigation.dispatched.single() as NavigationEvent.Navigate).key as DetectionResultKey
            assertEquals(FIRST_PAGE_URL, key.url)
            assertTrue(key.sharedLink)
        }

    @Test
    fun `detection result opens only after media is found`() =
        runTest(mainDispatcherRule.dispatcher) {
            val navigation = SpyNavigationDispatcher()
            val snapshots = MediaDetectionSnapshots()
            val viewModel = WebViewModel(FakeMediaDetectionRepository, navigation, SavedStateHandle(), snapshots)
            viewModel.applyInitialUrl("https://example.com")
            viewModel.onPageStarted(
                viewModel.uiState.value.currentTabId,
                "https://example.com",
                "Example",
                1L,
            )
            viewModel.onPageFinished(
                viewModel.uiState.value.currentTabId,
                "https://example.com",
                "Example",
                1L,
            )

            viewModel.openDetectedMedia()
            advanceUntilIdle()
            assertTrue(navigation.dispatched.isEmpty())

            viewModel.onMediaRequest(
                viewModel.uiState.value.currentTabId,
                1L,
                "https://example.com/video.mp4",
            )
            runCurrent()
            viewModel.openDetectedMedia()
            advanceUntilIdle()

            val destination = (navigation.dispatched.single() as NavigationEvent.Navigate).key as DetectionResultKey
            assertEquals("https://example.com", destination.url)
            assertTrue(destination.snapshotId != null)
            assertEquals(
                "https://example.com/video.mp4",
                snapshots
                    .get(checkNotNull(destination.snapshotId), destination.url)
                    ?.candidates
                    ?.single()
                    ?.url,
            )
        }

    @Test
    fun `page navigation clears media detected on the previous page`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            val tabId = viewModel.uiState.value.currentTabId
            viewModel.navigateCurrentTab(FIRST_PAGE_URL)
            viewModel.onPageStarted(tabId, FIRST_PAGE_URL, "First", 1L)
            viewModel.onPageFinished(tabId, FIRST_PAGE_URL, "First", 1L)
            viewModel.onMediaRequest(tabId, 1L, "https://example.com/first.mp4")
            runCurrent()

            viewModel.updatePage(tabId, SECOND_PAGE_URL, "Second")

            assertEquals(
                WebDetectionState.Idle,
                viewModel.uiState.value.currentTab
                    ?.detectionState,
            )
        }

    @Test
    fun `current page media is collected during loading and stale generations are ignored`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            val tabId = viewModel.uiState.value.currentTabId
            viewModel.onPageStarted(tabId, FIRST_PAGE_URL, "First", 1L)

            viewModel.onMediaRequest(tabId, 1L, "https://example.com/first.mp4")
            runCurrent()
            assertEquals(
                WebDetectionState.Found(1),
                viewModel.uiState.value.currentTab
                    ?.detectionState,
            )

            viewModel.onPageFinished(tabId, FIRST_PAGE_URL, "First", 1L)
            viewModel.onMediaRequest(tabId, 0L, "https://example.com/stale.mp4")
            viewModel.onMediaRequest(tabId, 1L, "https://example.com/first.mp4")
            runCurrent()
            assertEquals(
                WebDetectionState.Found(1),
                viewModel.uiState.value.currentTab
                    ?.detectionState,
            )
        }

    @Test
    fun `direct video url is detected when the page finishes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            val tabId = viewModel.uiState.value.currentTabId
            val videoUrl = "https://example.com/direct.mp4"

            viewModel.onPageStarted(tabId, videoUrl, "Direct", 1L)
            viewModel.onPageFinished(tabId, videoUrl, "Direct", 1L)
            runCurrent()

            assertEquals(
                WebDetectionState.Found(1),
                viewModel.uiState.value.currentTab
                    ?.detectionState,
            )
        }

    @Test
    fun `scan result from a previous page is ignored`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = ControllableMediaDetectionRepository()
            val viewModel = WebViewModel(repository, FakeNavigationDispatcher, SavedStateHandle())
            val tabId = viewModel.uiState.value.currentTabId
            viewModel.navigateCurrentTab(FIRST_PAGE_URL)
            viewModel.scanCurrentTab()
            runCurrent()

            viewModel.updatePage(tabId, SECOND_PAGE_URL, "Second")
            repository.complete(MediaDetectionResult.Unsupported(UnsupportedReason.NO_MEDIA))
            advanceUntilIdle()

            assertEquals(
                WebDetectionState.Idle,
                viewModel.uiState.value.currentTab
                    ?.detectionState,
            )
        }
}

private class ControllableMediaDetectionRepository : MediaDetectionRepository {
    private val result = CompletableDeferred<MediaDetectionResult>()

    override suspend fun detect(url: String): MediaDetectionResult = result.await()

    fun complete(value: MediaDetectionResult) {
        result.complete(value)
    }
}

private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
    WebViewModel(FakeMediaDetectionRepository, FakeNavigationDispatcher, savedStateHandle)

private object FakeMediaDetectionRepository : MediaDetectionRepository {
    override suspend fun detect(url: String): MediaDetectionResult = MediaDetectionResult.Unsupported(UnsupportedReason.NO_MEDIA)
}

private object FakeNavigationDispatcher : NavigationDispatcher {
    override val events: Flow<NavigationEvent> = emptyFlow()

    override suspend fun dispatch(event: NavigationEvent) = Unit
}

private const val FIRST_PAGE_URL = "https://example.com/first"
private const val SECOND_PAGE_URL = "https://example.com/second"
