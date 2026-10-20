package com.comst19.dambom.feature.library

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.comst19.dambom.feature.library.player.PlaybackHistoryRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackHistoryRecorderTest {
    @Test
    fun `opening or seeking before actual playback creates no history`() =
        runTest {
            val repository = LibraryTestPlaybackHistory()
            val recorder = PlaybackHistoryRecorder(repository, backgroundScope) {}
            val state = RecorderPlayer()

            recorder.save(state.player)
            state.position = 5_000L
            recorder.save(state.player)
            runCurrent()

            assertTrue(repository.history.value.isEmpty())
        }

    @Test
    fun `progress preserves playback timestamp and reset blocks a different unplayed item`() =
        runTest {
            val repository = LibraryTestPlaybackHistory()
            val recorder = PlaybackHistoryRecorder(repository, backgroundScope) {}
            val state = RecorderPlayer()
            recorder.onPlaying(state.player)
            runCurrent()
            val started = repository.history.value.getValue("video")
            state.position = 5_000L
            recorder.save(state.player)
            runCurrent()

            assertEquals(
                5_000L,
                repository.history.value
                    .getValue("video")
                    .positionMillis,
            )
            assertEquals(
                started.lastPlayedAtMillis,
                repository.history.value
                    .getValue("video")
                    .lastPlayedAtMillis,
            )
            recorder.reset()
            state.id = "other"
            recorder.save(state.player)
            runCurrent()
            assertEquals(setOf("video"), repository.history.value.keys)
        }

    @Test
    fun `unknown duration does not persist until a duration is available`() =
        runTest {
            val repository = LibraryTestPlaybackHistory()
            val recorder = PlaybackHistoryRecorder(repository, backgroundScope) {}
            val state = RecorderPlayer().apply { duration = C.TIME_UNSET }
            recorder.onPlaying(state.player)
            runCurrent()
            assertTrue(repository.history.value.isEmpty())

            state.duration = 10_000L
            recorder.save(state.player)
            runCurrent()
            assertEquals(
                10_000L,
                repository.history.value
                    .getValue("video")
                    .durationMillis,
            )
        }
}

private class RecorderPlayer {
    var id = "video"
    var duration = 10_000L
    var position = 100L
    val player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "RecorderPlayer"
                "getCurrentMediaItem" -> MediaItem.Builder().setMediaId(id).build()
                "getDuration" -> duration
                "getCurrentPosition" -> position
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player
}
