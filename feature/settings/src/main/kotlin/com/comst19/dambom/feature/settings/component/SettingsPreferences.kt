package com.comst19.dambom.feature.settings.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.locale.AppLanguage
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.feature.settings.R
import com.comst19.dambom.feature.settings.contract.SaveLocationMode
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

@Composable
internal fun SettingsSectionTitle(
    title: String,
    topPadding: Dp = 24.dp,
) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, top = topPadding, end = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ).heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        trailing?.invoke()
        if (trailing == null && onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
internal fun <T> SettingsChoiceDialog(
    title: String,
    entries: PersistentList<T>,
    selected: T,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                entries.forEach { entry ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = entry == selected,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(entry) },
                                ).heightIn(min = 56.dp)
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = entry == selected,
                            onClick = null,
                        )
                        Text(label(entry))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

internal val THEME_MODE_ENTRIES = ThemeMode.entries.toPersistentList()
internal val APP_LANGUAGE_ENTRIES = AppLanguage.entries.toPersistentList()
internal val SAVE_LOCATION_MODE_ENTRIES = SaveLocationMode.entries.toPersistentList()

internal val ThemeMode.labelRes: Int
    get() =
        when (this) {
            ThemeMode.SYSTEM -> R.string.theme_mode_system
            ThemeMode.LIGHT -> R.string.theme_mode_light
            ThemeMode.DARK -> R.string.theme_mode_dark
        }

internal val AppLanguage.labelRes: Int
    get() =
        when (this) {
            AppLanguage.SYSTEM -> R.string.language_system
            AppLanguage.KOREAN -> R.string.language_korean
            AppLanguage.ENGLISH -> R.string.language_english
            AppLanguage.URDU -> R.string.language_urdu
            AppLanguage.HINDI -> R.string.language_hindi
            AppLanguage.FRENCH -> R.string.language_french
            AppLanguage.PORTUGUESE -> R.string.language_portuguese
            AppLanguage.BENGALI -> R.string.language_bengali
            AppLanguage.ARABIC -> R.string.language_arabic
            AppLanguage.PASHTO -> R.string.language_pashto
            AppLanguage.AMHARIC -> R.string.language_amharic
            AppLanguage.SWAHILI -> R.string.language_swahili
            AppLanguage.GERMAN -> R.string.language_german
            AppLanguage.INDONESIAN -> R.string.language_indonesian
            AppLanguage.HEBREW -> R.string.language_hebrew
            AppLanguage.PERSIAN -> R.string.language_persian
            AppLanguage.BURMESE -> R.string.language_burmese
            AppLanguage.PUNJABI -> R.string.language_punjabi
            AppLanguage.SINDHI -> R.string.language_sindhi
            AppLanguage.TAMIL -> R.string.language_tamil
            AppLanguage.TELUGU -> R.string.language_telugu
            AppLanguage.MARATHI -> R.string.language_marathi
            AppLanguage.GUJARATI -> R.string.language_gujarati
            AppLanguage.KANNADA -> R.string.language_kannada
            AppLanguage.MALAYALAM -> R.string.language_malayalam
            AppLanguage.ODIA -> R.string.language_odia
            AppLanguage.ASSAMESE -> R.string.language_assamese
            AppLanguage.HAUSA -> R.string.language_hausa
            AppLanguage.YORUBA -> R.string.language_yoruba
            AppLanguage.IGBO -> R.string.language_igbo
            AppLanguage.TIGRINYA -> R.string.language_tigrinya
            AppLanguage.SOMALI -> R.string.language_somali
            AppLanguage.LINGALA -> R.string.language_lingala
            AppLanguage.UZBEK -> R.string.language_uzbek
            AppLanguage.TURKMEN -> R.string.language_turkmen
            AppLanguage.AZERBAIJANI -> R.string.language_azerbaijani
            AppLanguage.KURDISH -> R.string.language_kurdish
            AppLanguage.CENTRAL_KURDISH -> R.string.language_central_kurdish
            AppLanguage.AFRIKAANS -> R.string.language_afrikaans
            AppLanguage.ZULU -> R.string.language_zulu
            AppLanguage.XHOSA -> R.string.language_xhosa
            AppLanguage.SOUTHERN_SOTHO -> R.string.language_southern_sotho
            AppLanguage.RUSSIAN -> R.string.language_russian
        }

internal val SaveLocationMode.labelRes: Int
    get() =
        when (this) {
            SaveLocationMode.DEFAULT_FOLDER -> R.string.settings_save_method_default_folder
            SaveLocationMode.CHOOSE_EACH_TIME -> R.string.settings_save_method_choose_each_time
        }

@Preview(name = "English", locale = "en", widthDp = 320, heightDp = 640)
@Preview(name = "Arabic", locale = "ar", widthDp = 320, heightDp = 640)
@Preview(name = "French", locale = "fr", widthDp = 320, heightDp = 640)
@Composable
private fun LanguageChoiceDialogPreview() {
    DambomTheme {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_language),
            entries = APP_LANGUAGE_ENTRIES,
            selected = AppLanguage.SYSTEM,
            label = { stringResource(it.labelRes) },
            onDismiss = {},
            onSelect = {},
        )
    }
}
