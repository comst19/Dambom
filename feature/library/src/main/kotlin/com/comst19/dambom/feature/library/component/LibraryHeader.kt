package com.comst19.dambom.feature.library.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.contract.LibraryViewMode

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun LibraryHeader(
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    showDetailPaneControl: Boolean,
    isDetailPaneVisible: Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
    hasVideos: Boolean,
    isSelecting: Boolean,
    selectedCount: Int,
    onStartSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text =
                    if (isSelecting) {
                        stringResource(R.string.library_selected_count, selectedCount)
                    } else {
                        stringResource(R.string.library_title)
                    },
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            if (isSelecting) {
                SelectionActions(
                    selectedCount = selectedCount,
                    onSelectAll = onSelectAll,
                    onDeleteSelected = onDeleteSelected,
                    onClearSelection = onClearSelection,
                )
            } else {
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
                if (hasVideos) {
                    IconButton(onClick = onStartSelection) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.PlaylistAddCheck,
                            contentDescription = stringResource(R.string.library_start_selection),
                        )
                    }
                }
            }
        },
    )
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
