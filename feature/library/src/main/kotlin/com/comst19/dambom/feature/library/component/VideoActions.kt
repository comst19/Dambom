package com.comst19.dambom.feature.library.component

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.LibraryViewModel
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.file.suggestedFileName

@Immutable
internal data class LibraryFileActions(
    val onRename: (DownloadTask, String) -> Unit,
    val onExport: (DownloadTask) -> Unit,
    val onShare: (DownloadTask) -> Unit,
    val onCopyLink: (DownloadTask) -> Unit,
    val onOpenOriginal: (DownloadTask) -> Unit,
    val onDelete: (DownloadTask) -> Unit,
)

@Composable
internal fun rememberLibraryFileActions(
    viewModel: LibraryViewModel,
    onDelete: ((DownloadTask) -> Unit)? = null,
): LibraryFileActions {
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<DownloadTask?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/*")) { destination ->
            val task = pendingExport
            pendingExport = null
            if (destination != null && task != null) viewModel.export(task, destination)
        }
    val currentOnDelete = rememberUpdatedState(onDelete)
    return remember(viewModel, context, exportLauncher) {
        LibraryFileActions(
            onRename = viewModel::rename,
            onExport = { task ->
                pendingExport = task
                exportLauncher.launch(task.suggestedFileName())
            },
            onShare = { task ->
                val intent = viewModel.createShareIntent(task)
                if (intent == null) {
                    viewModel.notifyShareFailure()
                    return@LibraryFileActions
                }
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    viewModel.notifyShareFailure()
                } catch (_: IllegalArgumentException) {
                    viewModel.notifyShareFailure()
                }
            },
            onCopyLink = { task ->
                context
                    .getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("URL", task.sourcePageUrl))
                viewModel.notifyLinkCopied()
            },
            onOpenOriginal = { task ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(task.sourcePageUrl)))
                } catch (_: ActivityNotFoundException) {
                    viewModel.notifyOpenOriginalFailure()
                } catch (_: IllegalArgumentException) {
                    viewModel.notifyOpenOriginalFailure()
                }
            },
            onDelete = { task -> currentOnDelete.value?.invoke(task) ?: viewModel.delete(task) },
        )
    }
}

@Composable
internal fun VideoActionsButton(
    task: DownloadTask,
    actions: LibraryFileActions,
    modifier: Modifier = Modifier,
    iconOffsetY: Dp = 0.dp,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    IconButton(
        onClick = { menuExpanded = true },
        modifier = modifier.size(ACTION_TARGET_SIZE),
    ) {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = stringResource(R.string.library_more_actions, task.title),
            modifier = Modifier.offset(y = iconOffsetY),
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            ActionMenuItem(
                label = stringResource(R.string.library_rename),
                icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    renameOpen = true
                },
            )
            ActionMenuItem(
                label = stringResource(R.string.library_share),
                icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    actions.onShare(task)
                },
            )
            ActionMenuItem(
                label = stringResource(R.string.library_copy_link),
                icon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    actions.onCopyLink(task)
                },
            )
            ActionMenuItem(
                label = stringResource(R.string.library_open_original),
                icon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    actions.onOpenOriginal(task)
                },
            )
            ActionMenuItem(
                label = stringResource(R.string.library_export),
                icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    actions.onExport(task)
                },
            )
            ActionMenuItem(
                label = stringResource(R.string.library_delete),
                icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    deleteOpen = true
                },
            )
        }
    }

    if (renameOpen) {
        RenameVideoDialog(
            task = task,
            onDismiss = { renameOpen = false },
            onConfirm = { title ->
                renameOpen = false
                actions.onRename(task, title)
            },
        )
    }
    if (deleteOpen) {
        DeleteVideoDialog(
            task = task,
            onDismiss = { deleteOpen = false },
            onConfirm = {
                deleteOpen = false
                actions.onDelete(task)
            },
        )
    }
}

@Composable
private fun ActionMenuItem(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = icon,
    )
}

@Composable
private fun RenameVideoDialog(
    task: DownloadTask,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(task.id, task.title) { mutableStateOf(task.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rename_title)) },
        text = {
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.library_video_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank() && title.trim() != task.title,
            ) {
                Text(stringResource(R.string.library_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_cancel))
            }
        },
    )
}

@Composable
private fun DeleteVideoDialog(
    task: DownloadTask,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_title)) },
        text = { Text(stringResource(R.string.library_delete_description, task.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.library_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_cancel))
            }
        },
    )
}

private val ACTION_TARGET_SIZE = 48.dp
