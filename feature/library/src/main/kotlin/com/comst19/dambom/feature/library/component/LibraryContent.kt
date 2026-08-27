package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems

@Composable
internal fun LibraryPane(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onQueryChange: (String) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onVideoClick: (DownloadTask) -> Unit,
    showInlineEmptyState: Boolean,
    showDetailPaneControl: Boolean,
    isDetailPaneVisible: Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        LibraryHeader(
            viewMode = uiState.viewMode,
            onViewModeChange = onViewModeChange,
            showDetailPaneControl = showDetailPaneControl,
            isDetailPaneVisible = isDetailPaneVisible,
            onDetailPaneVisibilityChange = onDetailPaneVisibilityChange,
        )
        LibrarySearchField(
            query = uiState.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        )
        if (!uiState.hasVideos) {
            if (showInlineEmptyState) {
                EmptyLibrary(Modifier.weight(1f))
            }
        } else if (uiState.videos.isEmpty()) {
            EmptySearchResults(
                query = uiState.query,
                modifier = Modifier.weight(1f),
            )
        } else if (uiState.viewMode == LibraryViewMode.GRID) {
            VideoGrid(
                uiState = uiState,
                fileActions = fileActions,
                onVideoClick = onVideoClick,
            )
        } else {
            VideoList(
                uiState = uiState,
                fileActions = fileActions,
                onVideoClick = onVideoClick,
            )
        }
    }
}

@Composable
private fun LibraryHeader(
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    showDetailPaneControl: Boolean,
    isDetailPaneVisible: Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick = {
                onViewModeChange(
                    if (viewMode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID,
                )
            },
        ) {
            Icon(
                imageVector =
                    if (viewMode == LibraryViewMode.GRID) {
                        Icons.AutoMirrored.Outlined.ViewList
                    } else {
                        Icons.Outlined.GridView
                    },
                contentDescription =
                    stringResource(
                        if (viewMode == LibraryViewMode.GRID) {
                            R.string.library_view_as_list
                        } else {
                            R.string.library_view_as_grid
                        },
                    ),
            )
        }
        if (showDetailPaneControl) {
            IconToggleButton(
                checked = isDetailPaneVisible,
                onCheckedChange = onDetailPaneVisibilityChange,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ViewSidebar,
                    contentDescription =
                        stringResource(
                            if (isDetailPaneVisible) {
                                R.string.library_hide_details
                            } else {
                                R.string.library_show_details
                            },
                        ),
                    tint =
                        if (isDetailPaneVisible) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
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

@Composable
private fun VideoGrid(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onVideoClick: (DownloadTask) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MIN_VIDEO_CARD_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(
            items = uiState.videos,
            key = DownloadTask::id,
            contentType = { VIDEO_ITEM_CONTENT_TYPE },
        ) { task ->
            VideoCard(
                task = task,
                selected = task.id == uiState.selectedVideo?.id,
                fileActions = fileActions,
                onClick = { onVideoClick(task) },
            )
        }
    }
}

@Composable
private fun VideoList(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onVideoClick: (DownloadTask) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listItems(
            items = uiState.videos,
            key = DownloadTask::id,
            contentType = { VIDEO_ITEM_CONTENT_TYPE },
        ) { task ->
            VideoListItem(
                task = task,
                selected = task.id == uiState.selectedVideo?.id,
                fileActions = fileActions,
                onClick = { onVideoClick(task) },
            )
        }
    }
}

private val MIN_VIDEO_CARD_WIDTH = 156.dp
private const val VIDEO_ITEM_CONTENT_TYPE = "video"
