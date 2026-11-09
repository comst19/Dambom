package com.comst19.dambom.feature.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import com.comst19.dambom.feature.library.file.LibraryFileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryListReuseTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test fun `selection and display mode reuse videos while content changes invalidate them`() =
        runTest(dispatcherRule.dispatcher) {
            val repository = LibraryTestDownloads(listOf(savedVideo("first"), savedVideo("second")))
            val context = ApplicationProvider.getApplicationContext<Context>()
            val viewModel =
                LibraryViewModel(
                    repository,
                    LibraryTestSettings,
                    SpyNavigationDispatcher(),
                    SavedStateHandle(),
                    LibraryFileManager(context, dispatcherRule.dispatcher),
                    AppEventBus(),
                    LibraryTestPlaybackHistory(),
                    PrivateVideoSession(),
                    dispatcherRule.dispatcher,
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            val videos = viewModel.uiState.value.videos
            assertEquals(2, videos.size)
            viewModel.toggleSelection("first")
            runCurrent()
            assertSame(videos, viewModel.uiState.value.videos)
            assertEquals(setOf("first"), viewModel.uiState.value.selectedIds)
            viewModel.setViewMode(LibraryViewMode.LIST)
            runCurrent()
            assertSame(videos, viewModel.uiState.value.videos)
            assertEquals(LibraryViewMode.LIST, viewModel.uiState.value.viewMode)
            viewModel.updateQuery("second")
            runCurrent()
            assertEquals(
                listOf("second"),
                viewModel.uiState.value.videos
                    .map { it.id },
            )
            repository.downloads.value = listOf(savedVideo("first"), savedVideo("second").copy(title = "renamed"))
            runCurrent()
            assertTrue(
                viewModel.uiState.value.videos
                    .isEmpty(),
            )
            viewModel.updateQuery("")
            viewModel.setSourceFilter(LibrarySourceFilter.X)
            runCurrent()
            assertTrue(
                viewModel.uiState.value.videos
                    .isEmpty(),
            )
            assertEquals(setOf("first"), viewModel.uiState.value.selectedIds)
        }
}
