package com.comst19.dambom.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomDarkColorScheme
import com.comst19.dambom.core.designsystem.DambomLightColorScheme
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.DambomTypography
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.domain.model.ThemeMode
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity

@Composable
internal fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val feedbackTitle = stringResource(R.string.settings_feedback_chooser)
    val feedbackBody = stringResource(R.string.settings_feedback_body, viewModel.versionName)
    val feedbackFailure = stringResource(R.string.settings_external_action_failure)
    val licensesTitle = stringResource(R.string.settings_open_source)

    SettingsScreen(
        themeMode = settings.themeMode,
        language = language,
        clipboardSuggestionEnabled = settings.clipboardSuggestionEnabled,
        versionName = viewModel.versionName,
        onBack = viewModel::goBack,
        onThemeModeChange = viewModel::setThemeMode,
        onLanguageChange = viewModel::setLanguage,
        onClipboardSuggestionChange = viewModel::setClipboardSuggestion,
        onHelp = viewModel::openHelp,
        onFeedback = { context.sendFeedback(feedbackTitle, feedbackBody, feedbackFailure) },
        onLicenses = { context.openSourceLicenses(licensesTitle) },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreen(
    themeMode: ThemeMode,
    language: AppLanguage,
    clipboardSuggestionEnabled: Boolean,
    versionName: String,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onClipboardSuggestionChange: (Boolean) -> Unit,
    onHelp: () -> Unit,
    onFeedback: () -> Unit,
    onLicenses: () -> Unit,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    AppScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { SettingsSectionTitle(stringResource(R.string.settings_section_download)) }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.settings_download_location),
                    subtitle = stringResource(R.string.settings_download_location_value),
                )
            }
            item { SettingsDivider() }
            item { SettingsSectionTitle(stringResource(R.string.settings_section_general)) }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_theme),
                    subtitle = stringResource(themeMode.labelRes),
                    onClick = { showThemeDialog = true },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(language.labelRes),
                    onClick = { showLanguageDialog = true },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.ContentPaste,
                    title = stringResource(R.string.settings_clipboard_suggestion),
                    subtitle = stringResource(R.string.settings_clipboard_suggestion_description),
                    trailing = {
                        Switch(
                            checked = clipboardSuggestionEnabled,
                            onCheckedChange = onClipboardSuggestionChange,
                        )
                    },
                )
            }
            item { SettingsDivider() }
            item { SettingsSectionTitle(stringResource(R.string.settings_section_support)) }
            item {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = stringResource(R.string.settings_help),
                    subtitle = stringResource(R.string.settings_help_description),
                    onClick = onHelp,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Feedback,
                    title = stringResource(R.string.settings_feedback),
                    subtitle = stringResource(R.string.settings_feedback_description),
                    onClick = onFeedback,
                )
            }
            item { SettingsDivider() }
            item { SettingsSectionTitle(stringResource(R.string.settings_section_information)) }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_version),
                    subtitle = versionName,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Code,
                    title = stringResource(R.string.settings_open_source),
                    subtitle = stringResource(R.string.settings_open_source_description),
                    onClick = onLicenses,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Gavel,
                    title = stringResource(R.string.settings_legal),
                    subtitle = stringResource(R.string.settings_legal_pending),
                    enabled = false,
                )
            }
        }
    }

    if (showThemeDialog) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_theme),
            entries = ThemeMode.entries,
            selected = themeMode,
            label = { stringResource(it.labelRes) },
            onDismiss = { showThemeDialog = false },
            onSelect = {
                showThemeDialog = false
                onThemeModeChange(it)
            },
        )
    }
    if (showLanguageDialog) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_language),
            entries = AppLanguage.entries,
            selected = language,
            label = { stringResource(it.labelRes) },
            onDismiss = { showLanguageDialog = false },
            onSelect = {
                showLanguageDialog = false
                onLanguageChange(it)
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsRow(
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
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
}

@Composable
private fun <T> SettingsChoiceDialog(
    title: String,
    entries: List<T>,
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

private fun Context.sendFeedback(
    chooserTitle: String,
    body: String,
    failureMessage: String,
) {
    val intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
                .putExtra(Intent.EXTRA_TEXT, body),
            chooserTitle,
        )
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

private fun Context.openSourceLicenses(title: String) {
    OssLicensesMenuActivity.setActivityTitle(title)
    OssLicensesMenuActivity.setTheme(DambomLightColorScheme, DambomDarkColorScheme, DambomTypography)
    startActivity(Intent(this, OssLicensesMenuActivity::class.java))
}

private val ThemeMode.labelRes: Int
    get() =
        when (this) {
            ThemeMode.SYSTEM -> R.string.theme_mode_system
            ThemeMode.LIGHT -> R.string.theme_mode_light
            ThemeMode.DARK -> R.string.theme_mode_dark
        }

private val AppLanguage.labelRes: Int
    get() =
        when (this) {
            AppLanguage.SYSTEM -> R.string.language_system
            AppLanguage.KOREAN -> R.string.language_korean
            AppLanguage.ENGLISH -> R.string.language_english
        }

@FormFactorPreviews
@Composable
private fun SettingsScreenPreview() {
    DambomTheme {
        SettingsScreen(
            themeMode = ThemeMode.SYSTEM,
            language = AppLanguage.SYSTEM,
            clipboardSuggestionEnabled = true,
            versionName = "1.0",
            onBack = {},
            onThemeModeChange = {},
            onLanguageChange = {},
            onClipboardSuggestionChange = {},
            onHelp = {},
            onFeedback = {},
            onLicenses = {},
        )
    }
}
