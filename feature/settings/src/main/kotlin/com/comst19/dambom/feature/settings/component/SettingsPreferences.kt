package com.comst19.dambom.feature.settings.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.feature.settings.R
import com.comst19.dambom.feature.settings.contract.AppLanguage
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
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
                ).padding(horizontal = 24.dp, vertical = 14.dp),
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
            verticalArrangement = Arrangement.spacedBy(3.dp),
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
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
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
            Column {
                entries.forEach { entry ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(entry) }
                                .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = entry == selected,
                            onClick = { onSelect(entry) },
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
        }
