package com.comst19.dambom.feature.web

import androidx.lifecycle.SavedStateHandle
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
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
    fun `detection result opens only after media is found`() =
        runTest(mainDispatcherRule.dispatcher) {
            val navigation = SpyNavigationDispatcher()
            val viewModel = WebViewModel(FakeMediaDetectionRepository, navigation, SavedStateHandle())
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

            assertEquals(
                NavigationEvent.Navigate(DetectionResultKey("https://example.com")),
                navigation.dispatched.single(),
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
    fun `media request is ignored until the current page finishes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            val tabId = viewModel.uiState.value.currentTabId
            viewModel.onPageStarted(tabId, FIRST_PAGE_URL, "First", 1L)

            viewModel.onMediaRequest(tabId, 1L, "https://example.com/first.mp4")
            runCurrent()
            assertEquals(
                WebDetectionState.Idle,
                viewModel.uiState.value.currentTab
                    ?.detectionState,
            )

            viewModel.onPageFinished(tabId, FIRST_PAGE_URL, "First", 1L)
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
