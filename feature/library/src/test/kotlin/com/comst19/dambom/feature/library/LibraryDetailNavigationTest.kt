package com.comst19.dambom.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoDetailKey
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.export.ExportDocumentStore
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
                    LibraryFileManager(
                        ApplicationProvider.getApplicationContext(),
                        dispatcherRule.dispatcher,
                        ExportDocumentStore(ApplicationProvider.getApplicationContext(), dispatcherRule.dispatcher),
                    ),
                    AppEventBus(),
                    LibraryTestPlaybackHistory(),
                    privateVideoSession = PrivateVideoSession(),
                    defaultDispatcher = dispatcherRule.dispatcher,
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

    @Test
    fun `private vault video passes private hint to detail key and protects loading frame`() =
        runTest(dispatcherRule.dispatcher) {
            val privateVideo = libraryTestVideo("/private.mp4").copy(id = "secret", isPrivate = true)
            val navigation = SpyNavigationDispatcher()
            val session = PrivateVideoSession().apply { unlock() }
            val viewModel =
                LibraryViewModel(
                    LibraryTestDownloads(listOf(privateVideo)),
                    LibraryTestSettings,
                    navigation,
                    SavedStateHandle(),
                    LibraryFileManager(
                        ApplicationProvider.getApplicationContext(),
                        dispatcherRule.dispatcher,
                        ExportDocumentStore(ApplicationProvider.getApplicationContext(), dispatcherRule.dispatcher),
                    ),
                    AppEventBus(),
                    LibraryTestPlaybackHistory(),
                    privateVideoSession = session,
                    defaultDispatcher = dispatcherRule.dispatcher,
                )
            viewModel.setPrivateVault(true)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            viewModel.openVideo(privateVideo.id)
            runCurrent()

            assertEquals(
                listOf(NavigationEvent.Navigate(VideoDetailKey(privateVideo.id, isPrivate = true))),
                navigation.dispatched,
            )
            assertEquals(true, shouldProtectVideoWindow(isPrivateHint = true, task = null))
            assertEquals(false, shouldProtectVideoWindow(isPrivateHint = false, task = null))
            assertEquals(true, shouldProtectVideoWindow(isPrivateHint = false, task = privateVideo))
            assertEquals(
                false,
                shouldProtectVideoWindow(isPrivateHint = true, task = privateVideo.copy(isPrivate = false)),
            )
        }
}
