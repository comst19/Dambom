package com.comst19.dambom.feature.library

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.PlaybackHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackResumePromptTest {
    @Test
    fun `resume asks on play and continues only after confirmation`() {
        withSavedVideo { viewModel ->
            assertNull(viewModel.resumePromptPosition.value)
            assertFalse(viewModel.player.playWhenReady)
            viewModel.requestPlayPause()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(5_000L, viewModel.resumePromptPosition.value)
            assertFalse(viewModel.player.playWhenReady)
            viewModel.answerResume(true)
            assertNull(viewModel.resumePromptPosition.value)
            assertEquals(5_000L, viewModel.player.currentPosition)
            assertTrue(viewModel.player.playWhenReady)
        }
    }

    @Test
    fun `start over starts at zero and dismiss waits to ask on next play`() {
        withSavedVideo { viewModel ->
            viewModel.player.play()
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.dismissResumePrompt()
            assertNull(viewModel.resumePromptPosition.value)
            assertFalse(viewModel.player.playWhenReady)
            viewModel.player.play()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(5_000L, viewModel.resumePromptPosition.value)
            viewModel.answerResume(false)
            assertEquals(0L, viewModel.player.currentPosition)
            assertTrue(viewModel.player.playWhenReady)
        }
    }

    @Test
    fun `switching videos clears old prompt and repeat mode`() {
        withSavedVideo { viewModel ->
            viewModel.player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            viewModel.player.play()
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.prepare(savedVideo("other").copy(localFilePath = "/files/other.mp4"))
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.answerResume(true)
            assertNull(viewModel.resumePromptPosition.value)
            assertFalse(viewModel.player.playWhenReady)
            assertEquals("other", viewModel.player.currentMediaItem?.mediaId)
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, viewModel.player.repeatMode)
        }
    }

    private fun withSavedVideo(block: (VideoPlayerViewModel) -> Unit) {
        val history =
            LibraryTestPlaybackHistory().apply {
                this.history.value = mapOf("video" to PlaybackHistory("video", 5_000L, 20_000L, 1L))
            }
        val viewModel = testVideoPlayerViewModel(ApplicationProvider.getApplicationContext<Context>(), history)
        try {
            viewModel.prepare(savedVideo("video").copy(localFilePath = "/files/video.mp4"))
            shadowOf(Looper.getMainLooper()).idle()
            block(viewModel)
        } finally {
            viewModel.closeForTest()
        }
    }
}
