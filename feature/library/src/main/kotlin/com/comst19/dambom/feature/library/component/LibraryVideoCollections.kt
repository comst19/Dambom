package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.libraryContentType
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems

@Composable
@Suppress("LongParameterList")
internal fun VideoGrid(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onVideoClick: (DownloadTask) -> Unit,
    isSelecting: Boolean,
    onToggleSelection: (String) -> Unit,
    header: @Composable () -> Unit,
    emptyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MIN_VIDEO_CARD_WIDTH * LocalDensity.current.fontScale.coerceAtLeast(1f)),
        modifier = modifier.fillMaxSize().testTag("library-collection"),
        contentPadding =
            PaddingValues(
                start = LibraryHorizontalPadding,
                end = LibraryHorizontalPadding,
                bottom = 24.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "library-header", contentType = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        if (uiState.videos.isEmpty()) {
            item(key = "library-empty", contentType = "empty", span = { GridItemSpan(maxLineSpan) }) { emptyContent() }
        }
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
                onClick = {
                    if (isSelecting) {
                        onToggleSelection(task.id)
                    } else if (!task.deletePending) {
                        onVideoClick(task)
                    }
                },
                onToggleSelection = { onToggleSelection(task.id) },
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
internal fun VideoList(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onVideoClick: (DownloadTask) -> Unit,
    isSelecting: Boolean,
    onToggleSelection: (String) -> Unit,
    header: @Composable () -> Unit,
    emptyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("library-collection"),
        contentPadding =
            PaddingValues(
                start = LibraryHorizontalPadding,
                end = LibraryHorizontalPadding,
                bottom = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "library-header", contentType = "header") { header() }
        if (uiState.videos.isEmpty()) {
            item(key = "library-empty", contentType = "empty") { emptyContent() }
        }
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
                onClick = {
                    if (isSelecting) {
                        onToggleSelection(task.id)
                    } else if (!task.deletePending) {
                        onVideoClick(task)
                    }
                },
                onToggleSelection = { onToggleSelection(task.id) },
            )
        }
    }
}

private val MIN_VIDEO_CARD_WIDTH = 148.dp
