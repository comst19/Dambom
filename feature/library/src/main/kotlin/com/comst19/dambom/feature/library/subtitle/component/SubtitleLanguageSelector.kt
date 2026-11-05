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
internal fun SubtitleLanguageSelector(
    selected: SubtitleLanguagePair,
    enabled: Boolean,
    onSelect: (SubtitleLanguagePair) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SubtitleLanguageField(
                R.string.subtitle_source_language,
                selected.source,
                SubtitleLanguagePair.sourceLanguages,
                enabled,
            ) { onSelect(selected.copy(source = it)) }
            SubtitleLanguageField(
                R.string.subtitle_target_language,
                selected.target,
                SubtitleLanguagePair.targetLanguages,
                enabled,
            ) { onSelect(selected.copy(target = it)) }
            Text(
                stringResource(R.string.subtitle_language_model_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubtitleLanguageField(
    label: Int,
    selected: String,
    languages: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]

    fun labelFor(tag: String): String =
        Locale
            .forLanguageTag(tag)
            .getDisplayName(locale)
    OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(label) + ": " + labelFor(selected))
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(stringResource(label)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp).testTag("subtitle-language-options")) {
                    items(languages.size, key = { languages[it] }) { index ->
                        val language = languages[index]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = language == selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        expanded = false
                                        onSelect(language)
                                    },
                                ).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = language == selected, onClick = null)
                            Text(labelFor(language), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.subtitle_cancel)) }
            },
        )
    }
}
