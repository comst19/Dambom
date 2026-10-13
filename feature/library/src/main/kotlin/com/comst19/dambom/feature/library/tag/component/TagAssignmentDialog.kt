package com.comst19.dambom.feature.library.tag.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.tag.contract.LibraryTagState

@Composable
internal fun TagAssignmentDialog(
    state: LibraryTagState,
    selectedVideoIds: Set<String>,
    onAssign: (Set<String>, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(R.string.library_assign_tags, selectedVideoIds.size)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                if (state.tags.isEmpty()) {
                    item { Text(stringResource(R.string.library_create_tag_hint)) }
                }
                items(state.tags, key = { it.id }) { tag ->
                    val assigned = state.isAssignedToAll(tag.id, selectedVideoIds)
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = assigned,
                            role = Role.Checkbox,
                            onValueChange = { onAssign(selectedVideoIds, tag.id, it) },
                        ),
                    ) {
                        Checkbox(
                            checked = assigned,
                            onCheckedChange = null,
                        )
                        Text(tag.name, Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.library_tag_done)) }
        },
    )
}
