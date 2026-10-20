package com.comst19.dambom.core.common.ui.player

import android.text.format.DateUtils
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.comst19.dambom.core.common.ui.R
import kotlin.math.roundToLong

@Composable
@Suppress("LongParameterList", "LongMethod")
fun DambomPlayerControls(
    player: Player,
    showPlay: Boolean,
    playEnabled: Boolean,
    onPlayPause: () -> Unit,
    positionMillis: () -> Long,
    durationMillis: () -> Long,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onInteraction: () -> Unit = {},
    onInteractionChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    timelineModifier: Modifier = Modifier,
) {
    val rewindInteractionSource = remember { MutableInteractionSource() }
    val playPauseInteractionSource = remember { MutableInteractionSource() }
    val forwardInteractionSource = remember { MutableInteractionSource() }
    val isRewindPressed by rewindInteractionSource.collectIsPressedAsState()
    val isPlayPausePressed by playPauseInteractionSource.collectIsPressedAsState()
    val isForwardPressed by forwardInteractionSource.collectIsPressedAsState()
    var isTimelineInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(isTimelineInteracting, isRewindPressed, isPlayPausePressed, isForwardPressed) {
        val isInteracting = isTimelineInteracting || isRewindPressed || isPlayPausePressed || isForwardPressed
        onInteractionChanged(isInteracting)
        if (isInteracting) onInteraction()
    }

    Box(modifier) {
        if (!isTimelineInteracting) {
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
                modifier = Modifier.align(Alignment.Center),
            )
        }
        PlaybackTimeline(
            player = player,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            isTimelineInteracting = isTimelineInteracting,
            onTimelineInteractionChanged = { isTimelineInteracting = it },
            onInteraction = onInteraction,
            contentColor = contentColor,
            modifier = Modifier.align(Alignment.BottomCenter).then(timelineModifier),
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun PlaybackTimeline(
    player: Player,
    positionMillis: () -> Long,
    durationMillis: () -> Long,
    isTimelineInteracting: Boolean,
    onTimelineInteractionChanged: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    contentColor: Color,
    modifier: Modifier,
) {
    val currentPosition = positionMillis()
    val safeDuration = durationMillis().coerceAtLeast(0L)
    val progress =
        if (safeDuration > 0L) {
            (currentPosition.toFloat() / safeDuration).coerceIn(0f, 1f)
        } else {
            0f
        }
    val durationText = safeDuration.toTimeText()
    val seekDescription = stringResource(R.string.playback_seek_description)
    var scrubPosition by remember { mutableStateOf(0L) }
    val positionText = (if (isTimelineInteracting) scrubPosition else currentPosition).toTimeText()
    Column(modifier = modifier) {
        DambomSeekBar(
            value =
                if (isTimelineInteracting && safeDuration > 0) {
                    scrubPosition.toFloat() / safeDuration
                } else {
                    progress
                },
            enabled = isDambomSeekBarEnabled(safeDuration),
            contentDescription = seekDescription,
            stateDescription = "$positionText / $durationText",
            onValueChange = { value ->
                val targetPosition = (value * safeDuration).roundToLong()
                if (!isTimelineInteracting || targetPosition != scrubPosition) player.seekTo(targetPosition)
                scrubPosition = targetPosition
            },
            onInteractionChanged = { interacting ->
                if (interacting) scrubPosition = currentPosition
                onTimelineInteractionChanged(interacting)
                if (interacting) onInteraction()
            },
        )
        Text(
            text = "$positionText / $durationText",
            modifier = Modifier.fillMaxWidth(),
            color = contentColor,
            style = MaterialTheme.typography.bodySmall,
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                contentDescription = stringResource(R.string.playback_rewind),
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
                contentDescription = stringResource(if (showPlay) R.string.playback_play else R.string.playback_pause),
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
                contentDescription = stringResource(R.string.playback_forward),
                tint = contentColor,
            )
        }
    }
}

private fun Long.toTimeText(): String = DateUtils.formatElapsedTime(coerceAtLeast(0L) / DateUtils.SECOND_IN_MILLIS)

private val PLAYER_CONTROL_SIZE = 48.dp
private val PLAYER_PRIMARY_CONTROL_SIZE = 64.dp
