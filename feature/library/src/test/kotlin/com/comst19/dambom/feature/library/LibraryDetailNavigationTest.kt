package com.comst19.dambom.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoDetailKey
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.file.LibraryFileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryDetailNavigationTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `first video pushes detail while another selection replaces the existing detail`() =
        runTest(dispatcherRule.dispatcher) {
            val video = libraryTestVideo("/video.mp4")
            val other = video.copy(id = "other")
            val navigation = SpyNavigationDispatcher()
            val viewModel =
                LibraryViewModel(
                    LibraryTestDownloads(listOf(video, other)),
                    LibraryTestSettings,
                    navigation,
                    SavedStateHandle(),
                    LibraryFileManager(ApplicationProvider.getApplicationContext(), dispatcherRule.dispatcher),
                    AppEventBus(),
                    LibraryTestPlaybackHistory(),
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            viewModel.openVideo(video.id)
            runCurrent()
            viewModel.openVideo(other.id, replaceDetail = true)
            runCurrent()
            viewModel.openVideo(video.id, replaceDetail = true)
            runCurrent()

            assertEquals(
                listOf(
                    NavigationEvent.Navigate(VideoDetailKey(video.id)),
                    NavigationEvent.Replace(VideoDetailKey(other.id)),
                    NavigationEvent.Replace(VideoDetailKey(video.id)),
                ),
                navigation.dispatched,
            )
        }
}
