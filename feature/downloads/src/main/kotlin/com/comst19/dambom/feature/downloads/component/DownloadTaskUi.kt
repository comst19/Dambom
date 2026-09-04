package com.comst19.dambom.feature.downloads.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomShapes
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
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED -> {
                DownloadActionButton(R.string.downloads_cancel, onCancel, secondary = true)
                DownloadActionButton(R.string.downloads_pause, onPause)
            }

            task.status == DownloadStatus.PAUSED -> {
                DownloadActionButton(R.string.downloads_cancel, onCancel, secondary = true)
                DownloadActionButton(R.string.downloads_resume, onResume, enabled = canDownload)
            }

            task.status == DownloadStatus.FAILED -> {
                DownloadActionButton(R.string.downloads_remove, onCancel, secondary = true)
                DownloadActionButton(R.string.downloads_retry, onRetry, enabled = canDownload)
            }

            else -> {}
        }
    }
}

@Composable
private fun DownloadActionButton(
    labelRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    secondary: Boolean = false,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 48.dp),
        shape = DambomShapes.Control,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor =
                    if (secondary) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                contentColor =
                    if (secondary) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
            ),
    ) {
        Text(stringResource(labelRes))
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
