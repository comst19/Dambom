package com.comst19.dambom.feature.detection.component

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class CandidatePreviewRecoveryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `first prepare error ends spinner and retry recovers ready controls`() {
        lateinit var player: RecoveringPlayer
        compose.setContent {
            CandidatePreviewDialog(
                candidate = MediaCandidate("id", "https://example.com/video.mp4", "Video", "video/mp4", null),
                onDismiss = {},
                playerFactory = { RecoveringPlayer().also { player = it } },
            )
        }
        compose.onNodeWithText("Couldn’t play this video.").assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.onNodeWithText("Retry playback").performClick()
        compose.onNodeWithText("Couldn’t play this video.").assertDoesNotExist()
        compose.onNodeWithText("Retry playback").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, player.prepareCount)
            assertEquals(Player.STATE_READY, player.playbackState)
        }
    }

    private class RecoveringPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        var prepareCount = 0
        private var state =
            State
                .Builder()
                .setAvailableCommands(
                    Player.Commands
                        .Builder()
                        .add(Player.COMMAND_PREPARE)
                        .build(),
                ).setPlaylist(listOf(MediaItemData.Builder("video").build()))
                .build()

        override fun getState(): State = state

        override fun handlePrepare(): ListenableFuture<*> {
            prepareCount++
            state =
                state
                    .buildUpon()
                    .setPlaybackState(if (prepareCount == 1) Player.STATE_IDLE else Player.STATE_READY)
                    .setPlayerError(
                        if (prepareCount ==
                            1
                        ) {
                            PlaybackException("403", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
                        } else {
                            null
                        },
                    ).build()
            return Futures.immediateVoidFuture()
        }
    }
}
