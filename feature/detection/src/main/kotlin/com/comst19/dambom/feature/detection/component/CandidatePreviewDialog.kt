package com.comst19.dambom.feature.detection.component

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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.comst19.dambom.core.common.ui.player.DambomPlayerControls
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.feature.detection.R
import kotlinx.coroutines.delay

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun CandidatePreviewDialog(
    candidate: MediaCandidate,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val player =
        remember(candidate.url) {
            createCandidatePreviewPlayer(context).apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(candidate.url))
                prepare()
                playWhenReady = true
            }
        }
    var isPrepared by remember(candidate.id, candidate.url) { mutableStateOf(false) }
    var isPlaying by remember(candidate.id, candidate.url) { mutableStateOf(false) }
    var controlsVisible by remember(candidate.id, candidate.url) { mutableStateOf(true) }
    var controlsInteracting by remember(candidate.id, candidate.url) { mutableStateOf(false) }
    var controlsInteractionRevision by remember(candidate.id, candidate.url) { mutableStateOf(0) }
    val toggleControlsLabel = stringResource(R.string.detection_toggle_playback_controls)

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) isPrepared = true
                }

                override fun onIsPlayingChanged(value: Boolean) {
                    isPlaying = value
                }
            }
        player.addListener(listener)
        isPrepared = player.playbackState == Player.STATE_READY
        isPlaying = player.isPlaying
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(controlsVisible, isPlaying, controlsInteracting, controlsInteractionRevision) {
        if (controlsVisible && isPlaying && !controlsInteracting) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            controlsVisible = false
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
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
                            text = candidate.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.detection_close_preview),
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .semantics {
                                    onClick(label = toggleControlsLabel) {
                                        controlsVisible = !controlsVisible
                                        true
                                    }
                                }.pointerInput(Unit) {
                                    detectTapGestures { controlsVisible = !controlsVisible }
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        ContentFrame(
                            player = player,
                            shutter = {},
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .alpha(if (isPrepared) 1f else 0f),
                        )
                        if (!isPrepared) {
                            CircularProgressIndicator(color = Color.White)
                        }
                        CandidatePreviewControlOverlay(
                            player = player,
                            visible = isPrepared && controlsVisible,
                            modifier = Modifier.matchParentSize(),
                            onInteraction = {
                                controlsVisible = true
                                controlsInteractionRevision++
                            },
                            onInteractionChanged = { controlsInteracting = it },
                        )
                    }
                }
            }
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
