package com.comst19.dambom.feature.settings

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.comst19.dambom.core.common.ui.AppScreenDefaults
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.designsystem.previewNoOp
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
import com.comst19.dambom.feature.settings.component.openExternalPage
import com.comst19.dambom.feature.settings.component.openSourceLicenses
import com.comst19.dambom.feature.settings.component.sendSupportEmail
import com.comst19.dambom.feature.settings.contract.AppLanguage

private const val TERMS_OF_SERVICE_URL =
    "https://waiting-manchego-38d.notion.site/3cbeb6bb8cd580a0a03fc0287ed2420f"
private const val PRIVACY_POLICY_URL =
    "https://waiting-manchego-38d.notion.site/3cbeb6bb8cd580ab8b02cb81694a4288"

@Composable
internal fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val bugReportSubject = stringResource(R.string.settings_bug_report_subject)
    val bugReportBody =
        stringResource(
            R.string.settings_bug_report_body,
            viewModel.versionName,
            Build.VERSION.RELEASE,
            Build.MODEL,
        )
    val featureRequestSubject = stringResource(R.string.settings_feature_request_subject)
    val featureRequestBody = stringResource(R.string.settings_feature_request_body, viewModel.versionName)
    val externalActionFailure = stringResource(R.string.settings_external_action_failure)
    val licensesTitle = stringResource(R.string.settings_open_source)
    val defaultDownloadLocation = stringResource(R.string.settings_download_location_value)
    val downloadDirectoryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(viewModel::setDownloadDirectory)
        }

    SettingsScreen(
        state =
            SettingsContentState(
                themeMode = settings.themeMode,
                language = language,
                clipboardSuggestionEnabled = settings.clipboardSuggestionEnabled,
                wifiOnlyDownloads = settings.wifiOnlyDownloads,
                useConfiguredDownloadLocation = settings.useConfiguredDownloadLocation,
                downloadLocation = downloadLocationLabel(settings.downloadTreeUri, defaultDownloadLocation),
                versionName = viewModel.versionName,
            ),
        actions =
            SettingsActions(
                onBack = viewModel::goBack,
                download =
                    DownloadSettingsActions(
                        onUseConfiguredDownloadLocationChange = viewModel::setUseConfiguredDownloadLocation,
                        onDownloadLocationClick = {
                            downloadDirectoryLauncher.launch(settings.downloadTreeUri?.let(Uri::parse))
                        },
                        onWifiOnlyDownloadsChange = viewModel::setWifiOnlyDownloads,
                    ),
                general =
                    GeneralSettingsActions(
                        onThemeModeChange = viewModel::setThemeMode,
                        onLanguageChange = viewModel::setLanguage,
                        onClipboardSuggestionChange = viewModel::setClipboardSuggestion,
                    ),
                support =
                    SupportSettingsActions(
                        onHelp = viewModel::openHelp,
                        onBugReport = {
                            context.sendSupportEmail(bugReportSubject, bugReportBody, externalActionFailure)
                        },
                        onFeatureRequest = {
                            context.sendSupportEmail(featureRequestSubject, featureRequestBody, externalActionFailure)
                        },
                        onLicenses = { context.openSourceLicenses(licensesTitle) },
                        onTerms = { context.openExternalPage(TERMS_OF_SERVICE_URL, externalActionFailure) },
                        onPrivacy = { context.openExternalPage(PRIVACY_POLICY_URL, externalActionFailure) },
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
        maxWidth = AppScreenDefaults.SinglePaneMaxWidth,
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
                    useConfiguredDownloadLocation = true,
                    downloadLocation = "Download/Dambom",
                    versionName = "1.0.0",
                ),
            actions =
                SettingsActions(
                    onBack = ::previewNoOp,
                    download =
                        DownloadSettingsActions(
                            onUseConfiguredDownloadLocationChange = ::previewNoOp,
                            onDownloadLocationClick = ::previewNoOp,
                            onWifiOnlyDownloadsChange = ::previewNoOp,
                        ),
                    general =
                        GeneralSettingsActions(
                            onThemeModeChange = ::previewNoOp,
                            onLanguageChange = ::previewNoOp,
                            onClipboardSuggestionChange = ::previewNoOp,
                        ),
                    support =
                        SupportSettingsActions(
                            onHelp = ::previewNoOp,
                            onBugReport = ::previewNoOp,
                            onFeatureRequest = ::previewNoOp,
                            onLicenses = ::previewNoOp,
                            onTerms = ::previewNoOp,
                            onPrivacy = ::previewNoOp,
                        ),
                ),
        )
    }
}

internal fun downloadLocationLabel(
    treeUri: String?,
    defaultLabel: String,
): String {
    if (treeUri == null) return defaultLabel
    val documentId =
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)) }
            .getOrNull()
            ?: return defaultLabel
    return Uri.decode(documentId.substringAfter(':', documentId)).ifBlank { defaultLabel }
}
