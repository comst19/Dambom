package com.comst19.dambom.feature.library.trim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomLinearProgressIndicator
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.trim.component.TrimPreview
import com.comst19.dambom.feature.library.trim.component.TrimRange
import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import com.comst19.dambom.feature.library.trim.contract.VideoTrimState

@Composable
@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "LongParameterList")
internal fun VideoTrimScreen(
    state: VideoTrimState,
    onBack: () -> Unit,
    onSelect: (TrimSelection) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
) {
    var positionMillis by remember(state.task?.id) { mutableLongStateOf(state.selection.startMillis) }
    AppScreen(
        maxWidth = 720.dp,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trim_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.trim_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when {
                state.loading -> {
                    Text(stringResource(R.string.player_loading))
                }

                state.loadFailed -> {
                    Text(stringResource(R.string.player_load_error))
                }

                else -> {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Text(state.task?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        TrimPreview(checkNotNull(state.task?.localFilePath), state.selection, !state.exporting) {
                            positionMillis = it
                        }
                        TrimRange(state, onSelect, positionMillis)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when {
                            state.exporting -> {
                                Text(stringResource(R.string.trim_exporting))
                                DambomLinearProgressIndicator(
                                    progress = state.progress?.div(PROGRESS_PERCENT_SCALE),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedButton(
                                    onClick = onCancel,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.trim_cancel_export))
                                }
                            }

                            state.savedUri != null -> {
                                Text(stringResource(R.string.trim_saved))
                                Button(onClick = onShare, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.library_share_video))
                                }
                            }

                            else -> {
                                if (state.exportFailed) {
                                    Text(
                                        stringResource(R.string.trim_export_failed),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.trim_save))
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.trim_original_kept),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val PROGRESS_PERCENT_SCALE = 100f
