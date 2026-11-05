package com.comst19.dambom.feature.library.subtitle.export

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.file.suggestedFileName

@Composable
internal fun SubtitleExportRoute(
    id: String,
    viewModel: SubtitleExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            picking = false
            if (uri != null) viewModel.export(id, uri)
        }
    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(state.exporting) { if (!state.exporting) confirmCancel = false }
    BackHandler(state.exporting) { confirmCancel = true }
    SubtitleExportScreen(
        state,
        onBack = { if (state.exporting) confirmCancel = true else viewModel.goBack() },
        onSave = {
            if (!picking) {
                picking = true
                launcher.launch(
                    state.task
                        ?.suggestedFileName()
                        ?.substringBeforeLast('.')
                        .orEmpty() + "-subtitled.mp4",
                )
            }
        },
        onCancel = { confirmCancel = true },
    )
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.trim_cancel_title)) },
            text = { Text(stringResource(R.string.subtitle_video_cancel_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancel()
                }) {
                    Text(stringResource(R.string.trim_cancel_export))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.trim_continue)) }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SubtitleExportScreen(
    state: SubtitleExportState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AppScreen(maxWidth = 720.dp, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.subtitle_video_export)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.subtitle_back))
            }
        })
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(state.task?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.subtitle_video_hint))
            if (state.loading || state.exporting) {
                val progress = state.progress
                if (progress == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (state.exporting) {
                Text(stringResource(R.string.subtitle_exporting))
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.subtitle_cancel)) }
            } else {
                Button(
                    onClick = onSave,
                    enabled = !state.loading && state.task != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.subtitle_video_export))
                }
            }
            if (state.failed) {
                Text(stringResource(R.string.library_export_failure), color = MaterialTheme.colorScheme.error)
            }
            if (state.saved) Text(stringResource(R.string.library_export_success))
        }
    }
}

@Preview(name = "Subtitle video export", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
private fun SubtitleExportPreview() {
    DambomTheme { SubtitleExportScreen(SubtitleExportState(loading = false), {}, {}, {}) }
}
