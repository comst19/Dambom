package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.format.formatFileSize
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode

@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun LibraryPane(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onQueryChange: (String) -> Unit,
    onSourceFilterChange: (LibrarySourceFilter) -> Unit,
    onVideoClick: (DownloadTask) -> Unit,
    onToggleSelection: (String) -> Unit,
    showInlineEmptyState: Boolean,
    modifier: Modifier,
    tagControls: @Composable () -> Unit = {},
    onRecentlyPlayedOnlyChange: (Boolean) -> Unit = {},
) {
    val header: @Composable () -> Unit = {
        LibraryPaneHeader(
            uiState,
            onQueryChange,
            onSourceFilterChange,
            fileActions.onFavoritesOnlyChange,
            onRecentlyPlayedOnlyChange,
            tagControls,
        )
    }
    val emptyContent: @Composable () -> Unit = {
        when {
            !uiState.hasVideos -> if (showInlineEmptyState) EmptyLibrary()
            uiState.query.isNotBlank() -> EmptySearchResults(uiState.query)
            uiState.recentlyPlayedOnly -> EmptyRecentlyPlayed()
            uiState.favoritesOnly -> EmptyFavorites()
            else -> EmptySourceResults(hasTagFilter = uiState.hasTagFilter)
        }
    }
    if (uiState.viewMode == LibraryViewMode.GRID) {
        VideoGrid(
            uiState = uiState,
            fileActions = fileActions,
            onVideoClick = onVideoClick,
            isSelecting = uiState.isSelecting,
            onToggleSelection = onToggleSelection,
            header = header,
            emptyContent = emptyContent,
            modifier = modifier,
        )
    } else {
        VideoList(
            uiState = uiState,
            fileActions = fileActions,
            onVideoClick = onVideoClick,
            isSelecting = uiState.isSelecting,
            onToggleSelection = onToggleSelection,
            header = header,
            emptyContent = emptyContent,
            modifier = modifier,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun LibraryPaneHeader(
    uiState: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onSourceFilterChange: (LibrarySourceFilter) -> Unit,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onRecentlyPlayedOnlyChange: (Boolean) -> Unit,
    tagControls: @Composable () -> Unit,
) {
    Column {
        if (!uiState.isSelecting && uiState.hasVideos) {
            Text(
                text =
                    stringResource(
                        R.string.library_storage_summary,
                        uiState.totalVideoCount,
                        uiState.totalBytes.formatFileSize(),
                    ),
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LibrarySearchField(uiState.query, onQueryChange, Modifier.padding(bottom = 8.dp))
        LibrarySourceFilters(
            selected = uiState.sourceFilter,
            onSelected = onSourceFilterChange,
            favoritesOnly = uiState.favoritesOnly,
            onFavoritesOnlyChange = onFavoritesOnlyChange,
            tagControls = tagControls,
            recentlyPlayedOnly = uiState.recentlyPlayedOnly,
            onRecentlyPlayedOnlyChange = onRecentlyPlayedOnlyChange,
        )
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
    TextField(
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
        shape = DambomShapes.Control,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
    )
}
