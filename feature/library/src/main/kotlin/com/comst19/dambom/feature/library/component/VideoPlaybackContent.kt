package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.media.rememberLocalVideoMetadata

@Composable
internal fun FullscreenVideoPlayer(
    task: DownloadTask,
    player: Player,
    fileActions: LibraryFileActions,
    onDismiss: () -> Unit,
) {
    val playPauseState = rememberPlayPauseButtonState(player)
    val progressState = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MILLIS)
    val surfaceDescription = stringResource(R.string.player_surface_description, task.title)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        FullscreenSystemBars()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
            contentColor = Color.White,
        ) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.player_exit_fullscreen),
                        )
                    }
                    Text(
                        text = task.title,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 1,
                    )
                    VideoActionsButton(task = task, actions = fileActions)
                }
                ContentFrame(
                    player = player,
                    shutter = {},
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black)
                            .semantics { contentDescription = surfaceDescription },
                )
                Surface(
                    color = Color.Black,
                    contentColor = Color.White,
                ) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        PlayerControls(
                            player = player,
                            showPlay = playPauseState.showPlay,
                            playEnabled = playPauseState.isEnabled,
                            onPlayPause = playPauseState::onClick,
                            positionMillis = progressState.currentPositionMs,
                            durationMillis = progressState.durationMs,
                            contentColor = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenSystemBars() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window

    DisposableEffect(view, window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

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
internal fun VideoPlayerPanel(
    task: DownloadTask,
    player: Player,
    modifier: Modifier = Modifier,
) {
    val playPauseState = rememberPlayPauseButtonState(player)
    val progressState = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MILLIS)
    val metadata by rememberLocalVideoMetadata(task.localFilePath, task.updatedAtMillis)
    var playbackError by remember(player) { mutableStateOf<PlaybackException?>(player.playerError) }
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
        modifier = modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ContentFrame(
            player = player,
            shutter = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(VIDEO_ASPECT_RATIO)
                    .background(Color.Black)
                    .semantics { contentDescription = surfaceDescription },
        )
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
        PlayerControls(
            player = player,
            showPlay = playPauseState.showPlay,
            playEnabled = playPauseState.isEnabled,
            onPlayPause = playPauseState::onClick,
            positionMillis = progressState.currentPositionMs,
            durationMillis = progressState.durationMs,
        )
        VideoDetails(task = task, metadata = metadata)
    }
}

private const val VIDEO_ASPECT_RATIO = 16f / 9f
