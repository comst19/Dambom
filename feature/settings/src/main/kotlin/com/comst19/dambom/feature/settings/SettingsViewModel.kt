package com.comst19.dambom.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.SettingsGraph.HelpKey
import com.comst19.dambom.feature.settings.contract.AppLanguage
import com.comst19.dambom.feature.settings.contract.SaveLocationMode
import com.comst19.dambom.feature.settings.platform.SettingsPlatformActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private val downloadLocationMutex = Mutex()
        private val mutableLanguage = MutableStateFlow(AppLanguage.from(platformActions.currentLanguageTags))

        val settings: StateFlow<AppSettings> =
            repository.settings.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SETTINGS_STOP_TIMEOUT_MILLIS),
                initialValue = AppSettings(),
            )
        val language: StateFlow<AppLanguage> = mutableLanguage.asStateFlow()
        val versionName: String = platformActions.versionName

        fun setThemeMode(mode: ThemeMode) {
            launchSettingUpdate { repository.setThemeMode(mode) }
        }

        fun setLanguage(language: AppLanguage) {
            mutableLanguage.value = language
            platformActions.applyLanguage(language.languageTag)
        }

        fun setClipboardSuggestion(enabled: Boolean) {
            launchSettingUpdate {
                repository.setClipboardSuggestion(promptShown = true, enabled = enabled)
            }
        }

        fun setWifiOnlyDownloads(enabled: Boolean) {
            viewModelScope.launch {
                if (!updateSetting { repository.setWifiOnlyDownloads(enabled) }) return@launch
                updateSetting(R.string.settings_wifi_policy_apply_failure) {
                    downloadRepository.refreshNetworkPolicy()
                }
            }
        }

        fun setSaveLocationMode(mode: SaveLocationMode) {
            viewModelScope.launch {
                downloadLocationMutex.withLock {
                    val latestTreeUri = repository.settings.first().downloadTreeUri
                    updateSetting {
                        repository.setDownloadLocation(mode.usesConfiguredFolder, latestTreeUri)
                    }
                }
            }
        }

        fun setDownloadDirectory(treeUri: String) {
            viewModelScope.launch {
                downloadLocationMutex.withLock {
                    val previousTreeUri = repository.settings.first().downloadTreeUri
                    if (!platformActions.takePersistedDownloadDirectory(treeUri)) {
                        appEventBus.showSnackbar(R.string.settings_download_location_failure)
                        return@withLock
                    }
                    val acquiredNewGrant = previousTreeUri != treeUri
                    val result =
                        try {
                            suspendRunCatching {
                                repository.setDownloadLocation(enabled = true, treeUri = treeUri)
                            }
                        } catch (cancellation: CancellationException) {
                            if (acquiredNewGrant) {
                                platformActions.releasePersistedDownloadDirectory(treeUri)
                            }
                            throw cancellation
                        }
                    if (result.isFailure) {
                        if (acquiredNewGrant) {
                            platformActions.releasePersistedDownloadDirectory(treeUri)
                        }
                        appEventBus.showSnackbar(R.string.settings_download_location_failure)
                        return@withLock
                    }
                    previousTreeUri
                        ?.takeIf { it != treeUri }
                        ?.let(platformActions::releasePersistedDownloadDirectory)
                }
            }
        }

        private fun launchSettingUpdate(update: suspend () -> Unit) {
            viewModelScope.launch { updateSetting(update = update) }
        }

        private suspend fun updateSetting(
            failureMessageId: Int = R.string.settings_update_failure,
            update: suspend () -> Unit,
        ): Boolean {
            val result = suspendRunCatching { update() }
            if (result.isFailure) appEventBus.showSnackbar(failureMessageId)
            return result.isSuccess
        }

        fun openHelp() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Navigate(HelpKey)) }
        }

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }
    }

private const val SETTINGS_STOP_TIMEOUT_MILLIS = 5_000L

private suspend fun AppEventBus.showSnackbar(messageId: Int) {
    send(AppEvent.ShowSnackbar(UiText.Resource(messageId)))
}
