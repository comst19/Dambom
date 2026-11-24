package com.comst19.dambom.feature.library.tag.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.tag.contract.LibraryTagState

@Composable
@Suppress("LongParameterList")
internal fun LibraryTagControls(
    state: LibraryTagState,
    selectedVideoIds: Set<String>,
    onToggleFilter: (String) -> Unit,
    onCreate: (String, (Boolean) -> Unit) -> Unit,
    onRename: (String, String, (Boolean) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onAssign: (Set<String>, String, Boolean) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    FilterChip(
        selected = state.selectedTagIds.isNotEmpty(),
        onClick = { dialog = "filter" },
        modifier = Modifier.testTag("library-tags-button"),
        label = {
            Text(
                if (state.selectedTagIds.isEmpty()) {
                    stringResource(R.string.library_tags_filter)
                } else {
                    stringResource(R.string.library_tags_filter_selected, state.selectedTagIds.size)
                },
                maxLines = 1,
            )
        },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
    )
    if (selectedVideoIds.isNotEmpty()) {
        TextButton(onClick = { dialog = "assign" }) {
            Text(
                stringResource(R.string.library_assign_tags, selectedVideoIds.size),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    when (dialog) {
        "filter" -> {
            TagFilterDialog(
                state,
                onToggleFilter,
                onManage = { dialog = "manage" },
                onDismiss = { dialog = null },
            )
        }

        "manage" -> {
            TagManagerDialog(state.tags, onCreate, onRename, onDelete, onDismiss = { dialog = null })
        }

        "assign" -> {
            TagAssignmentDialog(state, selectedVideoIds, onAssign, onDismiss = { dialog = null })
        }
    }
}

@Composable
private fun TagFilterDialog(
    state: LibraryTagState,
    onToggleFilter: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_tags_filter)) },
        text = {
            Column {
                Text(stringResource(R.string.library_tags_filter_hint))
                LazyColumn(Modifier.heightIn(max = 320.dp).testTag("library-tag-filters")) {
                    if (state.tags.isEmpty()) {
                        item {
                            Text(stringResource(R.string.library_tags_empty_hint), Modifier.padding(vertical = 16.dp))
                        }
                    }
                    items(state.tags, key = { it.id }) { tag ->
                        val selected = tag.id in state.selectedTagIds
                        Row(
                            modifier =
                                Modifier.fillMaxWidth().toggleable(
                                    value = selected,
                                    role = Role.Checkbox,
                                    onValueChange = { onToggleFilter(tag.id) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Text(tag.name, Modifier.weight(1f))
                        }
                    }
                }
                FlowRow {
                    TextButton(enabled = state.selectedTagIds.isNotEmpty(), onClick = {
                        state.selectedTagIds.forEach(onToggleFilter)
                    }) { Text(stringResource(R.string.library_tags_clear)) }
                    TextButton(onClick = onManage) { Text(stringResource(R.string.library_manage_tags)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_tag_done)) } },
    )
}
