package com.comst19.dambom.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.SettingsGraph.HelpKey
import com.comst19.dambom.feature.settings.contract.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val downloadRepository: DownloadRepository,
        private val navigation: NavigationDispatcher,
        private val appEventBus: AppEventBus,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            repository.settings.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SETTINGS_STOP_TIMEOUT_MILLIS),
                initialValue = AppSettings(),
            )
        val language = MutableStateFlow(AppLanguage.from(AppCompatDelegate.getApplicationLocales().toLanguageTags()))
        val versionName: String =
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
                .ifBlank { "-" }

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { repository.setThemeMode(mode) }
        }

        fun setLanguage(language: AppLanguage) {
            this.language.value = language
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag))
        }

        fun setClipboardSuggestion(enabled: Boolean) {
            viewModelScope.launch {
                repository.setClipboardSuggestion(promptShown = true, enabled = enabled)
            }
        }

        fun setWifiOnlyDownloads(enabled: Boolean) {
            viewModelScope.launch {
                repository.setWifiOnlyDownloads(enabled)
                downloadRepository.refreshNetworkPolicy()
            }
        }

        fun setUseConfiguredDownloadLocation(enabled: Boolean) {
            viewModelScope.launch {
                repository.setDownloadLocation(enabled, settings.value.downloadTreeUri)
            }
        }

        fun setDownloadDirectory(uri: Uri) {
            viewModelScope.launch {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    repository.setDownloadLocation(enabled = true, treeUri = uri.toString())
                } catch (_: SecurityException) {
                    appEventBus.send(
                        AppEvent.ShowSnackbar(UiText.Resource(R.string.settings_download_location_failure)),
                    )
                }
            }
        }

        fun openHelp() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Navigate(HelpKey)) }
        }

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }
    }

private const val SETTINGS_STOP_TIMEOUT_MILLIS = 5_000L
