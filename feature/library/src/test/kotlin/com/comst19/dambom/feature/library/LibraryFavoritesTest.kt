package com.comst19.dambom.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.file.LibraryFileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryFavoritesTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `separate list and detail view models synchronize both ways while favorites filter is active`() =
        runTest(dispatcherRule.dispatcher) {
            val video = libraryTestVideo("/video.mp4")
            val repository = LibraryTestDownloads(listOf(video))
            val list = viewModel(repository, SavedStateHandle())
            val detail = viewModel(repository, SavedStateHandle())
            var detailState: VideoDetailState = VideoDetailState.Loading
            backgroundScope.launch { list.uiState.collect() }
            backgroundScope.launch { detail.observeVideo(video.id).collect { detailState = it } }
            runCurrent()
            list.setFavoritesOnly(true)
            list.toggleFavorite(video)
            runCurrent()

            assertTrue((detailState as VideoDetailState.Ready).task.isFavorite)
            assertEquals(
                video.id,
                list.uiState.value.videos
                    .single()
                    .id,
            )
            detail.toggleFavorite((detailState as VideoDetailState.Ready).task)
            runCurrent()

            assertTrue(
                list.uiState.value.videos
                    .isEmpty(),
            )
            assertFalse((detailState as VideoDetailState.Ready).task.isFavorite)
            detail.toggleFavorite((detailState as VideoDetailState.Ready).task)
            runCurrent()
            assertEquals(
                video.id,
                list.uiState.value.videos
                    .single()
                    .id,
            )
        }

    @Test
    fun `toggle updates filtered list and restores filter from saved state`() =
        runTest(dispatcherRule.dispatcher) {
            val video = libraryTestVideo("/video.mp4")
            val repository = LibraryTestDownloads(listOf(video))
            val state = SavedStateHandle()
            val viewModel = viewModel(repository, state)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            viewModel.setFavoritesOnly(true)
            runCurrent()
            assertTrue(
                viewModel.uiState.value.videos
                    .isEmpty(),
            )

            viewModel.toggleFavorite(video)
            runCurrent()
            assertTrue(
                viewModel.uiState.value.videos
                    .single()
                    .isFavorite,
            )
            val restored = viewModel(repository, SavedStateHandle(state.keys().associateWith { state.get<Any>(it) }))
            backgroundScope.launch { restored.uiState.collect() }
            runCurrent()
            assertTrue(restored.uiState.value.favoritesOnly)
            assertEquals(1, restored.uiState.value.videos.size)

            viewModel.toggleFavorite(
                viewModel.uiState.value.videos
                    .single(),
            )
            runCurrent()
            assertTrue(
                viewModel.uiState.value.videos
                    .isEmpty(),
            )
            assertTrue(viewModel.uiState.value.hasVideos)
            viewModel.setFavoritesOnly(false)
            runCurrent()
            assertFalse(
                viewModel.uiState.value.videos
                    .single()
                    .isFavorite,
            )
        }

    @Test
    fun `failed persistence reports error and leaves favorite unchanged`() =
        runTest(dispatcherRule.dispatcher) {
            val video = libraryTestVideo("/video.mp4")
            val repository =
                object : DownloadRepository by LibraryTestDownloads(listOf(video)) {
                    override suspend fun toggleFavorite(id: String): Unit = throw IOException("write failed")
                }
            val events = AppEventBus()
            val viewModel = viewModel(repository, SavedStateHandle(), events)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            viewModel.toggleFavorite(video)
            runCurrent()

            assertFalse(
                viewModel.uiState.value.videos
                    .single()
                    .isFavorite,
            )
            assertEquals(
                AppEvent.ShowSnackbar(UiText.Resource(R.string.library_favorite_failure, emptyList())),
                events.events.first(),
            )
        }

    private fun viewModel(
        repository: DownloadRepository,
        state: SavedStateHandle,
        events: AppEventBus = AppEventBus(),
    ) = LibraryViewModel(
        repository,
        LibraryTestSettings,
        SpyNavigationDispatcher(),
        state,
        LibraryFileManager(ApplicationProvider.getApplicationContext(), dispatcherRule.dispatcher),
        events,
    )
}
