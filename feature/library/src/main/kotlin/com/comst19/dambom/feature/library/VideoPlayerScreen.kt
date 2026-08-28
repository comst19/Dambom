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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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

    LifecycleEventEffect(Lifecycle.Event.ON_START) { playerViewModel.onUiStarted() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { playerViewModel.onUiResumed() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { playerViewModel.onUiPaused() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playerViewModel.onUiStopped() }
    VideoPlayerMediaSessionEffect(playerViewModel.player)
    LaunchedEffect(task?.id) {
        task?.let(playerViewModel::play)
    }
    LaunchedEffect(isVideoFullscreen, task) {
        if (shouldClearVideoFullscreen(isVideoFullscreen, task != null)) {
            onVideoFullscreenChange(false)
        }
    }

    PipPlatformEffect(
        player = playerViewModel.player,
        task = task,
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
            task = task,
            player = playerViewModel.player,
            fileActions = fileActions,
            onBack = libraryViewModel::goBack,
            showBack = !multiplePanes,
            deferFullscreenControlsOnEntry = multiplePanes,
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
    deferFullscreenControlsOnEntry: Boolean = false,
    isVideoFullscreen: Boolean,
    onVideoFullscreenChange: (Boolean) -> Unit,
    onVideoRotate: () -> Unit,
    isPipContentOnly: Boolean = false,
    onVideoBoundsChanged: (androidx.compose.ui.unit.IntRect?) -> Unit = {},
) {
    BackHandler(enabled = isVideoFullscreen) { onVideoFullscreenChange(false) }
    val showRotationControl = shouldShowFullscreenRotationControl(LocalConfiguration.current.smallestScreenWidthDp)

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

    if (isVideoFullscreen && task != null) {
        FullscreenVideoPlayer(
            task = task,
            player = player,
            fileActions = fileActions,
            onDismiss = { onVideoFullscreenChange(false) },
            onRotate = onVideoRotate,
            showRotationControl = showRotationControl,
            deferFullscreenControlsOnEntry = deferFullscreenControlsOnEntry,
            isPipContentOnly = isPipContentOnly,
            onVideoBoundsChanged = onVideoBoundsChanged,
        )
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
