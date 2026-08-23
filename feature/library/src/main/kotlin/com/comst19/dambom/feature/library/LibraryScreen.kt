package com.comst19.dambom.feature.library

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.appScaffoldPadding
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask

@Composable
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

internal fun Long.formatBytes(): String {
    val megabytes = this / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) "%.1f MB".format(megabytes) else "%.0f KB".format(this / 1024.0)
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
private const val VIDEO_ASPECT_RATIO = 16f / 9f
