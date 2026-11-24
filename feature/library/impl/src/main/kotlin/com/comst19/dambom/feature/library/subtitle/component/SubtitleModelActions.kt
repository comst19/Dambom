@file:Suppress("MagicNumber")

package com.comst19.dambom.feature.library.subtitle.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
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
import java.util.Locale

@Composable
internal fun SubtitleModelActions(
    state: SubtitleState,
    onDownload: () -> Unit,
    onRefresh: () -> Unit,
    onGenerate: () -> Unit,
) {
    when (state.model) {
        SubtitleModelStatus.AVAILABLE -> {
            if (state.cues.isEmpty()) {
                Button(onClick = onGenerate, enabled = state.task != null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.subtitle_generate))
                }
            } else {
                OutlinedButton(onClick = onGenerate, enabled = state.task != null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.subtitle_regenerate))
                }
                Text(
                    stringResource(R.string.subtitle_regenerate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SubtitleModelStatus.DOWNLOADABLE,
        SubtitleModelStatus.PREPARATION_REQUIRED,
        SubtitleModelStatus.LANGUAGE_DOWNLOAD_REQUIRED,
        -> {
            val preparing = state.model == SubtitleModelStatus.PREPARATION_REQUIRED
            val languageDownload = state.model == SubtitleModelStatus.LANGUAGE_DOWNLOAD_REQUIRED
            val description =
                when {
                    languageDownload -> R.string.subtitle_language_download_hint
                    preparing -> R.string.subtitle_prepare_description
                    else -> R.string.subtitle_download_consent
                }
            Text(stringResource(description))
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                val action =
                    when {
                        languageDownload -> R.string.subtitle_language_download
                        preparing -> R.string.subtitle_prepare
                        else -> R.string.subtitle_download
                    }
                Text(stringResource(action))
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
