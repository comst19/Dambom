package com.comst19.dambom.feature.library.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.comst19.dambom.core.common.ui.player.DambomPlayerControls
import com.comst19.dambom.core.common.ui.player.KeepScreenOnWhilePlaying
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.media.rememberLocalVideoMetadata
import com.comst19.dambom.feature.library.player.PlayerInteractionState
import com.comst19.dambom.feature.library.player.component.PlaybackOptionsButton

@Composable
internal fun MissingVideo(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.player_missing_file),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Suppress("LongParameterList", "LongMethod")
internal fun VideoPlayerPanel(
    task: DownloadTask,
    player: Player,
    interaction: PlayerInteractionState,
    onOpenOriginal: () -> Unit,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayPauseRequest: (() -> Unit)? = null,
) {
    val playPauseState = rememberPlayPauseButtonState(player)
    val metadata by rememberLocalVideoMetadata(task.localFilePath)
    var playbackError by remember(player) { mutableStateOf<PlaybackException?>(player.playerError) }
    val controls = rememberPlayerControlsState(task.id, isPlaying = !playPauseState.showPlay)
    val surfaceDescription = stringResource(R.string.player_surface_description, task.title)

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackError = error
                }

                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    playbackError = error
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InlineVideoSurface(
            player = player,
            interaction = interaction,
            surfaceDescription = surfaceDescription,
            controlsVisible = controls.isVisible,
            showPlay = playPauseState.showPlay,
            playEnabled = playPauseState.isEnabled,
            onPlayPause = onPlayPauseRequest ?: playPauseState::onClick,
            onToggleControls = controls::toggle,
            onControlsInteraction = controls::onInteraction,
            onControlsInteractionChanged = controls::onInteractionChanged,
        )
        PlaybackOptionsButton(player)
        Text(
            text = task.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        playbackError?.let {
            Text(
                text = stringResource(R.string.player_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        VideoDetails(
            task = task,
            metadata = metadata,
            onOpenOriginal = onOpenOriginal,
            onCopyLink = onCopyLink,
            onShareLink = onShareLink,
        )
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Suppress("LongParameterList", "LongMethod")
private fun InlineVideoSurface(
    player: Player,
    interaction: PlayerInteractionState,
    surfaceDescription: String,
    controlsVisible: Boolean,
    showPlay: Boolean,
    playEnabled: Boolean,
    onPlayPause: () -> Unit,
    onToggleControls: () -> Unit,
    onControlsInteraction: () -> Unit,
    onControlsInteractionChanged: (Boolean) -> Unit,
) {
    val toggleControlsLabel = stringResource(R.string.player_toggle_controls)
    KeepScreenOnWhilePlaying(player)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(VIDEO_ASPECT_RATIO)
                .background(Color.Black)
                .clipToBounds(),
    ) {
        ContentFrame(
            player = player,
            shutter = {},
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = interaction.scale
                        scaleY = interaction.scale
                        translationX = interaction.panX
                        translationY = interaction.panY
                    }.semantics { contentDescription = surfaceDescription },
        )
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    onClick(label = toggleControlsLabel) {
                        onToggleControls()
                        true
                    }
                }.fullscreenPlayerGestures(toggleControlsLabel, onToggleControls, {}, interaction),
        )
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(INLINE_CONTROLS_FADE_MILLIS)),
            exit = fadeOut(tween(INLINE_CONTROLS_FADE_MILLIS)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = INLINE_CONTROLS_SCRIM_ALPHA))) {
                InlineVideoControls(
                    player = player,
                    showPlay = showPlay,
                    playEnabled = playEnabled,
                    onPlayPause = onPlayPause,
                    onInteraction = onControlsInteraction,
                    onInteractionChanged = onControlsInteractionChanged,
                )
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Suppress("LongParameterList")
private fun InlineVideoControls(
    player: Player,
    showPlay: Boolean,
    playEnabled: Boolean,
    onPlayPause: () -> Unit,
    onInteraction: () -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
) {
    val progressState = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MILLIS)
    DambomPlayerControls(
        player = player,
        showPlay = showPlay,
        playEnabled = playEnabled,
        onPlayPause = onPlayPause,
        positionMillis = { progressState.currentPositionMs },
        durationMillis = { progressState.durationMs },
        contentColor = Color.White,
        onInteraction = onInteraction,
        onInteractionChanged = onInteractionChanged,
        modifier = Modifier.fillMaxSize(),
        timelineModifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private const val VIDEO_ASPECT_RATIO = 16f / 9f
private const val INLINE_CONTROLS_FADE_MILLIS = 180
private const val INLINE_CONTROLS_SCRIM_ALPHA = 0.48f
internal const val PROGRESS_TICK_MILLIS = 500L
