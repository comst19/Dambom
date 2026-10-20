package com.comst19.dambom.feature.library

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPlayerMediaSessionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `overlapping outgoing and incoming details can own sessions together`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outgoing = ExoPlayer.Builder(context).build()
        val incoming = ExoPlayer.Builder(context).build()
        val visible = mutableStateOf(true)
        try {
            composeRule.setContent {
                if (visible.value) {
                    VideoPlayerMediaSessionEffect(outgoing)
                    VideoPlayerMediaSessionEffect(incoming)
                    Text("both sessions attached")
                }
            }
            composeRule.onNodeWithText("both sessions attached").assertIsDisplayed()
        } finally {
            composeRule.runOnIdle { visible.value = false }
            composeRule.waitForIdle()
            outgoing.release()
            incoming.release()
        }
    }
}
