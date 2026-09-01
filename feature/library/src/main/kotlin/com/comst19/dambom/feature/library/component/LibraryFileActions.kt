package com.comst19.dambom.feature.library.component

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.LibraryViewModel
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.external.copyOriginalLink
import com.comst19.dambom.feature.library.external.openOriginalLink
import com.comst19.dambom.feature.library.external.shareOriginalLink
import com.comst19.dambom.feature.library.file.suggestedFileName

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
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentSettings = rememberUpdatedState(settings)
    var pendingExport by remember { mutableStateOf<DownloadTask?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/*")) { destination ->
            val task = pendingExport
            pendingExport = null
            if (destination != null && task != null) viewModel.export(task, destination)
        }
    var pendingDefaultExport by remember { mutableStateOf<DownloadTask?>(null) }
    val legacyStoragePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val task = pendingDefaultExport
            pendingDefaultExport = null
            if (task == null) return@rememberLauncherForActivityResult
            if (granted) {
                viewModel.exportToConfiguredLocation(task)
            } else {
                pendingExport = task
                exportLauncher.launch(task.suggestedFileName())
            }
        }
    val currentOnDelete = rememberUpdatedState(onDelete)
    return remember(viewModel, context, exportLauncher, legacyStoragePermissionLauncher) {
        LibraryFileActions(
            onRename = viewModel::rename,
            onExport = { task ->
                val downloadSettings = currentSettings.value
                when {
                    !downloadSettings.useConfiguredDownloadLocation -> {
                        pendingExport = task
                        exportLauncher.launch(task.suggestedFileName())
                    }

                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        downloadSettings.downloadTreeUri == null &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED -> {
                        pendingDefaultExport = task
                        legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }

                    else -> {
                        viewModel.exportToConfiguredLocation(task)
                    }
                }
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
