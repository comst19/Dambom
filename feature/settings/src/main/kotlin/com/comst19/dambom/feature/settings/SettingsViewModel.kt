package com.comst19.dambom.feature.settings

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
import com.comst19.dambom.feature.settings.platform.SettingsPlatformActions
import dagger.hilt.android.lifecycle.HiltViewModel
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
        private val platformActions: SettingsPlatformActions,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            repository.settings.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SETTINGS_STOP_TIMEOUT_MILLIS),
                initialValue = AppSettings(),
            )
        val language = MutableStateFlow(AppLanguage.from(platformActions.currentLanguageTags))
        val versionName: String = platformActions.versionName

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { repository.setThemeMode(mode) }
        }

        fun setLanguage(language: AppLanguage) {
            this.language.value = language
            platformActions.applyLanguage(language.languageTag)
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

        fun setDownloadDirectory(treeUri: String) {
            viewModelScope.launch {
                if (platformActions.persistDownloadDirectory(treeUri)) {
                    repository.setDownloadLocation(enabled = true, treeUri = treeUri)
                } else {
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
