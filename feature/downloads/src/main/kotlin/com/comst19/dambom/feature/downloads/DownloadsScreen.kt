package com.comst19.dambom.feature.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.designsystem.previewNoOp
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.domain.model.NetworkConnection
import com.comst19.dambom.feature.downloads.component.DownloadsContent
import com.comst19.dambom.feature.downloads.contract.DownloadsUiState
import com.comst19.dambom.feature.downloads.contract.DownloadsViewMode
import kotlinx.collections.immutable.persistentListOf

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
        onViewModeChange = viewModel::setViewMode,
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
    onViewModeChange: (DownloadsViewMode) -> Unit,
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
                actions = {
                    IconButton(
                        onClick = {
                            onViewModeChange(
                                if (uiState.viewMode == DownloadsViewMode.GRID) {
                                    DownloadsViewMode.LIST
                                } else {
                                    DownloadsViewMode.GRID
                                },
                            )
                        },
                    ) {
                        Icon(
                            imageVector =
                                if (uiState.viewMode == DownloadsViewMode.GRID) {
                                    Icons.AutoMirrored.Outlined.ViewList
                                } else {
                                    Icons.Outlined.GridView
                                },
                            contentDescription =
                                stringResource(
                                    if (uiState.viewMode == DownloadsViewMode.GRID) {
                                        R.string.downloads_view_as_list
                                    } else {
                                        R.string.downloads_view_as_grid
                                    },
                                ),
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
            DownloadsContent(
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

@FormFactorPreviews
@Composable
private fun DownloadsScreenPreview() {
    DambomTheme {
        DownloadsScreen(
            uiState =
                DownloadsUiState(
                    tasks =
                        persistentListOf(
                            previewTask("1", DownloadStatus.DOWNLOADING, 0.62f),
                            previewTask("2", DownloadStatus.QUEUED, 0f),
                            previewTask("3", DownloadStatus.PAUSED, 0.2f),
                        ),
                ),
            canDownload = NetworkAccessState(NetworkConnection.UNMETERED).canDownload,
            onBack = ::previewNoOp,
            onPause = ::previewNoOp,
            onResume = ::previewNoOp,
            onCancel = ::previewNoOp,
            onRetry = ::previewNoOp,
            onPauseAll = ::previewNoOp,
            onResumeAll = ::previewNoOp,
            onViewModeChange = ::previewNoOp,
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
