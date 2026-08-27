package com.comst19.dambom.feature.downloads.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.VideoThumbnail
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.downloads.R

@Composable
internal fun DownloadGridCard(
    task: DownloadTask,
    canDownload: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = DambomShapes.Card,
    ) {
        Column {
            DownloadThumbnail(task, Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT_RATIO), 44.dp)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DownloadTaskDetails(task)
                if (task.status != DownloadStatus.COMPLETED) {
                    DownloadActions(task, canDownload, onPause, onResume, onCancel, onRetry)
                }
            }
        }
    }
}

@Composable
internal fun DownloadListCard(
    task: DownloadTask,
    canDownload: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = DambomShapes.Card,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DownloadThumbnail(task, Modifier.width(LIST_THUMBNAIL_WIDTH).aspectRatio(VIDEO_ASPECT_RATIO), 36.dp)
                Column(
                    modifier = Modifier.weight(1f).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DownloadTaskDetails(task, maxLines = 2)
                }
            }
            if (task.status != DownloadStatus.COMPLETED) {
                DownloadActions(
                    task,
                    canDownload,
                    onPause,
                    onResume,
                    onCancel,
                    onRetry,
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadThumbnail(
    task: DownloadTask,
    modifier: Modifier,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        task.thumbnailSource()?.let { source ->
            VideoThumbnail(
                data = source,
                contentDescription = stringResource(R.string.downloads_thumbnail, task.title),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DownloadTaskDetails(
    task: DownloadTask,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        task.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        task.status.statusText(),
        color = task.status.statusColor(),
        style = MaterialTheme.typography.labelLarge,
    )
    Text(
        stringResource(
            R.string.downloads_bytes,
            task.downloadedBytes.formatBytes(),
            task.expectedBytes?.formatBytes() ?: stringResource(R.string.downloads_unknown_size),
            task.quality,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        maxLines = maxLines,
    )
    if (task.status == DownloadStatus.DOWNLOADING) {
        LinearProgressIndicator(
            progress = { task.progress },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(task.progress, 0f..1f) },
        )
    }
    task.failureReason?.let {
        Text(
            it.failureText(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val LIST_THUMBNAIL_WIDTH = 144.dp
private const val VIDEO_ASPECT_RATIO = 16f / 9f
