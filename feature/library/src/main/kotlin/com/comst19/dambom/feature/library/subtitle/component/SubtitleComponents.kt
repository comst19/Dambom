@file:Suppress("MagicNumber")

package com.comst19.dambom.feature.library.subtitle.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair

@Composable
internal fun SubtitleLanguageSelector(
    selected: SubtitleLanguagePair,
    enabled: Boolean,
    onSelect: (SubtitleLanguagePair) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        SubtitleLanguagePair.entries.forEach { pair ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == pair,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(pair) },
                    ).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = selected == pair, onClick = null, enabled = enabled)
                Text(stringResource(pair.labelResource()))
            }
        }
    }
}

@Composable
internal fun SubtitleProgress(
    state: SubtitleState,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        val message =
            when (state.operation) {
                SubtitleOperation.DOWNLOADING -> R.string.subtitle_downloading
                SubtitleOperation.EXPORTING -> R.string.subtitle_exporting
                SubtitleOperation.DELETING -> R.string.subtitle_deleting_model
                else -> R.string.subtitle_generating
            }
        Text(stringResource(message))
        if (state.operation == SubtitleOperation.DOWNLOADING) {
            Text(state.downloadedBytes.formatFileSize())
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
) {
    if (state.cues.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.subtitle_ready, state.cues.size))
        Button(onClick = onExport, enabled = !state.busy && state.operation != SubtitleOperation.CHECKING) {
            Text(stringResource(R.string.subtitle_export))
        }
        if (state.exported) Text(stringResource(R.string.subtitle_exported))
        state.cues.take(PREVIEW_CUE_COUNT).forEach { cue ->
            Text("${cue.startMillis / 1_000}s  ${cue.text}", style = MaterialTheme.typography.bodyMedium)
        }
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
            Button(onClick = onGenerate, enabled = state.task != null) {
                Text(stringResource(R.string.subtitle_generate))
            }
            OutlinedButton(onClick = onDownload) { Text(stringResource(R.string.subtitle_update_model)) }
        }

        SubtitleModelStatus.DOWNLOADABLE -> {
            Text(stringResource(R.string.subtitle_download_consent))
            Button(onClick = onDownload) { Text(stringResource(R.string.subtitle_download)) }
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

private const val PREVIEW_CUE_COUNT = 5
