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
import androidx.compose.ui.test.junit4.createComposeRule
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

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class CandidatePreviewOwnershipRuntimeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replacingPreviewMediaRestartsTheControlsTimer() {
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

    @Test fun touchGesturesControlTheCurrentPreviewAfterMediaChanges() {
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
        val label = ApplicationProvider.getApplicationContext<Context>().getString(R.string.detection_toggle_playback_controls)
        val surface =
            compose.onNode(
                SemanticsMatcher("preview surface") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
                },
            )
        surface.performTouchInput { click(Offset(8f, 8f)) }
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pause").assertDoesNotExist()
        surface.performTouchInput { click(Offset(8f, 8f)) }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle {
            candidate.value = candidate.value.copy(id = "second", url = "https://example.com/second.mp4")
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pause").assertExists()
        surface.performTouchInput { click(Offset(8f, 8f)) }
        compose.mainClock.advanceTimeBy(350)
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
}
