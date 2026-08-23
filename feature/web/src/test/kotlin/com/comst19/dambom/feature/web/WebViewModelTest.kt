package com.comst19.dambom.feature.web

import androidx.lifecycle.SavedStateHandle
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewModelTest {
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
    fun `media detection state belongs to its tab`() {
        val viewModel = createViewModel()
        val firstTabId = viewModel.uiState.value.currentTabId

        viewModel.onMediaRequest(firstTabId, "https://example.com/video.mp4")
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
