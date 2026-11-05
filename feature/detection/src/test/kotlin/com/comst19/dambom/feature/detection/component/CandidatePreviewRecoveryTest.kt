package com.comst19.dambom.feature.detection.component

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.feature.detection.R
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

    @Test fun `replacing preview media gives the new controls their own auto hide timer`() {
        val candidate =
            mutableStateOf(
                MediaCandidate("first", "https://example.com/first.mp4", "First", "video/mp4", null),
            )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CandidatePreviewDialog(candidate.value, onDismiss = {}, playerFactory = { ReadyPlayer() })
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pause").assertExists()
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle {
            candidate.value = candidate.value.copy(id = "second", url = "https://example.com/second.mp4")
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithContentDescription("Pause").assertExists()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pause").assertDoesNotExist()
    }

    private class ReadyPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var state =
            State
                .Builder()
                .setAvailableCommands(
                    Player.Commands
                        .Builder()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .build(),
                ).setPlaylist(listOf(MediaItemData.Builder("video").setDurationUs(100_000_000).build()))
                .setPlaybackState(Player.STATE_READY)
                .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()

        override fun getState(): State = state

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            state =
                state
                    .buildUpon()
                    .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    .build()
            return Futures.immediateVoidFuture()
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
