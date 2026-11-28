package com.comst19.dambom.feature.library

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.PlaybackHistory
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPlayerPreparationTest {
    @Test
    fun `locking private playback clears media and prevents playback from restarting`() {
        val session = PrivateVideoSession()
        val harness =
            testVideoPlayerHarness(
                ApplicationProvider.getApplicationContext<Context>(),
                session = session,
            )
        val task = savedVideo("private").copy(localFilePath = "/files/private.mp4", isPrivate = true)
        try {
            harness.viewModel.prepare(task)
            assertNull(harness.player.currentMediaItem)
            session.unlock()
            harness.viewModel.prepare(task)
            assertEquals("private", harness.player.currentMediaItem?.mediaId)
            session.lock()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertNull(harness.player.currentMediaItem)
            harness.viewModel.onUiStarted()
            harness.viewModel.requestPlayPause()
            harness.viewModel.prepare(task)
            assertNull(harness.player.currentMediaItem)
            assertFalse(harness.player.playWhenReady)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `stop ignores another video and clears the matching video`() {
        val harness = testVideoPlayerHarness(ApplicationProvider.getApplicationContext<Context>())
        try {
            harness.viewModel.prepare(savedVideo("video").copy(localFilePath = "/files/video.mp4"))
            harness.player.seekTo(5_000L)

            harness.viewModel.stop("other")

            assertEquals("video", harness.player.currentMediaItem?.mediaId)
            assertEquals(5_000L, harness.player.currentPosition)

            harness.viewModel.stop("video")
            harness.viewModel.onUiStarted()

            assertNull(harness.player.currentMediaItem)
            assertNull(harness.viewModel.resumePromptPosition.value)
            assertFalse(harness.player.playWhenReady)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `new detail waits for play and switching videos clears pending resume`() {
        val harness = testVideoPlayerHarness(ApplicationProvider.getApplicationContext<Context>())
        val first = savedVideo("first").copy(localFilePath = "/files/first.mp4")
        val second = savedVideo("second").copy(localFilePath = "/files/second.mp4")
        try {
            harness.viewModel.prepare(first)
            assertEquals("first", harness.player.currentMediaItem?.mediaId)
            assertFalse(harness.player.playWhenReady)
            harness.player.play()
            assertTrue(harness.player.playWhenReady)
            harness.player.seekTo(5_000L)
            harness.viewModel.prepare(first.copy(isFavorite = true))
            assertTrue(harness.player.playWhenReady)
            assertEquals(5_000L, harness.player.currentPosition)
            harness.viewModel.onUiStopped()
            harness.viewModel.prepare(second)
            harness.viewModel.onUiStarted()
            assertEquals("second", harness.player.currentMediaItem?.mediaId)
            assertFalse(harness.player.playWhenReady)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `prepare cancels pending load when path changes for same id while deduplicating same path`() {
        val historyGate = CompletableDeferred<Unit>()
        var historyCalls = 0
        val suspendingHistory =
            object : PlaybackHistoryRepository {
                override fun observeHistory(): Flow<Map<String, PlaybackHistory>> = MutableStateFlow(emptyMap())

                override suspend fun getHistory(downloadId: String): PlaybackHistory? {
                    historyCalls++
                    historyGate.await()
                    return null
                }

                override suspend fun saveProgress(
                    downloadId: String,
                    positionMillis: Long,
                    durationMillis: Long,
                    lastPlayedAtMillis: Long,
                ) = Unit
            }
        val session = PrivateVideoSession().apply { unlock() }
        val harness =
            testVideoPlayerHarness(
                ApplicationProvider.getApplicationContext<Context>(),
                history = suspendingHistory,
                session = session,
            )
        val oldPath = "/files/videos/one.mp4"
        val newPath = "/files/private_videos/one.mp4"
        val task = savedVideo("one").copy(localFilePath = oldPath, isPrivate = true)
        try {
            harness.viewModel.prepare(task)
            harness.viewModel.prepare(task.copy(isFavorite = true))
            assertEquals(1, historyCalls)

            harness.viewModel.prepare(task.copy(localFilePath = newPath))
            assertEquals(2, historyCalls)

            historyGate.complete(Unit)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertEquals("one", harness.player.currentMediaItem?.mediaId)
            val currentUri =
                harness.player.currentMediaItem
                    ?.localConfiguration
                    ?.uri
            assertEquals(Uri.fromFile(File(newPath)), currentUri)
        } finally {
            historyGate.complete(Unit)
            harness.close()
        }
    }

    @Test
    fun `prepare reloads when shared player replaces media item uri for same id`() {
        val harness = testVideoPlayerHarness(ApplicationProvider.getApplicationContext<Context>())
        val originalPath = "/files/videos/original.mp4"
        val replacedPath = "/files/videos/replaced.mp4"
        val task = savedVideo("same-id").copy(localFilePath = originalPath)
        try {
            harness.viewModel.prepare(task)
            assertEquals(
                Uri.fromFile(File(originalPath)),
                harness.player.currentMediaItem
                    ?.localConfiguration
                    ?.uri,
            )

            harness.player.setMediaItem(
                MediaItem
                    .Builder()
                    .setMediaId("same-id")
                    .setUri(Uri.fromFile(File(replacedPath)))
                    .build(),
            )
            harness.viewModel.prepare(task)
            assertEquals(
                Uri.fromFile(File(originalPath)),
                harness.player.currentMediaItem
                    ?.localConfiguration
                    ?.uri,
            )

            harness.player.setMediaItem(
                MediaItem
                    .Builder()
                    .setMediaId("same-id")
                    .setUri(Uri.parse("content://example.provider$originalPath"))
                    .build(),
            )
            harness.viewModel.prepare(task)
            assertEquals(
                Uri.fromFile(File(originalPath)),
                harness.player.currentMediaItem
                    ?.localConfiguration
                    ?.uri,
            )
        } finally {
            harness.close()
        }
    }
}
