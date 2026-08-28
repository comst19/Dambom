package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.toTimeText
import kotlin.math.roundToLong

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlayerControls(
    player: Player,
    showPlay: Boolean,
    playEnabled: Boolean,
    onPlayPause: () -> Unit,
    positionMillis: Long,
    durationMillis: Long,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onInteraction: () -> Unit = {},
    onInteractionChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    timelineModifier: Modifier = Modifier,
) {
    val safeDuration = durationMillis.coerceAtLeast(0L)
    val progress =
        if (safeDuration > 0L) {
            (positionMillis.toFloat() / safeDuration).coerceIn(0f, 1f)
        } else {
            0f
        }
    val positionText = positionMillis.toTimeText()
    val durationText = safeDuration.toTimeText()
    val seekDescription = stringResource(R.string.player_seek_description)
    val interactionSource = remember { MutableInteractionSource() }
    val rewindInteractionSource = remember { MutableInteractionSource() }
    val playPauseInteractionSource = remember { MutableInteractionSource() }
    val forwardInteractionSource = remember { MutableInteractionSource() }
    val isControlPressed by interactionSource.collectIsPressedAsState()
    val isControlDragged by interactionSource.collectIsDraggedAsState()
    val isRewindPressed by rewindInteractionSource.collectIsPressedAsState()
    val isPlayPausePressed by playPauseInteractionSource.collectIsPressedAsState()
    val isForwardPressed by forwardInteractionSource.collectIsPressedAsState()

    LaunchedEffect(isControlPressed, isControlDragged, isRewindPressed, isPlayPausePressed, isForwardPressed) {
        val isInteracting = isControlPressed || isControlDragged || isRewindPressed || isPlayPausePressed || isForwardPressed
        onInteractionChanged(isInteracting)
        if (isInteracting) onInteraction()
    }

    Box(modifier) {
        Box(Modifier.align(Alignment.Center)) {
            PlaybackButtons(
                player = player,
                showPlay = showPlay,
                playEnabled = playEnabled,
                onPlayPause = onPlayPause,
                onInteraction = onInteraction,
                contentColor = contentColor,
                rewindInteractionSource = rewindInteractionSource,
                playPauseInteractionSource = playPauseInteractionSource,
                forwardInteractionSource = forwardInteractionSource,
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(timelineModifier),
        ) {
            PlayerTimeline(
                player = player,
                safeDuration = safeDuration,
                progress = progress,
                positionText = positionText,
                durationText = durationText,
                seekDescription = seekDescription,
                interactionSource = interactionSource,
            )
            Text(
                text = "$positionText / $durationText",
                modifier = Modifier.fillMaxWidth(),
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlayerTimeline(
    player: Player,
    safeDuration: Long,
    progress: Float,
    positionText: String,
    durationText: String,
    seekDescription: String,
    interactionSource: MutableInteractionSource,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PLAYER_CONTROL_SIZE)
                .semantics(mergeDescendants = true) {
                    contentDescription = seekDescription
                    stateDescription = "$positionText / $durationText"
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    if (safeDuration > 0L) {
                        setProgress { targetProgress ->
                            player.seekTo((targetProgress.coerceIn(0f, 1f) * safeDuration).roundToLong())
                            true
                        }
                    } else {
                        disabled()
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = progress,
            onValueChange = { value ->
                if (safeDuration > 0L) player.seekTo((value * safeDuration).roundToLong())
            },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            enabled = safeDuration > 0L,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    enabled = safeDuration > 0L,
                    thumbSize = DpSize(16.dp, 16.dp),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    enabled = safeDuration > 0L,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp,
                )
            },
        )
    }
}

@Composable
private fun PlaybackButtons(
    player: Player,
    showPlay: Boolean,
    playEnabled: Boolean,
    onPlayPause: () -> Unit,
    onInteraction: () -> Unit,
    contentColor: Color,
    rewindInteractionSource: MutableInteractionSource,
    playPauseInteractionSource: MutableInteractionSource,
    forwardInteractionSource: MutableInteractionSource,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                onInteraction()
                player.seekBack()
            },
            enabled = player.isCommandAvailable(Player.COMMAND_SEEK_BACK),
            modifier = Modifier.size(PLAYER_CONTROL_SIZE),
            interactionSource = rewindInteractionSource,
        ) {
            Icon(
                imageVector = Icons.Outlined.Replay10,
                contentDescription = stringResource(R.string.player_rewind),
                tint = contentColor,
            )
        }
        Spacer(Modifier.width(12.dp))
        IconButton(
            onClick = {
                onInteraction()
                onPlayPause()
            },
            enabled = playEnabled,
            modifier = Modifier.size(PLAYER_PRIMARY_CONTROL_SIZE),
            interactionSource = playPauseInteractionSource,
        ) {
            Icon(
                imageVector = if (showPlay) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription =
                    stringResource(
                        if (showPlay) R.string.player_play else R.string.player_pause,
                    ),
                modifier = Modifier.size(36.dp),
                tint = contentColor,
            )
        }
        Spacer(Modifier.width(12.dp))
        IconButton(
            onClick = {
                onInteraction()
                player.seekForward()
            },
            enabled = player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD),
            modifier = Modifier.size(PLAYER_CONTROL_SIZE),
            interactionSource = forwardInteractionSource,
        ) {
            Icon(
                imageVector = Icons.Outlined.Forward10,
                contentDescription = stringResource(R.string.player_forward),
                tint = contentColor,
            )
        }
    }
}

internal const val PROGRESS_TICK_MILLIS = 500L
private val PLAYER_CONTROL_SIZE = 48.dp
private val PLAYER_PRIMARY_CONTROL_SIZE = 64.dp
