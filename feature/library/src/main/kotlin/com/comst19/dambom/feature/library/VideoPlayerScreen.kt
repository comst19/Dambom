package com.comst19.dambom.feature.library

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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

@Composable
internal fun VideoPlayerRoute(id: String) {
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
