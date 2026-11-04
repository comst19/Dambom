package com.comst19.dambom.feature.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idle()
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
}
