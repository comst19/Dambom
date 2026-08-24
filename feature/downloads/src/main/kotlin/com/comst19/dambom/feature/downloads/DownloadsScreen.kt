package com.comst19.dambom.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.domain.model.NetworkConnection
import com.comst19.dambom.feature.downloads.contract.DownloadsUiState

@Composable
internal fun DownloadsRoute(
    networkAccess: NetworkAccessState,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsScreen(
        uiState = uiState,
        canDownload = networkAccess.canDownload,
        onBack = viewModel::goBack,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        onPauseAll = viewModel::pauseAll,
        onResumeAll = viewModel::resumeAll,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DownloadsScreen(
    uiState: DownloadsUiState,
    canDownload: Boolean,
    onBack: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
) {
    AppScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.downloads_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (uiState.tasks.isEmpty()) {
                EmptyDownloads()
            } else {
                DownloadList(
                    uiState = uiState,
                    canDownload = canDownload,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onPauseAll = onPauseAll,
                    onResumeAll = onResumeAll,
                )
            }
        }
    }
}

@Composable
private fun EmptyDownloads() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.downloads_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.downloads_empty_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadList(
    uiState: DownloadsUiState,
    canDownload: Boolean,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
) {
    val groups =
        listOf(
            DownloadStatus.DOWNLOADING,
            DownloadStatus.QUEUED,
            DownloadStatus.PAUSED,
            DownloadStatus.FAILED,
            DownloadStatus.COMPLETED,
        )
    LazyColumn(
        modifier =
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DownloadSummary(
                state = uiState,
                canDownload = canDownload,
                onPauseAll = onPauseAll,
                onResumeAll = onResumeAll,
            )
            Spacer(Modifier.height(12.dp))
        }
        groups.forEach { status ->
            val tasks = uiState.tasks.filter { it.status == status }
            if (tasks.isNotEmpty()) {
                item { Text(status.groupTitle(), style = MaterialTheme.typography.titleMedium) }
                items(tasks, key = DownloadTask::id) { task ->
                    DownloadRow(
                        task = task,
                        canDownload = canDownload,
                        onPause = { onPause(task.id) },
                        onResume = { onResume(task.id) },
                        onCancel = { onCancel(task.id) },
                        onRetry = { onRetry(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadSummary(
    state: DownloadsUiState,
    canDownload: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = DambomShapes.Summary,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.downloads_summary, state.activeCount, state.totalCount),
                style = MaterialTheme.typography.headlineSmall,
            )
            LinearProgressIndicator(
                progress = { state.progress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canPauseAll) {
                    OutlinedButton(onClick = onPauseAll) { Text(stringResource(R.string.downloads_pause_all)) }
                }
                if (state.canResumeAll) {
                    Button(onClick = onResumeAll, enabled = canDownload) {
                        Text(stringResource(R.string.downloads_resume_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                        TextButton(onClick = onPause) { Text(stringResource(R.string.downloads_pause)) }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.downloads_cancel)) }
                    }

                    DownloadStatus.PAUSED -> {
                        TextButton(onClick = onResume, enabled = canDownload) {
                            Text(stringResource(R.string.downloads_resume))
                        }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.downloads_cancel)) }
                    }

                    DownloadStatus.FAILED -> {
                        TextButton(onClick = onRetry, enabled = canDownload) {
                            Text(stringResource(R.string.downloads_retry))
                        }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.downloads_remove)) }
                    }

                    DownloadStatus.COMPLETED -> {}
                }
            }
        }
    }
}

@Composable
private fun DownloadStatus.groupTitle(): String =
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
private fun DownloadStatus.statusText(): String = groupTitle()

@Composable
private fun DownloadStatus.statusColor() =
    when (this) {
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun DownloadFailureReason.failureText(): String =
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

private fun Long.formatBytes(): String {
    val megabytes = this / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) "%.1f MB".format(megabytes) else "%.0f KB".format(this / 1024.0)
}

@FormFactorPreviews
@Composable
private fun DownloadsScreenPreview() {
    DambomTheme {
        DownloadsScreen(
            uiState =
                DownloadsUiState(
                    tasks =
                        listOf(
                            previewTask("1", DownloadStatus.DOWNLOADING, 0.62f),
                            previewTask("2", DownloadStatus.QUEUED, 0f),
                            previewTask("3", DownloadStatus.PAUSED, 0.2f),
                        ),
                ),
            canDownload = NetworkAccessState(NetworkConnection.UNMETERED).canDownload,
            onBack = {},
            onPause = {},
            onResume = {},
            onCancel = {},
            onRetry = {},
            onPauseAll = {},
            onResumeAll = {},
        )
    }
}

private fun previewTask(
    id: String,
    status: DownloadStatus,
    progress: Float,
) = DownloadTask(
    id = id,
    url = "https://example.com/$id.mp4",
    sourcePageUrl = "https://example.com",
    title = "여행 영상 $id",
    mimeType = "video/mp4",
    expectedBytes = 100_000_000L,
    downloadedBytes = (100_000_000L * progress).toLong(),
    quality = "원본",
    status = status,
    failureReason = null,
    localFileName = null,
    createdAtMillis = 1L,
    updatedAtMillis = 1L,
)
