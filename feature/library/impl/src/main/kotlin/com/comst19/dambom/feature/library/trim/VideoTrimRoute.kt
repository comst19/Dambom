package com.comst19.dambom.feature.library.trim

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.file.suggestedFileName

@Composable
@UnstableApi
@Suppress("LongMethod")
internal fun VideoTrimRoute(
    id: String,
    viewModel: VideoTrimViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.library_share_chooser)
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var shareFailed by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            if (uri != null) viewModel.export(id, uri)
        }
    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(state.exporting) { if (!state.exporting) confirmCancel = false }
    BackHandler(state.exporting) { confirmCancel = true }
    VideoTrimScreen(
        state = state,
        onBack = { if (state.exporting) confirmCancel = true else viewModel.goBack() },
        onSelect = viewModel::select,
        onSave = { launcher.launch("${state.task?.suggestedFileName()?.substringBeforeLast('.').orEmpty()}-clip.mp4") },
        onCancel = { confirmCancel = true },
        onShare = {
            state.savedUri?.let { savedUri ->
                val uri = Uri.parse(savedUri)
                val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(context.contentResolver, "clip", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                try {
                    val chooser = Intent.createChooser(intent, shareTitle)
                    context.startActivity(chooser)
                } catch (_: ActivityNotFoundException) {
                    shareFailed = true
                } catch (_: SecurityException) {
                    shareFailed = true
                }
            }
        },
    )
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.trim_cancel_title)) },
            text = { Text(stringResource(R.string.trim_cancel_description)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancelExport()
                }) {
                    Text(stringResource(R.string.trim_cancel_export))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.trim_continue)) }
            },
        )
    }
    if (shareFailed) {
        AlertDialog(
            onDismissRequest = { shareFailed = false },
            text = { Text(stringResource(R.string.library_share_failure)) },
            confirmButton = {
                TextButton(onClick = { shareFailed = false }) { Text(stringResource(R.string.trim_done)) }
            },
        )
    }
}
