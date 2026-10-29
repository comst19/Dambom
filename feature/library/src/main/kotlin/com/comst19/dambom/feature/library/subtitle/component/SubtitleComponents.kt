@file:Suppress("MagicNumber")

package com.comst19.dambom.feature.library.subtitle.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.format.formatFileSize
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.subtitle.SubtitleOperation
import com.comst19.dambom.feature.library.subtitle.SubtitleState
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleModelStatus
import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadProgress
import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadStage
import com.comst19.dambom.feature.library.subtitle.model.SubtitleFailure
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair

@Composable
internal fun SubtitleLanguageSelector(
    selected: SubtitleLanguagePair,
    enabled: Boolean,
    onSelect: (SubtitleLanguagePair) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.selectableGroup().padding(12.dp)) {
            Text(
                stringResource(R.string.subtitle_language_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(4.dp),
            )
            SubtitleLanguagePair.entries.forEach { pair ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected == pair,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(pair) },
                        ).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = selected == pair, onClick = null, enabled = enabled)
                    Text(stringResource(pair.labelResource()))
                }
            }
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun SubtitleProgress(
    state: SubtitleState,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val progress = state.modelDownload?.progress ?: ModelDownloadProgress()
        val fraction = progress.fraction
        if (state.operation == SubtitleOperation.DOWNLOADING && fraction != null &&
            progress.stage == ModelDownloadStage.DOWNLOADING
        ) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val message =
            when (state.operation) {
                SubtitleOperation.DOWNLOADING -> {
                    when (progress.stage) {
                        ModelDownloadStage.PREPARING -> R.string.subtitle_model_preparing
                        ModelDownloadStage.DOWNLOADING -> R.string.subtitle_downloading
                        ModelDownloadStage.VERIFYING -> R.string.subtitle_model_verifying
                    }
                }

                SubtitleOperation.EXPORTING -> {
                    R.string.subtitle_exporting
                }

                SubtitleOperation.DELETING -> {
                    R.string.subtitle_deleting_model
                }

                else -> {
                    R.string.subtitle_generating
                }
            }
        Text(stringResource(message))
        if (state.operation == SubtitleOperation.DOWNLOADING) {
            if (progress.totalBytes > 0) {
                Text(
                    stringResource(
                        R.string.subtitle_model_bytes,
                        progress.bytes.formatFileSize(),
                        progress.totalBytes.formatFileSize(),
                        ((fraction ?: 0f) * 100).toInt(),
                    ),
                )
            }
            if (progress.stage == ModelDownloadStage.DOWNLOADING) {
                progress.remainingSeconds?.let { seconds ->
                    Text(
                        stringResource(R.string.subtitle_model_remaining, seconds / 60, seconds % 60),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stringResource(R.string.subtitle_download_background_hint), style = MaterialTheme.typography.bodySmall)
        } else if (state.operation == SubtitleOperation.GENERATING) {
            Text(stringResource(R.string.subtitle_processed, state.processedMillis / 1_000))
            Text(stringResource(R.string.subtitle_background_hint))
        }
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.subtitle_cancel)) }
    }
}

@Composable
internal fun SubtitleResult(
    state: SubtitleState,
    onExport: () -> Unit,
    onView: () -> Unit,
) {
    if (state.cues.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.subtitle_ready, state.cues.size))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onView, enabled = !state.busy) { Text(stringResource(R.string.subtitle_view)) }
            TextButton(onClick = onExport, enabled = !state.busy && state.operation != SubtitleOperation.CHECKING) {
                Text(stringResource(R.string.subtitle_export))
            }
        }
        if (state.exported) Text(stringResource(R.string.subtitle_exported))
    }
}

@Composable
internal fun SubtitleModelActions(
    state: SubtitleState,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onGenerate: () -> Unit,
) {
    when (state.model) {
        SubtitleModelStatus.AVAILABLE -> {
            Button(onClick = onGenerate, enabled = state.task != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.subtitle_generate))
            }
        }

        SubtitleModelStatus.DOWNLOADABLE, SubtitleModelStatus.PREPARATION_REQUIRED -> {
            val preparing = state.model == SubtitleModelStatus.PREPARATION_REQUIRED
            val description =
                if (preparing) {
                    R.string.subtitle_prepare_description
                } else {
                    R.string.subtitle_download_consent
                }
            Text(stringResource(description))
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (preparing) R.string.subtitle_prepare else R.string.subtitle_download))
            }
        }

        SubtitleModelStatus.DOWNLOADING -> {
            Text(stringResource(R.string.subtitle_system_downloading))
            OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.subtitle_refresh)) }
        }

        else -> {
            Text(
                stringResource(
                    if (state.model == SubtitleModelStatus.FAILED) {
                        R.string.subtitle_check_failed
                    } else {
                        R.string.subtitle_unavailable
                    },
                ),
            )
            OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.subtitle_refresh)) }
        }
    }
}

private fun SubtitleLanguagePair.labelResource(): Int =
    when (this) {
        SubtitleLanguagePair.ENGLISH_KOREAN -> R.string.subtitle_en_ko
        SubtitleLanguagePair.JAPANESE_KOREAN -> R.string.subtitle_ja_ko
        SubtitleLanguagePair.JAPANESE_ENGLISH -> R.string.subtitle_ja_en
    }

@Composable
internal fun SubtitleModelManagement(
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.subtitle_model_ready), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.subtitle_model_on_device),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.subtitle_model_manage))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.subtitle_update_model)) },
                        onClick = {
                            expanded = false
                            onUpdate()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.subtitle_delete_model),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SubtitleError(state: SubtitleState) {
    val error =
        when {
            state.speechPermissionDenied -> {
                R.string.subtitle_speech_permission
            }

            state.modelDownload?.failed == true -> {
                R.string.subtitle_model_failed
            }

            else -> {
                when (state.generation?.failure) {
                    SubtitleFailure.SPEECH_PERMISSION -> R.string.subtitle_speech_permission
                    SubtitleFailure.NO_SPEECH -> R.string.subtitle_no_speech
                    SubtitleFailure.SPEECH -> R.string.subtitle_speech_failed
                    SubtitleFailure.AUDIO -> R.string.subtitle_no_audio
                    else -> R.string.subtitle_failed
                }
            }
        }
    Text(stringResource(error), color = MaterialTheme.colorScheme.error)
}
