package com.comst19.dambom.feature.library

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import com.comst19.dambom.core.common.ui.appScaffoldPadding
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlin.math.roundToLong

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun LibraryRoute(viewModel: LibraryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = checkNotNull(LocalActivity.current) as ComponentActivity
    val playerViewModel: VideoPlayerViewModel = hiltViewModel(activity)

    LibraryScreen(
        uiState = uiState,
        onVideoClick = { task ->
            playerViewModel.play(task)
            viewModel.openVideo(task.id)
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun VideoPlayerRoute(
    id: String,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val activity = checkNotNull(LocalActivity.current) as ComponentActivity
    val playerViewModel: VideoPlayerViewModel = hiltViewModel(activity)
    val task = uiState.videos.firstOrNull { it.id == id }
    val multiplePanes = currentWindowAdaptiveInfoV2().windowSizeClass.supportsMultiplePanes

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
        onBack = libraryViewModel::goBack,
        showBack = !multiplePanes,
    )
}

@Composable
internal fun LibraryScreen(
    uiState: LibraryUiState,
    onVideoClick: (DownloadTask) -> Unit,
) {
    LibraryPane(
        uiState = uiState,
        onVideoClick = onVideoClick,
        modifier = Modifier.fillMaxSize().appScaffoldPadding(),
    )
}

@Composable
private fun LibraryPane(
    uiState: LibraryUiState,
    onVideoClick: (DownloadTask) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.library_title),
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (uiState.videos.isEmpty()) {
            EmptyLibrary(Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(MIN_VIDEO_CARD_WIDTH),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(uiState.videos, key = DownloadTask::id) { task ->
                    VideoCard(
                        task = task,
                        selected = task.id == uiState.selectedVideo?.id,
                        onClick = { onVideoClick(task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.library_empty_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun VideoCard(
    task: DownloadTask,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(VIDEO_ASPECT_RATIO)
                    .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = task.downloadedBytes.formatBytes(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun LibraryDetailPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.library_select_video),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VideoPlayerScreen(
    task: DownloadTask?,
    player: Player,
    onBack: () -> Unit,
    showBack: Boolean,
) {
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
        Text(
            text = stringResource(R.string.player_file_size, task.downloadedBytes.formatBytes()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

private val WindowSizeClass.supportsMultiplePanes: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

private fun Long.formatBytes(): String {
    val megabytes = this / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) "%.1f MB".format(megabytes) else "%.0f KB".format(this / 1024.0)
}

private fun Long.toTimeText(): String {
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

@FormFactorPreviews
@Composable
private fun LibraryScreenPreview() {
    DambomTheme {
        LibraryScreen(
            uiState = LibraryUiState(videos = listOf(previewTask("여행 영상"), previewTask("인터뷰"))),
            onVideoClick = {},
        )
    }
}

private fun previewTask(title: String) =
    DownloadTask(
        id = title,
        url = "https://example.com/video.mp4",
        sourcePageUrl = "https://example.com",
        title = title,
        mimeType = "video/mp4",
        expectedBytes = 4_000_000L,
        downloadedBytes = 4_000_000L,
        quality = "원본",
        status = DownloadStatus.COMPLETED,
        failureReason = null,
        localFileName = "video.mp4",
        localFilePath = "/data/user/0/example/files/videos/video.mp4",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

private val MIN_VIDEO_CARD_WIDTH = 156.dp
private val PLAYER_CONTROL_SIZE = 48.dp
private val PLAYER_PRIMARY_CONTROL_SIZE = 64.dp
private const val VIDEO_ASPECT_RATIO = 16f / 9f
private const val PROGRESS_TICK_MILLIS = 500L
