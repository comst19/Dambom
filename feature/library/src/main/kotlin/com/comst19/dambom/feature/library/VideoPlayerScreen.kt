package com.comst19.dambom.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.common.ui.currentAdaptiveLayoutInfo
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.component.FullscreenVideoPlayer
import com.comst19.dambom.feature.library.component.LibraryFileActions
import com.comst19.dambom.feature.library.component.MissingVideo
import com.comst19.dambom.feature.library.component.VideoActionsButton
import com.comst19.dambom.feature.library.component.VideoPlayerPanel
import com.comst19.dambom.feature.library.component.rememberLibraryFileActions
import com.comst19.dambom.feature.library.file.isLocalVideoAvailable
import com.comst19.dambom.feature.library.file.rememberLocalVideoAvailable
import com.comst19.dambom.feature.library.pip.PipPlatformEffect

@Composable
internal fun VideoPlayerRoute(
    id: String,
    isVideoFullscreen: Boolean,
    onVideoFullscreenChange: (Boolean) -> Unit,
    onVideoRotate: () -> Unit,
) {
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val playerViewModel: VideoPlayerViewModel = hiltViewModel()
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val task =
        uiState.selectedVideo?.takeIf { it.id == id }
            ?: uiState.videos.firstOrNull { it.id == id }
    var isLocalVideoAvailable by rememberLocalVideoAvailable(task)

    fun refreshLocalVideoAvailability() {
        isLocalVideoAvailable = isLocalVideoAvailable(task)
    }
    val multiplePanes = currentAdaptiveLayoutInfo().supportsMultiplePanes
    val fileActions =
        rememberLibraryFileActions(
            viewModel = libraryViewModel,
            onDelete = { video ->
                onVideoFullscreenChange(false)
                playerViewModel.stop(video.id)
                libraryViewModel.delete(video, closeDetail = true)
            },
        )

    DisposableEffect(Unit) {
        onDispose { onVideoFullscreenChange(false) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        refreshLocalVideoAvailability()
        playerViewModel.onUiStarted()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshLocalVideoAvailability()
        playerViewModel.onUiResumed()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { playerViewModel.onUiPaused() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playerViewModel.onUiStopped() }
    VideoPlayerMediaSessionEffect(playerViewModel.player)
    LaunchedEffect(task?.id, task?.localFilePath, isLocalVideoAvailable) {
        if (isLocalVideoAvailable) {
            task?.let(playerViewModel::play)
        } else {
            playerViewModel.stopUnavailableVideo()
        }
    }
    LaunchedEffect(isVideoFullscreen, task, isLocalVideoAvailable) {
        refreshLocalVideoAvailability()
        if (shouldClearVideoFullscreen(isVideoFullscreen, isLocalVideoAvailable)) {
            onVideoFullscreenChange(false)
        }
    }

    val playableTask = task?.takeIf { isLocalVideoAvailable }

    PipPlatformEffect(
        player = playerViewModel.player,
        task = playableTask,
        isFullscreen = isVideoFullscreen,
        onPictureInPictureModeChanged = { inPictureInPictureMode ->
            if (inPictureInPictureMode) {
                playerViewModel.onPictureInPictureEntered()
            } else {
                playerViewModel.onPictureInPictureExited()
            }
        },
    ) { pipContentOnly, onVideoBoundsChanged ->
        VideoPlayerScreen(
            task = playableTask,
            player = playerViewModel.player,
            fileActions = fileActions,
            onBack = libraryViewModel::goBack,
            showBack = !multiplePanes,
            isVideoFullscreen = isVideoFullscreen,
            onVideoFullscreenChange = onVideoFullscreenChange,
            onVideoRotate = onVideoRotate,
            isPipContentOnly = pipContentOnly,
            onVideoBoundsChanged = onVideoBoundsChanged,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VideoPlayerScreen(
    task: DownloadTask?,
    player: Player,
    fileActions: LibraryFileActions,
    onBack: () -> Unit,
    showBack: Boolean,
    isVideoFullscreen: Boolean,
    onVideoFullscreenChange: (Boolean) -> Unit,
    onVideoRotate: () -> Unit,
    isPipContentOnly: Boolean = false,
    onVideoBoundsChanged: (androidx.compose.ui.unit.IntRect?) -> Unit = {},
) {
    BackHandler(enabled = isVideoFullscreen) { onVideoFullscreenChange(false) }
    val showRotationControl = shouldShowFullscreenRotationControl(LocalConfiguration.current.smallestScreenWidthDp)

    if ((isVideoFullscreen || isPipContentOnly) && task != null) {
        FullscreenVideoPlayer(
            task = task,
            player = player,
            fileActions = fileActions,
            onDismiss = { onVideoFullscreenChange(false) },
            onRotate = onVideoRotate,
            showRotationControl = showRotationControl,
            isPipContentOnly = isPipContentOnly,
            onVideoBoundsChanged = onVideoBoundsChanged,
        )
    } else {
        AppScreen(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = task?.title ?: stringResource(R.string.player_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
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
                            IconButton(onClick = { onVideoFullscreenChange(true) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Fullscreen,
                                    contentDescription = stringResource(R.string.player_fullscreen),
                                )
                            }
                            VideoActionsButton(
                                task = it,
                                actions = fileActions,
                                modifier = Modifier.padding(end = 8.dp),
                            )
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
                    onOpenOriginal = { fileActions.onOpenOriginal(task) },
                    onCopyLink = { fileActions.onCopyLink(task) },
                    onShareLink = { fileActions.onShareLink(task) },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                )
            }
        }
    }
}

internal fun shouldClearVideoFullscreen(
    isVideoFullscreen: Boolean,
    hasVideo: Boolean,
): Boolean = isVideoFullscreen && !hasVideo

internal fun shouldShowFullscreenRotationControl(smallestScreenWidthDp: Int): Boolean =
    smallestScreenWidthDp < ROTATION_MIN_SMALLEST_WIDTH_DP

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

private const val ROTATION_MIN_SMALLEST_WIDTH_DP = 600
