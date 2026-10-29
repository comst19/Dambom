package com.comst19.dambom.feature.library.compress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.common.ui.format.formatFileSize
import com.comst19.dambom.core.designsystem.DambomLinearProgressIndicator
import com.comst19.dambom.feature.library.R

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "LongParameterList")
internal fun VideoCompressionScreen(
    state: VideoCompressionState,
    onBack: () -> Unit,
    onSelect: (CompressionProfile) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
) {
    AppScreen(
        maxWidth = 720.dp,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compress_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.compress_back))
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
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                state.loading -> {
                    Text(stringResource(R.string.player_loading))
                }

                state.loadFailed -> {
                    Text(stringResource(R.string.player_load_error))
                }

                else -> {
                    val source = checkNotNull(state.source)
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(state.task?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.compress_original_size, source.bytes.formatFileSize()))
                        Text(stringResource(R.string.compress_choose), style = MaterialTheme.typography.titleMedium)
                        CompressionProfile.entries.forEach { profile ->
                            val settings = source.settings(profile)
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = profile == state.profile,
                                            enabled = !state.exporting,
                                            role = Role.RadioButton,
                                            onClick = { onSelect(profile) },
                                        ).padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(
                                    selected = profile == state.profile,
                                    onClick = null,
                                    enabled = !state.exporting,
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(
                                            when (profile) {
                                                CompressionProfile.SMALL -> R.string.compress_small
                                                CompressionProfile.BALANCED -> R.string.compress_balanced
                                                CompressionProfile.QUALITY -> R.string.compress_quality
                                            },
                                        ),
                                    )
                                    Text(
                                        stringResource(
                                            R.string.compress_estimate,
                                            settings.width,
                                            settings.height,
                                            settings.estimatedBytes.formatFileSize(),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.compress_estimate_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    CompressionActions(state, onSave, onCancel, onShare)
                    Text(stringResource(R.string.compress_original_kept), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CompressionActions(
    state: VideoCompressionState,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.exporting -> {
                Text(stringResource(R.string.compress_exporting))
                DambomLinearProgressIndicator(
                    progress = state.progress?.div(PROGRESS_PERCENT_SCALE),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.trim_cancel_export))
                }
            }

            state.savedUri != null -> {
                Text(stringResource(R.string.compress_saved, state.savedBytes?.formatFileSize().orEmpty()))
                Button(onClick = onShare, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.library_share_video))
                }
            }

            else -> {
                state.failure?.let { failure ->
                    Text(
                        stringResource(
                            if (failure ==
                                CompressionFailure.NOT_SMALLER
                            ) {
                                R.string.compress_not_smaller
                            } else {
                                R.string.compress_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.compress_save))
                }
            }
        }
    }
}

private const val PROGRESS_PERCENT_SCALE = 100f
