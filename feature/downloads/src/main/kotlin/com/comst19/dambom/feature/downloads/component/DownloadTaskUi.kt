package com.comst19.dambom.feature.downloads.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.downloads.R

internal fun DownloadTask.thumbnailSource(): String? = localFilePath

@Composable
internal fun DownloadActions(
    task: DownloadTask,
    canDownload: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED -> {
                TextButton(onClick = onPause) { Text(stringResource(R.string.downloads_pause)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.downloads_cancel)) }
            }

            task.status == DownloadStatus.PAUSED -> {
                TextButton(onClick = onResume, enabled = canDownload) {
                    Text(stringResource(R.string.downloads_resume))
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.downloads_cancel)) }
            }

            task.status == DownloadStatus.FAILED -> {
                TextButton(onClick = onRetry, enabled = canDownload) {
                    Text(stringResource(R.string.downloads_retry))
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.downloads_remove)) }
            }

            else -> {}
        }
    }
}

@Composable
internal fun DownloadStatus.groupTitle(): String =
    stringResource(
        when (this) {
            DownloadStatus.DOWNLOADING -> R.string.downloads_group_active
            DownloadStatus.QUEUED -> R.string.downloads_group_queued
            DownloadStatus.PAUSED -> R.string.downloads_group_paused
            DownloadStatus.FAILED -> R.string.downloads_group_failed
            DownloadStatus.COMPLETED -> R.string.downloads_group_completed
        },
    )

@Composable
internal fun DownloadStatus.statusText(): String = groupTitle()

@Composable
internal fun DownloadStatus.statusColor() =
    when (this) {
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
internal fun DownloadFailureReason.failureText(): String =
    stringResource(
        when (this) {
            DownloadFailureReason.ACCESS_RESTRICTED -> R.string.downloads_error_access
            DownloadFailureReason.UNSUPPORTED_FORMAT -> R.string.downloads_error_format
            DownloadFailureReason.NETWORK -> R.string.downloads_error_network
            DownloadFailureReason.STORAGE -> R.string.downloads_error_storage
            DownloadFailureReason.SERVER -> R.string.downloads_error_server
            DownloadFailureReason.UNKNOWN -> R.string.downloads_error_unknown
        },
    )
