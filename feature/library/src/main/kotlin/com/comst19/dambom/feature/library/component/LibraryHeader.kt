package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import com.comst19.dambom.feature.library.formatBytes

@Composable
internal fun LibraryHeader(
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    showDetailPaneControl: Boolean,
    isDetailPaneVisible: Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
    hasVideos: Boolean,
    isSelecting: Boolean,
    selectedCount: Int,
    videoCount: Int,
    totalBytes: Long,
    onStartSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    if (isSelecting) {
                        stringResource(R.string.library_selected_count, selectedCount)
                    } else {
                        stringResource(R.string.library_title)
                    },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!isSelecting && hasVideos) {
                Text(
                    text = stringResource(R.string.library_storage_summary, videoCount, totalBytes.formatBytes()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (isSelecting) {
            SelectionActions(
                selectedCount = selectedCount,
                onSelectAll = onSelectAll,
                onDeleteSelected = onDeleteSelected,
                onClearSelection = onClearSelection,
            )
            return@Row
        }
        IconButton(
            onClick = {
                onViewModeChange(
                    if (viewMode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID,
                )
            },
        ) {
            Icon(
                imageVector = if (viewMode == LibraryViewMode.GRID) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                contentDescription =
                    stringResource(
                        if (viewMode == LibraryViewMode.GRID) R.string.library_view_as_list else R.string.library_view_as_grid,
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
                            if (isDetailPaneVisible) R.string.library_hide_details else R.string.library_show_details,
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
        if (hasVideos) {
            IconButton(onClick = onStartSelection) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistAddCheck,
                    contentDescription = stringResource(R.string.library_start_selection),
                )
            }
        }
    }
}

@Composable
private fun SelectionActions(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
) {
    IconButton(onClick = onSelectAll) {
        Icon(Icons.Outlined.SelectAll, stringResource(R.string.library_select_all))
    }
    IconButton(onClick = onDeleteSelected, enabled = selectedCount > 0) {
        Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.library_delete_selected))
    }
    IconButton(onClick = onClearSelection) {
        Icon(Icons.Outlined.Close, stringResource(R.string.library_cancel_selection))
    }
}
