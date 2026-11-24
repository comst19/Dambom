package com.comst19.dambom.feature.web

import androidx.lifecycle.SavedStateHandle
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.web.contract.WebDetectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebPageValidationTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository =
        object : MediaDetectionRepository {
            override suspend fun detect(url: String) = MediaDetectionResult.Unsupported(UnsupportedReason.NO_MEDIA)
        }

    @Test
    fun `page finish with another URL is ignored even for the current generation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = WebViewModel(repository, SpyNavigationDispatcher(), SavedStateHandle())
            val tabId = viewModel.uiState.value.currentTabId
            viewModel.onPageStarted(tabId, PAGE_URL, "Page", 1L)

            viewModel.onPageFinished(tabId, "https://example.com/other.mp4", "Other", 1L)
            advanceUntilIdle()

            val tab = viewModel.uiState.value.currentTab
            assertEquals(PAGE_URL, tab?.url)
            assertEquals("Page", tab?.title)
            assertEquals(WebDetectionState.Idle, tab?.detectionState)
        }

    @Test
    fun `manual scan accepts a result before a page generation is assigned`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = WebViewModel(repository, SpyNavigationDispatcher(), SavedStateHandle())
            viewModel.navigateCurrentTab(PAGE_URL)
            viewModel.scanCurrentTab()
            advanceUntilIdle()

            assertEquals(
                WebDetectionState.NotFound(UnsupportedReason.NO_MEDIA),
                viewModel.uiState.value.currentTab
                    ?.detectionState,
            )
        }
}

private const val PAGE_URL = "https://example.com/page"
