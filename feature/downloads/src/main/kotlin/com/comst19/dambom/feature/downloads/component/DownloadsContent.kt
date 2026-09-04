package com.comst19.dambom.feature.downloads.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.downloads.R
import com.comst19.dambom.feature.downloads.contract.DownloadsUiState
import com.comst19.dambom.feature.downloads.contract.DownloadsViewMode
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems

@Immutable
internal data class DownloadsActions(
    val navigation: DownloadsNavigationActions,
    val task: DownloadTaskActions,
    val queue: DownloadQueueActions,
)

@Immutable
internal data class DownloadsNavigationActions(
    val onBack: () -> Unit,
    val onOpenLibrary: () -> Unit,
    val onViewModeChange: (DownloadsViewMode) -> Unit,
)

@Immutable
internal data class DownloadTaskActions(
    val onPause: (String) -> Unit,
    val onResume: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onRetry: (String) -> Unit,
)

@Immutable
internal data class DownloadQueueActions(
    val onPauseAll: () -> Unit,
    val onResumeAll: () -> Unit,
)

@Composable
internal fun DownloadsContent(
    uiState: DownloadsUiState,
    canDownload: Boolean,
    actions: DownloadsActions,
) {
    if (uiState.tasks.isEmpty()) {
        EmptyDownloads(actions.navigation.onOpenLibrary)
    } else if (uiState.viewMode == DownloadsViewMode.GRID) {
        DownloadGrid(
            uiState = uiState,
            canDownload = canDownload,
            onPause = actions.task.onPause,
            onResume = actions.task.onResume,
            onCancel = actions.task.onCancel,
            onRetry = actions.task.onRetry,
            onPauseAll = actions.queue.onPauseAll,
            onResumeAll = actions.queue.onResumeAll,
        )
    } else {
        DownloadList(
            uiState = uiState,
            canDownload = canDownload,
            onPause = actions.task.onPause,
            onResume = actions.task.onResume,
            onCancel = actions.task.onCancel,
            onRetry = actions.task.onRetry,
            onPauseAll = actions.queue.onPauseAll,
            onResumeAll = actions.queue.onResumeAll,
        )
    }
}

@Composable
private fun EmptyDownloads(onOpenLibrary: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Outlined.Downloading,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.downloads_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.downloads_empty_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onOpenLibrary, shape = DambomShapes.Control) {
            Text(stringResource(R.string.downloads_open_library))
        }
    }
}

@Composable
private fun DownloadGrid(
    uiState: DownloadsUiState,
    canDownload: Boolean,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MIN_DOWNLOAD_CARD_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            DownloadSummary(uiState, canDownload, onPauseAll, onResumeAll)
            Spacer(Modifier.height(12.dp))
        }
        DOWNLOAD_GROUPS.forEach { status ->
            val tasks = uiState.tasks.filter { it.status == status }
            if (tasks.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DownloadGroupTitle(status, tasks.size)
                }
                gridItems(
                    items = tasks,
                    key = DownloadTask::id,
                    contentType = { DOWNLOAD_ITEM_CONTENT_TYPE },
                ) { task ->
                    DownloadGridCard(
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DownloadSummary(uiState, canDownload, onPauseAll, onResumeAll)
            Spacer(Modifier.height(12.dp))
        }
        DOWNLOAD_GROUPS.forEach { status ->
            val tasks = uiState.tasks.filter { it.status == status }
            if (tasks.isNotEmpty()) {
                item { DownloadGroupTitle(status, tasks.size) }
                listItems(
                    items = tasks,
                    key = DownloadTask::id,
                    contentType = { DOWNLOAD_ITEM_CONTENT_TYPE },
                ) { task ->
                    DownloadListCard(
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = DambomShapes.Summary,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Downloading,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        state.summaryTitle(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.downloads_summary_remaining, state.totalCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (state.activeCount > 0) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f) },
                )
            }
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun DownloadsUiState.summaryTitle(): String =
    when {
        activeCount > 0 -> stringResource(R.string.downloads_summary, activeCount)
        tasks.any { it.status == DownloadStatus.QUEUED } -> stringResource(R.string.downloads_summary_waiting)
        tasks.any { it.status == DownloadStatus.PAUSED } -> stringResource(R.string.downloads_summary_paused)
        else -> stringResource(R.string.downloads_summary_attention)
    }

@Composable
private fun DownloadGroupTitle(
    status: DownloadStatus,
    count: Int,
) {
    Text(
        text = stringResource(R.string.downloads_group_count, status.groupTitle(), count),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private val MIN_DOWNLOAD_CARD_WIDTH = 340.dp
private val DOWNLOAD_GROUPS =
    listOf(
        DownloadStatus.DOWNLOADING,
        DownloadStatus.QUEUED,
        DownloadStatus.PAUSED,
        DownloadStatus.FAILED,
    )
private const val DOWNLOAD_ITEM_CONTENT_TYPE = "download"
