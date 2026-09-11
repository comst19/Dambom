package com.comst19.dambom.feature.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.file.LibraryFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPlayerLifecycleTest {
    @get:Rule val composeRule = createComposeRule()

    private val videoFile = File.createTempFile("lifecycle-video", ".mp4")
    private lateinit var playerViewModel: VideoPlayerViewModel

    @After
    fun tearDown() {
        if (::playerViewModel.isInitialized) playerViewModel.player.release()
        File("${videoFile.path}.thumbnail.unavailable").delete()
        videoFile.delete()
    }

    @Test
    @Suppress("LongMethod")
    fun `returning while detail reloads preserves paused media position and fullscreen`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = LifecycleDownloadRepository()
        val libraryViewModel =
            LibraryViewModel(
                repository,
                LifecycleSettings,
                SpyNavigationDispatcher(),
                SavedStateHandle(),
                LibraryFileManager(context, Dispatchers.IO),
                AppEventBus(),
            )
        playerViewModel = VideoPlayerViewModel(context)
        val owner =
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this)
            }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val fullscreen = mutableStateOf(false)
        val video = savedVideo("video").copy(localFilePath = videoFile.path)
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MaterialTheme {
                    VideoPlayerRoute(
                        id = video.id,
                        isVideoFullscreen = fullscreen.value,
                        onVideoFullscreenChange = { fullscreen.value = it },
                        onVideoRotate = {},
                        libraryViewModel = libraryViewModel,
                        playerViewModel = playerViewModel,
                    )
                }
            }
        }
        composeRule.waitUntil { repository.updates.subscriptionCount.value == 1 }
        composeRule.runOnIdle { assertTrue(repository.updates.tryEmit(video)) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("video", playerViewModel.player.currentMediaItem?.mediaId)
            playerViewModel.player.pause()
            playerViewModel.player.seekTo(15_000L)
            fullscreen.value = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        composeRule.waitUntil { repository.updates.subscriptionCount.value == 0 }
        composeRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        composeRule.waitUntil { repository.updates.subscriptionCount.value == 1 }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(videoFile.isFile)
            assertEquals("video", playerViewModel.player.currentMediaItem?.mediaId)
            assertEquals(15_000L, playerViewModel.player.currentPosition)
            assertFalse(playerViewModel.player.playWhenReady)
            assertTrue(fullscreen.value)
            assertTrue(repository.updates.tryEmit(video))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(15_000L, playerViewModel.player.currentPosition)
            assertFalse(playerViewModel.player.playWhenReady)
            assertTrue(fullscreen.value)
            assertTrue(repository.updates.tryEmit(null))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0, playerViewModel.player.mediaItemCount)
            assertFalse(fullscreen.value)
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        }
    }
}

private class LifecycleDownloadRepository : DownloadRepository {
    val updates = MutableSharedFlow<DownloadTask?>(extraBufferCapacity = 1)
    override val downloads = flowOf(emptyList<DownloadTask>())

    override fun observeDownload(id: String) = updates

    override suspend fun enqueue(requests: List<DownloadRequest>) = EnqueueDownloadsResult(0, 0)

    override suspend fun pause(id: String) = Unit

    override suspend fun resume(id: String) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun rename(
        id: String,
        title: String,
    ) = Unit

    override suspend fun retry(id: String) = Unit

    override suspend fun pauseAll() = Unit

    override suspend fun resumeAll() = Unit

    override suspend fun recoverPendingDownloads() = Unit

    override suspend fun refreshNetworkPolicy() = Unit
}

private object LifecycleSettings : SettingsRepository {
    override val settings = flowOf(AppSettings())

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    ) = Unit

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit

    override suspend fun setDownloadLocation(
        enabled: Boolean,
        treeUri: String?,
    ) = Unit
}
