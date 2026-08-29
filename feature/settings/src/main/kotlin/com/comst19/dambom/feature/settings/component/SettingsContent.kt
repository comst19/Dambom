package com.comst19.dambom.feature.settings.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.feature.settings.R
import com.comst19.dambom.feature.settings.contract.AppLanguage

@Immutable
internal data class SettingsContentState(
    val themeMode: ThemeMode,
    val language: AppLanguage,
    val clipboardSuggestionEnabled: Boolean,
    val wifiOnlyDownloads: Boolean,
    val useConfiguredDownloadLocation: Boolean,
    val downloadLocation: String,
    val versionName: String,
)

@Immutable
internal data class SettingsActions(
    val onBack: () -> Unit,
    val download: DownloadSettingsActions,
    val general: GeneralSettingsActions,
    val support: SupportSettingsActions,
)

@Immutable
internal data class DownloadSettingsActions(
    val onUseConfiguredDownloadLocationChange: (Boolean) -> Unit,
    val onDownloadLocationClick: () -> Unit,
    val onWifiOnlyDownloadsChange: (Boolean) -> Unit,
)

@Immutable
internal data class GeneralSettingsActions(
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onLanguageChange: (AppLanguage) -> Unit,
    val onClipboardSuggestionChange: (Boolean) -> Unit,
)

@Immutable
internal data class SupportSettingsActions(
    val onHelp: () -> Unit,
    val onBugReport: () -> Unit,
    val onFeatureRequest: () -> Unit,
    val onLicenses: () -> Unit,
    val onTerms: () -> Unit,
    val onPrivacy: () -> Unit,
)

@Composable
internal fun SettingsContent(
    state: SettingsContentState,
    actions: SettingsActions,
    onThemeClick: () -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        downloadSection(state, actions)
        item { SettingsDivider() }
        generalSection(state, actions, onThemeClick, onLanguageClick)
        item { SettingsDivider() }
        supportSection(actions)
        item { SettingsDivider() }
        informationSection(state, actions)
    }
}

private fun LazyListScope.downloadSection(
    state: SettingsContentState,
    actions: SettingsActions,
) {
    item { SettingsSectionTitle(stringResource(R.string.settings_section_download)) }
    item {
        SettingsRow(
            icon = Icons.Outlined.Folder,
            title = stringResource(R.string.settings_use_download_location),
            subtitle =
                stringResource(
                    if (state.useConfiguredDownloadLocation) {
                        R.string.settings_use_download_location_enabled_description
                    } else {
                        R.string.settings_use_download_location_disabled_description
                    },
                ),
            onClick = {
                actions.download.onUseConfiguredDownloadLocationChange(!state.useConfiguredDownloadLocation)
            },
            trailing = {
                Switch(
                    checked = state.useConfiguredDownloadLocation,
                    onCheckedChange = actions.download.onUseConfiguredDownloadLocationChange,
                )
            },
        )
    }
    item {
        SettingsRow(
            icon = Icons.Outlined.FolderOpen,
            title = stringResource(R.string.settings_download_location),
            subtitle = state.downloadLocation,
            enabled = state.useConfiguredDownloadLocation,
            onClick = actions.download.onDownloadLocationClick,
        )
    }
    item {
        SettingsRow(
            icon = Icons.Outlined.Wifi,
            title = stringResource(R.string.settings_wifi_only),
            subtitle = stringResource(R.string.settings_wifi_only_description),
            onClick = { actions.download.onWifiOnlyDownloadsChange(!state.wifiOnlyDownloads) },
            trailing = { Switch(checked = state.wifiOnlyDownloads, onCheckedChange = null) },
        )
    }
}

private fun LazyListScope.generalSection(
    state: SettingsContentState,
    actions: SettingsActions,
    onThemeClick: () -> Unit,
    onLanguageClick: () -> Unit,
) {
    item { SettingsSectionTitle(stringResource(R.string.settings_section_general)) }
    item {
        SettingsRow(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.settings_theme),
            subtitle = stringResource(state.themeMode.labelRes),
            onClick = onThemeClick,
        )
    }
    item {
        SettingsRow(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.settings_language),
            subtitle = stringResource(state.language.labelRes),
            onClick = onLanguageClick,
        )
    }
    item {
        SettingsRow(
            icon = Icons.Outlined.ContentPaste,
            title = stringResource(R.string.settings_clipboard_suggestion),
            subtitle = stringResource(R.string.settings_clipboard_suggestion_description),
            trailing = {
                Switch(
                    checked = state.clipboardSuggestionEnabled,
                    onCheckedChange = actions.general.onClipboardSuggestionChange,
                )
            },
        )
    }
}

private fun LazyListScope.supportSection(actions: SettingsActions) {
    item { SettingsSectionTitle(stringResource(R.string.settings_section_support)) }
    item {
        SettingsRow(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            title = stringResource(R.string.settings_help),
            subtitle = stringResource(R.string.settings_help_description),
            onClick = actions.support.onHelp,
        )
    }
    item {
        SettingsRow(
            icon = Icons.Outlined.BugReport,
            title = stringResource(R.string.settings_bug_report),
            subtitle = stringResource(R.string.settings_bug_report_description),
            onClick = actions.support.onBugReport,
        )
    }
    item {
        SettingsRow(
            icon = Icons.Outlined.Lightbulb,
            title = stringResource(R.string.settings_feature_request),
            subtitle = stringResource(R.string.settings_feature_request_description),
            onClick = actions.support.onFeatureRequest,
        )
    }
}

private fun LazyListScope.informationSection(
    state: SettingsContentState,
    actions: SettingsActions,
) {
    item { SettingsSectionTitle(stringResource(R.string.settings_section_information)) }
    item {
        SettingsRow(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.settings_version),
            subtitle = state.versionName,
        )
    }
    item {
        SettingsRow(
            icon = Icons.Outlined.Description,
            title = stringResource(R.string.settings_terms),
            subtitle = stringResource(R.string.settings_terms_description),
            onClick = actions.support.onTerms,
        )
    }
    item {
        SettingsRow(
            icon = Icons.Outlined.PrivacyTip,
            title = stringResource(R.string.settings_privacy),
            subtitle = stringResource(R.string.settings_privacy_description),
            onClick = actions.support.onPrivacy,
        )
    }
    item {
        SettingsRow(
            icon = Icons.Outlined.Code,
            title = stringResource(R.string.settings_open_source),
            subtitle = stringResource(R.string.settings_open_source_description),
            onClick = actions.support.onLicenses,
        )
    }
}
