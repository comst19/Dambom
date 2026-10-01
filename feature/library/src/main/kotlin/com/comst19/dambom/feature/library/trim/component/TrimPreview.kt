package com.comst19.dambom.feature.library.trim.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import com.comst19.dambom.core.common.ui.player.DambomSeekBar
import com.comst19.dambom.core.common.ui.player.KeepScreenOnWhilePlaying
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.toTimeText
import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import com.comst19.dambom.feature.library.trim.media.clippedMediaItem
import kotlinx.coroutines.delay

@Composable
@UnstableApi
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun TrimPreview(
    path: String,
    selection: TrimSelection,
    enabled: Boolean,
    onPositionChanged: (Long) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val player =
        remember(context) {
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
            }
        }
    var isPlaying by remember { mutableStateOf(false) }
    KeepScreenOnWhilePlaying(player, enabled)
    var position by remember { mutableLongStateOf(0L) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }
            }
        player.addListener(listener)
        onDispose { player.release() }
    }
    LaunchedEffect(path, selection) {
        position = 0L
        failed = false
        player.pause()
        player.setMediaItem(clippedMediaItem(path, selection))
        player.prepare()
    }
    LaunchedEffect(enabled) { if (!enabled) player.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    LaunchedEffect(player, isPlaying) {
        do {
            position = player.currentPosition.coerceAtLeast(0L)
            if (isPlaying) delay(POSITION_UPDATE_MILLIS)
        } while (isPlaying)
    }
    LaunchedEffect(position, selection) { onPositionChanged(selection.startMillis + position) }
    val duration = selection.endMillis - selection.startMillis
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((maxWidth / PREVIEW_ASPECT_RATIO).coerceAtMost(240.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            ) {
                ContentFrame(player = player, modifier = Modifier.matchParentSize())
                IconButton(
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                            player.play()
                        }
                    },
                    enabled = enabled && !failed,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (isPlaying) R.string.trim_pause else R.string.trim_preview),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        if (failed) Text(stringResource(R.string.player_error), color = MaterialTheme.colorScheme.error)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${position.toTimeText()} / ${duration.toTimeText()}", style = MaterialTheme.typography.labelMedium)
            DambomSeekBar(
                value = if (duration > 0L) position.toFloat() / duration else 0f,
                enabled = enabled && !failed && duration > 0L,
                contentDescription = stringResource(R.string.trim_seek),
                stateDescription = position.toTimeText(),
                onValueChange = { value ->
                    position = (duration * value).toLong()
                    player.seekTo(position)
                },
                onInteractionChanged = { interacting -> if (interacting) player.pause() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val PREVIEW_ASPECT_RATIO = 16f / 9f

private const val POSITION_UPDATE_MILLIS = 100L
