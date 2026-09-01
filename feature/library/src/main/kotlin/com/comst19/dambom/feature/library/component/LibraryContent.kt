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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.format.formatFileSize
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
    onSourceFilterChange: (LibrarySourceFilter) -> Unit,
    onVideoClick: (DownloadTask) -> Unit,
    onToggleSelection: (String) -> Unit,
    showInlineEmptyState: Boolean,
    modifier: Modifier,
) {
    Column(modifier) {
        if (!uiState.isSelecting && uiState.hasVideos) {
            Text(
                text =
                    stringResource(
                        R.string.library_storage_summary,
                        uiState.totalVideoCount,
                        uiState.totalBytes.formatFileSize(),
                    ),
                modifier =
                    Modifier.padding(
                        start = LibraryHorizontalPadding,
                        end = LibraryHorizontalPadding,
                        bottom = 8.dp,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LibrarySearchField(
            query = uiState.query,
            onQueryChange = onQueryChange,
            modifier =
                Modifier.padding(
                    start = LibraryHorizontalPadding,
                    end = LibraryHorizontalPadding,
                    bottom = 8.dp,
                ),
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
}

internal val LibraryHorizontalPadding = 16.dp
internal const val LIBRARY_SEARCH_FIELD_TAG = "library-search-field"

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().testTag(LIBRARY_SEARCH_FIELD_TAG),
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
