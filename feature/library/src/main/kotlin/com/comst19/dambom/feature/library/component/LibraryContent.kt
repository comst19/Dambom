package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode

@Composable
internal fun LibraryPane(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onQueryChange: (String) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onSourceFilterChange: (LibrarySourceFilter) -> Unit,
    onVideoClick: (DownloadTask) -> Unit,
    onStartSelection: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    showInlineEmptyState: Boolean,
    showDetailPaneControl: Boolean,
    isDetailPaneVisible: Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var deleteSelectedOpen by remember { mutableStateOf(false) }
    Column(modifier) {
        LibraryHeader(
            viewMode = uiState.viewMode,
            onViewModeChange = onViewModeChange,
            showDetailPaneControl = showDetailPaneControl,
            isDetailPaneVisible = isDetailPaneVisible,
            onDetailPaneVisibilityChange = onDetailPaneVisibilityChange,
            hasVideos = uiState.hasVideos,
            isSelecting = uiState.isSelecting,
            selectedCount = uiState.selectedIds.size,
            videoCount = uiState.totalVideoCount,
            totalBytes = uiState.totalBytes,
            onStartSelection = onStartSelection,
            onSelectAll = onSelectAll,
            onDeleteSelected = { deleteSelectedOpen = true },
            onClearSelection = onClearSelection,
        )
        LibrarySearchField(
            query = uiState.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        LibrarySourceFilters(
            selected = uiState.sourceFilter,
            onSelected = onSourceFilterChange,
        )
        if (!uiState.hasVideos) {
            if (showInlineEmptyState) {
                EmptyLibrary(Modifier.weight(1f))
            }
        } else if (uiState.videos.isEmpty()) {
            if (uiState.query.isNotBlank()) {
                EmptySearchResults(
                    query = uiState.query,
                    modifier = Modifier.weight(1f),
                )
            } else {
                EmptySourceResults(Modifier.weight(1f))
            }
        } else if (uiState.viewMode == LibraryViewMode.GRID) {
            VideoGrid(
                uiState = uiState,
                fileActions = fileActions,
                onVideoClick = onVideoClick,
                isSelecting = uiState.isSelecting,
                onToggleSelection = onToggleSelection,
            )
        } else {
            VideoList(
                uiState = uiState,
                fileActions = fileActions,
                onVideoClick = onVideoClick,
                isSelecting = uiState.isSelecting,
                onToggleSelection = onToggleSelection,
            )
        }
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
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
            )
        },
        trailingIcon =
            if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.library_search_clear),
                        )
                    }
                }
            } else {
                null
            },
        singleLine = true,
    )
}
