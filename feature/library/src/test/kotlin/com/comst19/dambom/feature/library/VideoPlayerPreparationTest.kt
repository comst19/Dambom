package com.comst19.dambom.feature.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPlayerPreparationTest {
    @Test
    fun `new detail waits for play and switching videos clears pending resume`() {
        val viewModel = VideoPlayerViewModel(ApplicationProvider.getApplicationContext<Context>())
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
            viewModel.player.release()
        }
    }
}
