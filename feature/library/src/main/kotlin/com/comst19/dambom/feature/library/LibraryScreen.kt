package com.comst19.dambom.feature.library

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.common.ui.currentAdaptiveLayoutInfo
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.designsystem.previewNoOp
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.component.DeleteSelectedVideosDialog
import com.comst19.dambom.feature.library.component.EmptyLibrary
import com.comst19.dambom.feature.library.component.EmptyLibraryDetail
import com.comst19.dambom.feature.library.component.LibraryFileActions
import com.comst19.dambom.feature.library.component.LibraryHeader
import com.comst19.dambom.feature.library.component.LibraryPane
import com.comst19.dambom.feature.library.component.rememberLibraryFileActions
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun LibraryRoute(
    isDetailPaneVisible: Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fileActions = rememberLibraryFileActions(viewModel)
    val multiplePanes = currentAdaptiveLayoutInfo().supportsMultiplePanes
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
    }

    LibraryScreen(
        uiState = uiState,
        fileActions = fileActions,
        onQueryChange = viewModel::updateQuery,
        onViewModeChange = viewModel::setViewMode,
        onSourceFilterChange = viewModel::setSourceFilter,
        onStartSelection = viewModel::startSelection,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAllVisible,
        onClearSelection = viewModel::clearSelection,
        onDeleteSelected = viewModel::deleteSelected,
        onVideoClick = { task ->
            if (multiplePanes && !isDetailPaneVisible) onDetailPaneVisibilityChange(true)
            viewModel.openVideo(task.id)
        },
        showInlineEmptyState = !multiplePanes || !isDetailPaneVisible,
        showDetailPaneControl = multiplePanes,
        isDetailPaneVisible = isDetailPaneVisible,
        onDetailPaneVisibilityChange = onDetailPaneVisibilityChange,
    )
}

@Composable
internal fun LibraryScreen(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onQueryChange: (String) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onSourceFilterChange: (LibrarySourceFilter) -> Unit = {},
    onVideoClick: (DownloadTask) -> Unit,
    onStartSelection: () -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    showInlineEmptyState: Boolean = true,
    showDetailPaneControl: Boolean = false,
    isDetailPaneVisible: Boolean = true,
    onDetailPaneVisibilityChange: (Boolean) -> Unit = {},
) {
    var deleteSelectedOpen by remember { mutableStateOf(false) }
    AppScreen(
        topBar = {
            LibraryHeader(
                viewMode = uiState.viewMode,
                onViewModeChange = onViewModeChange,
                showDetailPaneControl = showDetailPaneControl,
                isDetailPaneVisible = isDetailPaneVisible,
                onDetailPaneVisibilityChange = onDetailPaneVisibilityChange,
                hasVideos = uiState.hasVideos,
                isSelecting = uiState.isSelecting,
                selectedCount = uiState.selectedIds.size,
                onStartSelection = onStartSelection,
                onSelectAll = onSelectAll,
                onDeleteSelected = { deleteSelectedOpen = true },
                onClearSelection = onClearSelection,
            )
        },
    ) { innerPadding ->
        LibraryPane(
            uiState = uiState,
            fileActions = fileActions,
            onQueryChange = onQueryChange,
            onSourceFilterChange = onSourceFilterChange,
            onVideoClick = onVideoClick,
            onToggleSelection = onToggleSelection,
            showInlineEmptyState = showInlineEmptyState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        )
    }
    if (deleteSelectedOpen) {
        DeleteSelectedVideosDialog(
            count = uiState.selectedIds.size,
            onDismiss = { deleteSelectedOpen = false },
            onConfirm = {
                deleteSelectedOpen = false
                onDeleteSelected()
            },
        )
    }
}

@Composable
internal fun LibraryDetailPlaceholderRoute() {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.hasVideos) {
        EmptyLibraryDetail()
    } else {
        EmptyLibrary(Modifier.fillMaxSize())
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
            uiState =
                LibraryUiState(
                    videos = persistentListOf(previewTask("여행 영상"), previewTask("인터뷰")),
                    hasVideos = true,
                ),
            fileActions =
                LibraryFileActions(
                    onRename = ::previewNoOp,
                    onExport = ::previewNoOp,
                    onShareVideo = ::previewNoOp,
                    onShareLink = ::previewNoOp,
                    onCopyLink = ::previewNoOp,
                    onOpenOriginal = ::previewNoOp,
                    onDelete = ::previewNoOp,
                ),
            onQueryChange = ::previewNoOp,
            onViewModeChange = ::previewNoOp,
            onVideoClick = ::previewNoOp,
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
