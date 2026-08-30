package com.comst19.dambom.feature.downloads.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DownloadTaskHeader(task)
            DownloadTaskDetails(task)
            DownloadActions(task, canDownload, onPause, onResume, onCancel, onRetry)
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
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DownloadStatusIcon()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    DownloadTaskDetails(task, maxLines = 2)
                }
            }
            DownloadActions(task, canDownload, onPause, onResume, onCancel, onRetry)
        }
    }
}

@Composable
private fun DownloadTaskHeader(task: DownloadTask) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DownloadStatusIcon()
        Text(
            task.status.statusText(),
            color = task.status.statusColor(),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun DownloadStatusIcon() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = DambomShapes.Control,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Downloading,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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
    if (maxLines != Int.MAX_VALUE) {
        Text(
            task.status.statusText(),
            color = task.status.statusColor(),
            style = MaterialTheme.typography.labelLarge,
        )
    }
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
