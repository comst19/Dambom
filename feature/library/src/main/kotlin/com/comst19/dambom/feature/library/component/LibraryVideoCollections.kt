package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.libraryContentType
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems

@Composable
internal fun VideoGrid(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onVideoClick: (DownloadTask) -> Unit,
    isSelecting: Boolean,
    onToggleSelection: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MIN_VIDEO_CARD_WIDTH * LocalDensity.current.fontScale.coerceAtLeast(1f)),
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = LibraryHorizontalPadding,
                end = LibraryHorizontalPadding,
                bottom = 24.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(
            items = uiState.videos,
            key = DownloadTask::id,
            contentType = DownloadTask::libraryContentType,
        ) { task ->
            VideoCard(
                task = task,
                selected = task.id == uiState.selectedVideo?.id,
                selectionSelected = task.id in uiState.selectedIds,
                isSelecting = isSelecting,
                fileActions = fileActions,
                onClick = { if (isSelecting) onToggleSelection(task.id) else onVideoClick(task) },
                onToggleSelection = { onToggleSelection(task.id) },
            )
        }
    }
}

@Composable
internal fun VideoList(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onVideoClick: (DownloadTask) -> Unit,
    isSelecting: Boolean,
    onToggleSelection: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = LibraryHorizontalPadding,
                end = LibraryHorizontalPadding,
                bottom = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listItems(
            items = uiState.videos,
            key = DownloadTask::id,
            contentType = DownloadTask::libraryContentType,
        ) { task ->
            VideoListItem(
                task = task,
                selected = task.id == uiState.selectedVideo?.id,
                selectionSelected = task.id in uiState.selectedIds,
                isSelecting = isSelecting,
                fileActions = fileActions,
                onClick = { if (isSelecting) onToggleSelection(task.id) else onVideoClick(task) },
                onToggleSelection = { onToggleSelection(task.id) },
            )
        }
    }
}

private val MIN_VIDEO_CARD_WIDTH = 148.dp
