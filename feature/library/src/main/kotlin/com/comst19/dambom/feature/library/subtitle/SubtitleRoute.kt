package com.comst19.dambom.feature.library.subtitle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.file.suggestedFileName
import com.comst19.dambom.feature.library.subtitle.runtime.SubtitleRuntimeEntryPoint
import dagger.hilt.android.EntryPointAccessors

@Composable
@Suppress("LongMethod")
internal fun SubtitleRoute(
    id: String,
    viewModel: SubtitleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val delivery =
        remember(context) {
            EntryPointAccessors.fromApplication(context, SubtitleRuntimeEntryPoint::class.java).delivery()
        }
    val confirmation by delivery.confirmation.collectAsStateWithLifecycle()
    val confirmationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    LaunchedEffect(confirmation) {
        confirmation?.let { sender ->
            delivery.confirmationLaunched()
            confirmationLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
            picking = false
            if (uri != null) viewModel.export(uri)
        }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.generate()
        }
    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(state.busy) { if (!state.busy) confirmCancel = false }
    val blocking = state.busy && state.operation != SubtitleOperation.GENERATING
    BackHandler(blocking) { confirmCancel = true }
    SubtitleScreen(
        state = state,
        onBack = { if (blocking) confirmCancel = true else viewModel.goBack() },
        onSelect = viewModel::select,
        onDownload = viewModel::download,
        onDeleteModel = { confirmDelete = true },
        onRefresh = viewModel::refresh,
        onGenerate = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.generate()
            }
        },
        onCancel = { confirmCancel = true },
        onExport = {
            if (!picking) {
                picking = true
                launcher.launch("${state.task?.suggestedFileName()?.substringBeforeLast('.').orEmpty()}.srt")
            }
        },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.subtitle_delete_model_description)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteModel()
                }) { Text(stringResource(R.string.subtitle_delete_model)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.trim_continue)) }
            },
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            text = { Text(stringResource(R.string.subtitle_cancel_description)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancel()
                }) {
                    Text(stringResource(R.string.subtitle_cancel))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.trim_continue)) }
            },
        )
    }
}
