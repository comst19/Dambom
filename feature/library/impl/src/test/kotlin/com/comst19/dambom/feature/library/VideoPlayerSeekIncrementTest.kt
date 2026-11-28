package com.comst19.dambom.feature.library

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.feature.library.player.Media3VideoPlaybackController
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
        val harness = testVideoPlayerHarness(ApplicationProvider.getApplicationContext<Context>())

        try {
            assertEquals(
                listOf(10_000L, 10_000L),
                listOf(harness.player.seekBackIncrement, harness.player.seekForwardIncrement),
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun `clearing detail view model store releases the shared player exactly once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val realPlayer = ExoPlayer.Builder(context).build()
        var releaseCount = 0
        val trackingPlayer =
            object : Player by realPlayer {
                override fun release() {
                    releaseCount++
                    realPlayer.release()
                }
            }
        val harness =
            testVideoPlayerHarness(
                controller = Media3VideoPlaybackController(trackingPlayer),
            )

        harness.close()

        assertEquals(1, releaseCount)
    }
}
