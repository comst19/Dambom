package com.comst19.dambom.feature.library.component

import android.content.ActivityNotFoundException
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.comst19.dambom.feature.library.VideoSourceKind
import com.comst19.dambom.feature.library.external.copyOriginalLink
import com.comst19.dambom.feature.library.external.openOriginalLink
import com.comst19.dambom.feature.library.external.shareOriginalLink
import com.comst19.dambom.feature.library.file.suggestedFileName
import com.comst19.dambom.feature.library.videoSourcePresentation

@Immutable
internal data class LibraryFileActions(
    val onRename: (DownloadTask, String) -> Unit,
    val onExport: (DownloadTask) -> Unit,
    val onShareVideo: (DownloadTask) -> Unit,
    val onShareLink: (DownloadTask) -> Unit,
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
            onShareVideo = { task ->
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
            onShareLink = { task ->
                val chooserTitle = context.getString(R.string.library_share_link_chooser)
                if (!shareOriginalLink(context, task.sourcePageUrl, chooserTitle)) {
                    viewModel.notifyShareLinkFailure()
                }
            },
            onCopyLink = { task ->
                copyOriginalLink(context, task.sourcePageUrl)
                viewModel.notifyLinkCopied()
            },
            onOpenOriginal = { task ->
                if (!openOriginalLink(context, task.sourcePageUrl)) viewModel.notifyOpenOriginalFailure()
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
    val sourceKind = videoSourcePresentation(task.sourcePageUrl).kind

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
                label = stringResource(R.string.library_share_video),
                icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    actions.onShareVideo(task)
                },
            )
            ActionMenuItem(
                label = stringResource(R.string.library_share_link),
                icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    actions.onShareLink(task)
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
                label =
                    stringResource(
                        if (sourceKind == VideoSourceKind.X) {
                            R.string.player_open_in_x
                        } else {
                            R.string.player_open_website
                        },
                    ),
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

private val ACTION_TARGET_SIZE = 48.dp
