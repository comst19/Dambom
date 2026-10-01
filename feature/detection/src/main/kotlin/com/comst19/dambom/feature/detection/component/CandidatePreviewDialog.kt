package com.comst19.dambom.feature.detection.component

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.comst19.dambom.core.common.ui.player.DambomPlayerControls
import com.comst19.dambom.core.common.ui.player.KeepScreenOnWhilePlaying
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.feature.detection.R
import kotlinx.coroutines.delay

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun CandidatePreviewDialog(
    candidate: MediaCandidate,
    onDismiss: () -> Unit,
    playerFactory: (Context) -> Player = ::createCandidatePreviewPlayer,
) {
    val context = LocalContext.current
    val player =
        remember(candidate.url) {
            playerFactory(context).apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(candidate.url))
                prepare()
                playWhenReady = true
            }
        }
    val state = rememberPreviewState(player)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    LaunchedEffect(state.controlsVisible, state.isPlaying, state.controlsInteracting, state.interactionRevision) {
        if (state.controlsVisible && state.isPlaying && !state.controlsInteracting) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            state.controlsVisible = false
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        KeepScreenOnWhilePlaying(player)
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                CandidatePreviewHeader(candidate.title, onDismiss)
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CandidatePreviewSurface(player, state)
                }
            }
        }
    }
}

private class PreviewPlaybackState {
    var isPrepared by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var controlsVisible by mutableStateOf(true)
    var controlsInteracting by mutableStateOf(false)
    var interactionRevision by mutableStateOf(0)
}

@Composable
private fun rememberPreviewState(player: Player): PreviewPlaybackState {
    val state = remember(player) { PreviewPlaybackState() }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) state.isPrepared = true
                }

                override fun onIsPlayingChanged(value: Boolean) {
                    state.isPlaying = value
                }

                override fun onPlayerError(error: PlaybackException) {
                    state.failed = true
                }
            }
        player.addListener(listener)
        state.isPrepared = player.playbackState == Player.STATE_READY
        state.isPlaying = player.isPlaying
        state.failed = player.playerError != null
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    return state
}

@Composable
private fun CandidatePreviewHeader(
    title: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.detection_play_selected_quality),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.detection_close_preview))
        }
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private fun CandidatePreviewSurface(
    player: Player,
    state: PreviewPlaybackState,
) {
    val toggleControlsLabel = stringResource(R.string.detection_toggle_playback_controls)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .semantics {
                    onClick(label = toggleControlsLabel) {
                        state.controlsVisible = !state.controlsVisible
                        true
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { state.controlsVisible = !state.controlsVisible }
                },
        contentAlignment = Alignment.Center,
    ) {
        ContentFrame(
            player = player,
            shutter = {},
            modifier = Modifier.fillMaxSize().background(Color.Black).alpha(if (state.isPrepared) 1f else 0f),
        )
        if (state.failed) {
            CandidatePreviewError {
                state.failed = false
                state.isPrepared = false
                state.controlsVisible = true
                player.prepare()
                player.play()
            }
        } else if (!state.isPrepared) {
            CircularProgressIndicator(color = Color.White)
        }
        CandidatePreviewControlOverlay(
            player = player,
            visible = !state.failed && state.isPrepared && state.controlsVisible,
            modifier = Modifier.matchParentSize(),
            onInteraction = {
                state.controlsVisible = true
                state.interactionRevision++
            },
            onInteractionChanged = { state.controlsInteracting = it },
        )
    }
}

@Composable
private fun CandidatePreviewError(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.detection_preview_error))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.detection_preview_retry))
        }
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private fun CandidatePreviewControlOverlay(
    player: Player,
    visible: Boolean,
    onInteraction: () -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPauseState = rememberPlayPauseButtonState(player)
    val progressState = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MILLIS)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(CONTROLS_FADE_MILLIS)),
        exit = fadeOut(tween(CONTROLS_FADE_MILLIS)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = CONTROLS_SCRIM_ALPHA))) {
            DambomPlayerControls(
                player = player,
                showPlay = playPauseState.showPlay,
                playEnabled = playPauseState.isEnabled,
                onPlayPause = playPauseState::onClick,
                positionMillis = progressState.currentPositionMs,
                durationMillis = progressState.durationMs,
                contentColor = Color.White,
                onInteraction = onInteraction,
                onInteractionChanged = onInteractionChanged,
                modifier = Modifier.fillMaxSize(),
                timelineModifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private const val CONTROLS_AUTO_HIDE_MILLIS = 3_000L
private const val CONTROLS_FADE_MILLIS = 180
private const val CONTROLS_SCRIM_ALPHA = 0.48f
private const val PROGRESS_TICK_MILLIS = 500L
