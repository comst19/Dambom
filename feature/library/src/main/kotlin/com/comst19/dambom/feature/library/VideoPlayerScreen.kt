package com.comst19.dambom.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.window.core.layout.WindowSizeClass
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.ORIGINAL_QUALITY
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToLong

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun VideoPlayerRoute(id: String) {
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val playerViewModel: VideoPlayerViewModel = hiltViewModel()
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val task =
        uiState.selectedVideo?.takeIf { it.id == id }
            ?: uiState.videos.firstOrNull { it.id == id }
    val multiplePanes = currentWindowAdaptiveInfoV2().windowSizeClass.supportsMultiplePanes
    val fileActions =
        rememberLibraryFileActions(
            viewModel = libraryViewModel,
            onDelete = { video ->
                playerViewModel.stop(video.id)
                libraryViewModel.delete(video, closeDetail = true)
            },
        )

    LifecycleResumeEffect(playerViewModel) {
        playerViewModel.onUiResumed()
        onPauseOrDispose { playerViewModel.onUiPaused() }
    }
    LaunchedEffect(task?.id) {
        task?.let(playerViewModel::play)
    }

    VideoPlayerScreen(
        task = task,
        player = playerViewModel.player,
        fileActions = fileActions,
        onBack = libraryViewModel::goBack,
        showBack = !multiplePanes,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VideoPlayerScreen(
    task: DownloadTask?,
    player: Player,
    fileActions: LibraryFileActions,
    onBack: () -> Unit,
    showBack: Boolean,
) {
    var fullscreen by remember { mutableStateOf(false) }

    AppScreen(
        topBar = {
            TopAppBar(
                title = { Text(task?.title ?: stringResource(R.string.player_title)) },
                navigationIcon =
                    if (showBack) {
                        {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.player_back),
                                )
                            }
                        }
                    } else {
                        {}
                    },
                actions = {
                    task?.let {
                        IconButton(onClick = { fullscreen = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Fullscreen,
                                contentDescription = stringResource(R.string.player_fullscreen),
                            )
                        }
                        VideoActionsButton(task = it, actions = fileActions)
                    }
                },
            )
        },
    ) { innerPadding ->
        if (task == null) {
            MissingVideo(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding))
        } else {
            VideoPlayerPanel(
                task = task,
                player = player,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
            )
        }
    }

    if (fullscreen && task != null) {
        FullscreenVideoPlayer(
            task = task,
            player = player,
            fileActions = fileActions,
            onDismiss = { fullscreen = false },
        )
    }
}

@Composable
private fun FullscreenVideoPlayer(
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
private fun MissingVideo(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.player_missing_file),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun VideoPlayerPanel(
    task: DownloadTask,
    player: Player,
    modifier: Modifier = Modifier,
) {
    val playPauseState = rememberPlayPauseButtonState(player)
    val progressState = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MILLIS)
    val metadata by rememberLocalVideoMetadata(task.localFilePath)
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

@Composable
private fun VideoDetails(
    task: DownloadTask,
    metadata: LocalVideoMetadata?,
) {
    val configuration = LocalConfiguration.current
    val downloadedAt =
        remember(task.updatedAtMillis, configuration) {
            DateFormat
                .getDateInstance(DateFormat.MEDIUM, configuration.locales[0])
                .format(Date(task.updatedAtMillis))
        }
    val unknown = stringResource(R.string.player_info_unknown)
    val resolution =
        metadata?.let { details ->
            if (details.width != null && details.height != null) "${details.width} × ${details.height}" else null
        } ?: unknown
    val duration = metadata?.durationMillis?.toTimeText() ?: unknown
    val quality =
        if (task.quality == ORIGINAL_QUALITY) {
            stringResource(R.string.player_quality_original)
        } else {
            task.quality
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.player_info_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            VideoMetadataItem(stringResource(R.string.player_info_duration), duration)
            VideoMetadataItem(stringResource(R.string.player_info_resolution), resolution)
            VideoMetadataItem(stringResource(R.string.player_info_quality), quality)
            VideoMetadataItem(stringResource(R.string.player_info_size), task.downloadedBytes.formatBytes())
            VideoMetadataItem(stringResource(R.string.player_info_downloaded_at), downloadedAt)
            SelectionContainer {
                VideoMetadataItem(stringResource(R.string.player_info_source), task.sourcePageUrl)
            }
        }
    }
}

@Composable
private fun VideoMetadataItem(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlayerControls(
    player: Player,
    showPlay: Boolean,
    playEnabled: Boolean,
    onPlayPause: () -> Unit,
    positionMillis: Long,
    durationMillis: Long,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                )
            },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = player::seekBack,
            enabled = player.isCommandAvailable(Player.COMMAND_SEEK_BACK),
            modifier = Modifier.size(PLAYER_CONTROL_SIZE),
        ) {
            Icon(
                imageVector = Icons.Outlined.Replay10,
                contentDescription = stringResource(R.string.player_rewind),
            )
        }
        Spacer(Modifier.width(12.dp))
        IconButton(
            onClick = onPlayPause,
            enabled = playEnabled,
            modifier = Modifier.size(PLAYER_PRIMARY_CONTROL_SIZE),
        ) {
            Icon(
                imageVector = if (showPlay) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription =
                    stringResource(
                        if (showPlay) R.string.player_play else R.string.player_pause,
                    ),
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        IconButton(
            onClick = player::seekForward,
            enabled = player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD),
            modifier = Modifier.size(PLAYER_CONTROL_SIZE),
        ) {
            Icon(
                imageVector = Icons.Outlined.Forward10,
                contentDescription = stringResource(R.string.player_forward),
            )
        }
    }
    Text(
        text = "$positionText / $durationText",
        modifier = Modifier.fillMaxWidth(),
        color = contentColor,
        style = MaterialTheme.typography.bodySmall,
    )
}

private val WindowSizeClass.supportsMultiplePanes: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

internal fun Long.toTimeText(): String {
    val totalSeconds = coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private val PLAYER_CONTROL_SIZE = 48.dp
private val PLAYER_PRIMARY_CONTROL_SIZE = 64.dp
private const val VIDEO_ASPECT_RATIO = 16f / 9f
private const val PROGRESS_TICK_MILLIS = 500L
