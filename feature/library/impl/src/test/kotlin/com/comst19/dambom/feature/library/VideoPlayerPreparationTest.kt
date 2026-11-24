package com.comst19.dambom.feature.library

import android.content.Context
import android.net.Uri
import android.os.Looper
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
        val viewModel =
            testVideoPlayerViewModel(
                ApplicationProvider.getApplicationContext<Context>(),
                session = session,
            )
        val task = savedVideo("private").copy(localFilePath = "/files/private.mp4", isPrivate = true)
        try {
            viewModel.prepare(task)
            assertNull(viewModel.player.currentMediaItem)
            session.unlock()
            viewModel.prepare(task)
            assertEquals("private", viewModel.player.currentMediaItem?.mediaId)
            session.lock()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertNull(viewModel.player.currentMediaItem)
            viewModel.onUiStarted()
            viewModel.requestPlayPause()
            viewModel.prepare(task)
            assertNull(viewModel.player.currentMediaItem)
            assertFalse(viewModel.player.playWhenReady)
        } finally {
            viewModel.closeForTest()
        }
    }

    @Test
    fun `stop ignores another video and clears the matching video`() {
        val viewModel = testVideoPlayerViewModel(ApplicationProvider.getApplicationContext<Context>())
        try {
            viewModel.prepare(savedVideo("video").copy(localFilePath = "/files/video.mp4"))
            viewModel.player.seekTo(5_000L)

            viewModel.stop("other")

            assertEquals("video", viewModel.player.currentMediaItem?.mediaId)
            assertEquals(5_000L, viewModel.player.currentPosition)

            viewModel.stop("video")
            viewModel.onUiStarted()

            assertNull(viewModel.player.currentMediaItem)
            assertNull(viewModel.resumePromptPosition.value)
            assertFalse(viewModel.player.playWhenReady)
        } finally {
            viewModel.closeForTest()
        }
    }

    @Test
    fun `new detail waits for play and switching videos clears pending resume`() {
        val viewModel = testVideoPlayerViewModel(ApplicationProvider.getApplicationContext<Context>())
        val first = savedVideo("first").copy(localFilePath = "/files/first.mp4")
        val second = savedVideo("second").copy(localFilePath = "/files/second.mp4")
        try {
            viewModel.prepare(first)
            assertEquals("first", viewModel.player.currentMediaItem?.mediaId)
            assertFalse(viewModel.player.playWhenReady)
            viewModel.player.play()
            assertTrue(viewModel.player.playWhenReady)
            viewModel.player.seekTo(5_000L)
            viewModel.prepare(first.copy(isFavorite = true))
            assertTrue(viewModel.player.playWhenReady)
            assertEquals(5_000L, viewModel.player.currentPosition)
            viewModel.onUiStopped()
            viewModel.prepare(second)
            viewModel.onUiStarted()
            assertEquals("second", viewModel.player.currentMediaItem?.mediaId)
            assertFalse(viewModel.player.playWhenReady)
        } finally {
            viewModel.closeForTest()
        }
    }

    @Test
    fun `prepare cancels pending load when uri changes for same id while deduplicating same uri`() {
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
        val viewModel =
            testVideoPlayerViewModel(
                ApplicationProvider.getApplicationContext<Context>(),
                history = suspendingHistory,
                session = session,
            )
        val oldPath = "/files/videos/one.mp4"
        val newPath = "/files/private_videos/one.mp4"
        val task = savedVideo("one").copy(localFilePath = oldPath, isPrivate = true)
        try {
            viewModel.prepare(task)
            viewModel.prepare(task.copy(isFavorite = true))
            assertEquals(1, historyCalls)

            viewModel.prepare(task.copy(localFilePath = newPath))
            assertEquals(2, historyCalls)

            historyGate.complete(Unit)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertEquals("one", viewModel.player.currentMediaItem?.mediaId)
            val currentUri =
                viewModel.player.currentMediaItem
                    ?.localConfiguration
                    ?.uri
            assertEquals(Uri.fromFile(File(newPath)), currentUri)
        } finally {
            historyGate.complete(Unit)
            viewModel.closeForTest()
        }
    }
}
