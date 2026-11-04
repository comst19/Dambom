package com.comst19.dambom.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.comst19.dambom.feature.library.tag.LibraryTagViewModel
import com.comst19.dambom.feature.library.tag.component.LibraryTagControls
import com.comst19.dambom.feature.library.tag.component.rememberTagFilteredVideos
import com.comst19.dambom.feature.library.vault.PrivateVaultLockedScreen
import com.comst19.dambom.feature.library.vault.PrivateVideoWindowProtection
import com.comst19.dambom.feature.library.vault.rememberPrivateVaultAuthentication
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
@Suppress("LongMethod")
internal fun LibraryRoute(
    isDetailPaneVisible: Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
    activeVideoId: String?,
    privateVault: Boolean = false,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val unlocked by viewModel.privateVideoSession.isUnlocked.collectAsStateWithLifecycle()
    var pendingPrivacyIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val authenticate =
        rememberPrivateVaultAuthentication {
            viewModel.privateVideoSession.unlock()
            if (pendingPrivacyIds.isNotEmpty()) {
                viewModel.changePrivacy(pendingPrivacyIds, !privateVault)
                pendingPrivacyIds = emptySet()
            }
        }
    DisposableEffect(viewModel, privateVault) {
        viewModel.setPrivateVault(privateVault)
        onDispose { }
    }
    if (privateVault) {
        PrivateVideoWindowProtection()
        BackHandler(onBack = viewModel::leavePrivateVault)
        if (!unlocked) {
            PrivateVaultLockedScreen(authenticate, viewModel::leavePrivateVault)
            return
        }
    }

    fun changePrivacy(ids: Set<String>) {
        if (unlocked) {
            viewModel.changePrivacy(ids, !privateVault)
        } else {
            pendingPrivacyIds = ids
            authenticate()
        }
    }
    val tagViewModel: LibraryTagViewModel = hiltViewModel()
    val tagState by tagViewModel.state.collectAsStateWithLifecycle()
    val visibleVideos = rememberTagFilteredVideos(uiState.videos, tagState, tagViewModel::filterVideos)
    val fileActions = rememberLibraryFileActions(viewModel).copy(onChangePrivacy = { changePrivacy(setOf(it.id)) })
    val multiplePanes = currentAdaptiveLayoutInfo().supportsMultiplePanes
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
    }

    LibraryScreen(
        uiState =
            uiState.copy(
                isPrivateVault = privateVault,
                videos = visibleVideos,
                selectedVideo = uiState.videos.firstOrNull { it.id == activeVideoId },
                hasTagFilter = tagState.selectedTagIds.isNotEmpty(),
            ),
        tagControls = {
            LibraryTagControls(
                state = tagState,
                selectedVideoIds = uiState.selectedIds,
                onToggleFilter = tagViewModel::toggleFilter,
                onCreate = tagViewModel::create,
                onRename = tagViewModel::rename,
                onDelete = tagViewModel::delete,
                onAssign = tagViewModel::assign,
            )
        },
        fileActions = fileActions,
        onQueryChange = viewModel::updateQuery,
        onViewModeChange = viewModel::setViewMode,
        onSourceFilterChange = viewModel::setSourceFilter,
        onRecentlyPlayedOnlyChange = viewModel::setRecentlyPlayedOnly,
        onStartSelection = viewModel::startSelection,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = { viewModel.selectAllVisible(visibleVideos.map(DownloadTask::id)) },
        onClearSelection = viewModel::clearSelection,
        onDeleteSelected = viewModel::deleteSelected,
        onVaultClick = if (privateVault) viewModel::lockPrivateVault else viewModel::openPrivateVault,
        onBack = if (privateVault) viewModel::leavePrivateVault else null,
        onChangeSelectedPrivacy = { changePrivacy(uiState.selectedIds) },
        onVideoClick = { task ->
            if (!privateVault && multiplePanes && !isDetailPaneVisible) onDetailPaneVisibilityChange(true)
            if (task.id != activeVideoId) {
                viewModel.openVideo(task.id, replaceDetail = activeVideoId != null)
            }
        },
        showInlineEmptyState = !multiplePanes || !isDetailPaneVisible,
        showDetailPaneControl = multiplePanes && !privateVault,
        isDetailPaneVisible = isDetailPaneVisible,
        onDetailPaneVisibilityChange = onDetailPaneVisibilityChange,
    )
}

@Composable
@Suppress("LongParameterList")
internal fun LibraryScreen(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onQueryChange: (String) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onSourceFilterChange: (LibrarySourceFilter) -> Unit = {},
    onRecentlyPlayedOnlyChange: (Boolean) -> Unit = {},
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
    tagControls: @Composable () -> Unit = {},
    onVaultClick: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onChangeSelectedPrivacy: () -> Unit = {},
) {
    var deleteSelectedOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = uiState.isSelecting, onBack = onClearSelection)
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
                isPrivateVault = uiState.isPrivateVault,
                onVaultClick = onVaultClick,
                onBack = onBack,
                onChangeSelectedPrivacy = onChangeSelectedPrivacy,
            )
        },
    ) { innerPadding ->
        LibraryPane(
            uiState =
                if (showDetailPaneControl && isDetailPaneVisible) uiState else uiState.copy(selectedVideo = null),
            fileActions = fileActions,
            onQueryChange = onQueryChange,
            onSourceFilterChange = onSourceFilterChange,
            onRecentlyPlayedOnlyChange = onRecentlyPlayedOnlyChange,
            onVideoClick = onVideoClick,
            onToggleSelection = onToggleSelection,
            showInlineEmptyState = showInlineEmptyState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            tagControls = tagControls,
        )
    }
    if (deleteSelectedOpen) {
        DeleteSelectedVideosDialog(
            count = uiState.selectedIds.size,
            hiddenCount = uiState.hiddenSelectedCount,
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
    val hasVideosFlow = remember(viewModel) { viewModel.uiState.map { it.hasVideos }.distinctUntilChanged() }
    val initialHasVideos = remember(viewModel) { viewModel.uiState.value.hasVideos }
    val hasVideos by hasVideosFlow.collectAsStateWithLifecycle(initialValue = initialHasVideos)

    if (hasVideos) {
        EmptyLibraryDetail()
    } else {
        EmptyLibrary(Modifier.fillMaxSize())
    }
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

@androidx.compose.ui.tooling.preview.Preview(
    name = "Korean private library",
    locale = "ko",
    widthDp = 393,
    heightDp = 852,
)
@FormFactorPreviews
@Composable
private fun PrivateLibraryScreenPreview() {
    DambomTheme {
        LibraryScreen(
            uiState =
                LibraryUiState(
                    videos =
                        persistentListOf(
                            previewTask("여행 기록").copy(isPrivate = true),
                            previewTask("겨울 산행").copy(isPrivate = true),
                        ),
                    hasVideos = true,
                    totalVideoCount = 2,
                    totalBytes = 8_000_000L,
                    isPrivateVault = true,
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
                    onChangePrivacy = ::previewNoOp,
                ),
            onQueryChange = ::previewNoOp,
            onViewModeChange = ::previewNoOp,
            onVideoClick = ::previewNoOp,
            onBack = ::previewNoOp,
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
