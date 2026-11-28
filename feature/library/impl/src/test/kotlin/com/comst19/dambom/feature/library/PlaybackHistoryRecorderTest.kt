package com.comst19.dambom.feature.library

import com.comst19.dambom.feature.library.player.PlaybackHistoryRecorder
import com.comst19.dambom.feature.library.player.PlaybackProgressSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackHistoryRecorderTest {
    @Test
    fun `opening or seeking before actual playback creates no history`() =
        runTest {
            val repository = LibraryTestPlaybackHistory()
            val recorder = PlaybackHistoryRecorder(repository, backgroundScope) {}

            recorder.save(PlaybackProgressSnapshot("video", 100L, 10_000L))
            recorder.save(PlaybackProgressSnapshot("video", 5_000L, 10_000L))
            runCurrent()

            assertTrue(repository.history.value.isEmpty())
        }

    @Test
    fun `progress preserves playback timestamp and reset blocks a different unplayed item`() =
        runTest {
            val repository = LibraryTestPlaybackHistory()
            val recorder = PlaybackHistoryRecorder(repository, backgroundScope) {}
            recorder.onPlaying(PlaybackProgressSnapshot("video", 100L, 10_000L))
            runCurrent()
            val started = repository.history.value.getValue("video")
            recorder.save(PlaybackProgressSnapshot("video", 5_000L, 10_000L))
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
            recorder.save(PlaybackProgressSnapshot("other", 5_000L, 10_000L))
            runCurrent()
            assertEquals(setOf("video"), repository.history.value.keys)
        }

    @Test
    fun `unknown duration does not persist until a duration is available`() =
        runTest {
            val repository = LibraryTestPlaybackHistory()
            val recorder = PlaybackHistoryRecorder(repository, backgroundScope) {}
            recorder.onPlaying(PlaybackProgressSnapshot("video", 100L, Long.MIN_VALUE + 1L))
            runCurrent()
            assertTrue(repository.history.value.isEmpty())

            recorder.save(PlaybackProgressSnapshot("video", 100L, 10_000L))
            runCurrent()
            assertEquals(
                10_000L,
                repository.history.value
                    .getValue("video")
                    .durationMillis,
            )
        }
}
