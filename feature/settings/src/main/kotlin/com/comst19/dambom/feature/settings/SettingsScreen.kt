package com.comst19.dambom.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.feature.settings.component.APP_LANGUAGE_ENTRIES
import com.comst19.dambom.feature.settings.component.DownloadSettingsActions
import com.comst19.dambom.feature.settings.component.GeneralSettingsActions
import com.comst19.dambom.feature.settings.component.SettingsActions
import com.comst19.dambom.feature.settings.component.SettingsChoiceDialog
import com.comst19.dambom.feature.settings.component.SettingsContent
import com.comst19.dambom.feature.settings.component.SettingsContentState
import com.comst19.dambom.feature.settings.component.SupportSettingsActions
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
        state =
            SettingsContentState(
                themeMode = settings.themeMode,
                language = language,
                clipboardSuggestionEnabled = settings.clipboardSuggestionEnabled,
                wifiOnlyDownloads = settings.wifiOnlyDownloads,
                versionName = viewModel.versionName,
            ),
        actions =
            SettingsActions(
                onBack = viewModel::goBack,
                download = DownloadSettingsActions(viewModel::setWifiOnlyDownloads),
                general =
                    GeneralSettingsActions(
                        onThemeModeChange = viewModel::setThemeMode,
                        onLanguageChange = viewModel::setLanguage,
                        onClipboardSuggestionChange = viewModel::setClipboardSuggestion,
                    ),
                support =
                    SupportSettingsActions(
                        onHelp = viewModel::openHelp,
                        onFeedback = { context.sendFeedback(feedbackTitle, feedbackBody, feedbackFailure) },
                        onLicenses = { context.openSourceLicenses(licensesTitle) },
                    ),
            ),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsScreen(
    state: SettingsContentState,
    actions: SettingsActions,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    AppScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SettingsContent(
            state = state,
            actions = actions,
            onThemeClick = { showThemeDialog = true },
            onLanguageClick = { showLanguageDialog = true },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    if (showThemeDialog) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_theme),
            entries = THEME_MODE_ENTRIES,
            selected = state.themeMode,
            label = { stringResource(it.labelRes) },
            onDismiss = { showThemeDialog = false },
            onSelect = {
                showThemeDialog = false
                actions.general.onThemeModeChange(it)
            },
        )
    }
    if (showLanguageDialog) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_language),
            entries = APP_LANGUAGE_ENTRIES,
            selected = state.language,
            label = { stringResource(it.labelRes) },
            onDismiss = { showLanguageDialog = false },
            onSelect = {
                showLanguageDialog = false
                actions.general.onLanguageChange(it)
            },
        )
    }
}

@FormFactorPreviews
@Composable
private fun SettingsScreenPreview() {
    DambomTheme {
        SettingsScreen(
            state =
                SettingsContentState(
                    themeMode = ThemeMode.SYSTEM,
                    language = AppLanguage.SYSTEM,
                    clipboardSuggestionEnabled = true,
                    wifiOnlyDownloads = false,
                    versionName = "1.0",
                ),
            actions =
                SettingsActions(
                    onBack = {},
                    download = DownloadSettingsActions(onWifiOnlyDownloadsChange = {}),
                    general =
                        GeneralSettingsActions(
                            onThemeModeChange = {},
                            onLanguageChange = {},
                            onClipboardSuggestionChange = {},
                        ),
                    support = SupportSettingsActions(onHelp = {}, onFeedback = {}, onLicenses = {}),
                ),
        )
    }
}
