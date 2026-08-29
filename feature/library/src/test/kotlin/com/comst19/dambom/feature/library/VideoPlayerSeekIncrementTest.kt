package com.comst19.dambom.feature.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPlayerSeekIncrementTest {
    @Test
    fun `player moves backward and forward by ten seconds`() {
        val viewModel = VideoPlayerViewModel(ApplicationProvider.getApplicationContext<Context>())

        try {
            assertEquals(
                listOf(10_000L, 10_000L),
                listOf(viewModel.player.seekBackIncrement, viewModel.player.seekForwardIncrement),
            )
        } finally {
            viewModel.player.release()
        }
    }
}
