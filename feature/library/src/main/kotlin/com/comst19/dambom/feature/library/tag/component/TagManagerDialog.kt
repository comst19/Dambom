package com.comst19.dambom.feature.library.tag.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.VideoTag
import com.comst19.dambom.feature.library.R
import kotlinx.collections.immutable.PersistentList

@Composable
internal fun TagManagerDialog(
    tags: PersistentList<VideoTag>,
    onCreate: (String, (Boolean) -> Unit) -> Unit,
    onRename: (String, String, (Boolean) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(tags, editingId, deletingId) {
        if (editingId != null && tags.none { it.id == editingId }) {
            editorOpen = false
            editingId = null
        }
        if (deletingId != null && tags.none { it.id == deletingId }) deletingId = null
    }
    val editing = tags.firstOrNull { it.id == editingId }
    val deleting = tags.firstOrNull { it.id == deletingId }
    when {
        editorOpen && (editingId == null || editing != null) -> {
            TagEditorDialog(
                tag = editing,
                onCreate = onCreate,
                onRename = onRename,
                onDismiss = {
                    editorOpen = false
                    editingId = null
                },
            )
        }

        deleting != null -> {
            DeleteTagDialog(deleting, onDismiss = { deletingId = null }, onConfirm = {
                onDelete(deleting.id)
                deletingId = null
            })
        }

        else -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.library_manage_tags)) },
                text = {
                    TagManagerList(tags, onEdit = {
                        editingId = it.id
                        editorOpen = true
                    }, onDelete = { deletingId = it.id }, onNew = {
                        editingId = null
                        editorOpen = true
                    })
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_tag_done)) } },
            )
        }
    }
}

@Composable
private fun TagManagerList(
    tags: PersistentList<VideoTag>,
    onEdit: (VideoTag) -> Unit,
    onDelete: (VideoTag) -> Unit,
    onNew: () -> Unit,
) {
    Column {
        LazyColumn(Modifier.heightIn(max = 320.dp).testTag("library-tag-manager-list")) {
            if (tags.isEmpty()) {
                item { Text(stringResource(R.string.library_tags_empty_hint)) }
            }
            items(tags, key = { it.id }) { tag ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(tag.name, style = MaterialTheme.typography.titleSmall)
                    Row {
                        TextButton(
                            modifier = Modifier.testTag("library-tag-rename-${tag.id}"),
                            onClick = { onEdit(tag) },
                        ) { Text(stringResource(R.string.library_rename_tag)) }
                        TextButton(onClick = { onDelete(tag) }) {
                            Text(stringResource(R.string.library_tag_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        FilledTonalButton(onClick = onNew) { Text(stringResource(R.string.library_new_tag)) }
    }
}

@Composable
private fun TagEditorDialog(
    tag: VideoTag?,
    onCreate: (String, (Boolean) -> Unit) -> Unit,
    onRename: (String, String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(tag?.id) { mutableStateOf(tag?.name.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { active = false } }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(if (tag == null) R.string.library_new_tag else R.string.library_rename_tag)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.library_tag_name)) },
                singleLine = true,
                enabled = !saving,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !saving, onClick = {
                saving = true
                val onComplete: (Boolean) -> Unit = { success ->
                    if (active) {
                        saving = false
                        if (success) {
                            name = ""
                            onDismiss()
                        }
                    }
                }
                if (tag == null) onCreate(name, onComplete) else onRename(tag.id, name, onComplete)
            }) { Text(stringResource(if (tag == null) R.string.library_tag_add else R.string.library_tag_save)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.library_tag_cancel)) }
        },
    )
}

@Composable
private fun DeleteTagDialog(
    tag: VideoTag,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_tag_delete)) },
        text = { Text(stringResource(R.string.library_tag_delete_hint, tag.name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.library_tag_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_tag_cancel)) } },
    )
}
