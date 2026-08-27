package com.comst19.dambom.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.feature.settings.component.APP_LANGUAGE_ENTRIES
import com.comst19.dambom.feature.settings.component.SettingsChoiceDialog
import com.comst19.dambom.feature.settings.component.SettingsDivider
import com.comst19.dambom.feature.settings.component.SettingsRow
import com.comst19.dambom.feature.settings.component.SettingsSectionTitle
import com.comst19.dambom.feature.settings.component.THEME_MODE_ENTRIES
import com.comst19.dambom.feature.settings.component.labelRes
import com.comst19.dambom.feature.settings.component.openSourceLicenses
import com.comst19.dambom.feature.settings.component.sendFeedback
import com.comst19.dambom.feature.settings.contract.AppLanguage

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
        wifiOnlyDownloads = settings.wifiOnlyDownloads,
        versionName = viewModel.versionName,
        onBack = viewModel::goBack,
        onThemeModeChange = viewModel::setThemeMode,
        onLanguageChange = viewModel::setLanguage,
        onClipboardSuggestionChange = viewModel::setClipboardSuggestion,
        onWifiOnlyDownloadsChange = viewModel::setWifiOnlyDownloads,
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
    wifiOnlyDownloads: Boolean,
    versionName: String,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onClipboardSuggestionChange: (Boolean) -> Unit,
    onWifiOnlyDownloadsChange: (Boolean) -> Unit,
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
            item {
                SettingsRow(
                    icon = Icons.Outlined.Wifi,
                    title = stringResource(R.string.settings_wifi_only),
                    subtitle = stringResource(R.string.settings_wifi_only_description),
                    onClick = { onWifiOnlyDownloadsChange(!wifiOnlyDownloads) },
                    trailing = {
                        Switch(
                            checked = wifiOnlyDownloads,
                            onCheckedChange = null,
                        )
                    },
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
            entries = THEME_MODE_ENTRIES,
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
            entries = APP_LANGUAGE_ENTRIES,
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

@FormFactorPreviews
@Composable
private fun SettingsScreenPreview() {
    DambomTheme {
        SettingsScreen(
            themeMode = ThemeMode.SYSTEM,
            language = AppLanguage.SYSTEM,
            clipboardSuggestionEnabled = true,
            wifiOnlyDownloads = false,
            versionName = "1.0",
            onBack = {},
            onThemeModeChange = {},
            onLanguageChange = {},
            onClipboardSuggestionChange = {},
            onWifiOnlyDownloadsChange = {},
            onHelp = {},
            onFeedback = {},
            onLicenses = {},
        )
    }
}
