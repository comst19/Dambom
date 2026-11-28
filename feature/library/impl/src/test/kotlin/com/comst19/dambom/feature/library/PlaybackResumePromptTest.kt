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
        withSavedVideo { harness ->
            assertNull(harness.viewModel.resumePromptPosition.value)
            assertFalse(harness.player.playWhenReady)
            harness.viewModel.requestPlayPause()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(5_000L, harness.viewModel.resumePromptPosition.value)
            assertFalse(harness.player.playWhenReady)
            harness.viewModel.answerResume(true)
            assertNull(harness.viewModel.resumePromptPosition.value)
            assertEquals(5_000L, harness.player.currentPosition)
            assertTrue(harness.player.playWhenReady)
        }
    }

    @Test
    fun `start over starts at zero and dismiss waits to ask on next play`() {
        withSavedVideo { harness ->
            harness.player.play()
            shadowOf(Looper.getMainLooper()).idle()
            harness.viewModel.dismissResumePrompt()
            assertNull(harness.viewModel.resumePromptPosition.value)
            assertFalse(harness.player.playWhenReady)
            harness.player.play()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(5_000L, harness.viewModel.resumePromptPosition.value)
            harness.viewModel.answerResume(false)
            assertEquals(0L, harness.player.currentPosition)
            assertTrue(harness.player.playWhenReady)
        }
    }

    @Test
    fun `switching videos clears old prompt and repeat mode`() {
        withSavedVideo { harness ->
            harness.player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            harness.player.play()
            shadowOf(Looper.getMainLooper()).idle()
            harness.viewModel.prepare(savedVideo("other").copy(localFilePath = "/files/other.mp4"))
            shadowOf(Looper.getMainLooper()).idle()
            harness.viewModel.answerResume(true)
            assertNull(harness.viewModel.resumePromptPosition.value)
            assertFalse(harness.player.playWhenReady)
            assertEquals("other", harness.player.currentMediaItem?.mediaId)
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, harness.player.repeatMode)
        }
    }

    private fun withSavedVideo(block: (TestVideoPlayerViewModel) -> Unit) {
        val history =
            LibraryTestPlaybackHistory().apply {
                this.history.value = mapOf("video" to PlaybackHistory("video", 5_000L, 20_000L, 1L))
            }
        val harness = testVideoPlayerHarness(ApplicationProvider.getApplicationContext<Context>(), history)
        try {
            harness.viewModel.prepare(savedVideo("video").copy(localFilePath = "/files/video.mp4"))
            shadowOf(Looper.getMainLooper()).idle()
            block(harness)
        } finally {
            harness.close()
        }
    }
}
