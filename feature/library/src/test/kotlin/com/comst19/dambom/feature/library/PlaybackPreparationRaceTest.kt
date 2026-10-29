package com.comst19.dambom.feature.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.model.PlaybackHistory
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackPreparationRaceTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun `both stop paths cancel pending history loading and queued play`() =
        runTest {
            for (unavailable in listOf(false, true)) {
                val loaded = CompletableDeferred<PlaybackHistory?>()
                val repository =
                    object : PlaybackHistoryRepository by LibraryTestPlaybackHistory() {
                        override suspend fun getHistory(downloadId: String) = loaded.await()
                    }
                val model =
                    VideoPlayerViewModel(
                        ApplicationProvider.getApplicationContext<Context>(),
                        repository,
                        LibraryTestSettings,
                        backgroundScope,
                        AppEventBus(),
                    )
                try {
                    model.prepare(savedVideo("video").copy(localFilePath = "/files/video.mp4"))
                    runCurrent()
                    model.requestPlayPause()
                    if (unavailable) model.stopUnavailableVideo() else model.stop("video")
                    loaded.complete(PlaybackHistory("video", 5_000L, 20_000L, 1L))
                    runCurrent()
                    model.onUiStarted()

                    assertNull(model.player.currentMediaItem)
                    assertNull(model.resumePromptPosition.value)
                    assertFalse(model.player.playWhenReady)
                } finally {
                    model.closeForTest()
                }
            }
        }

    @Test
    fun `play during history loading waits and asks before playback`() =
        runTest {
            val loaded = CompletableDeferred<PlaybackHistory?>()
            val repository =
                object : PlaybackHistoryRepository by LibraryTestPlaybackHistory() {
                    override suspend fun getHistory(downloadId: String) = loaded.await()
                }
            val model =
                VideoPlayerViewModel(
                    ApplicationProvider.getApplicationContext<Context>(),
                    repository,
                    LibraryTestSettings,
                    backgroundScope,
                    AppEventBus(),
                )
            try {
                model.prepare(savedVideo("video").copy(localFilePath = "/files/video.mp4"))
                runCurrent()
                model.requestPlayPause()
                assertFalse(model.player.playWhenReady)
                model.player.play()
                assertFalse(model.player.playWhenReady)
                loaded.complete(PlaybackHistory("video", 5_000L, 20_000L, 1L))
                runCurrent()
                assertEquals(5_000L, model.resumePromptPosition.value)
                assertFalse(model.player.playWhenReady)
                assertEquals(0L, model.player.currentPosition)
            } finally {
                model.closeForTest()
            }
        }
}
