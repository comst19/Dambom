package com.comst19.dambom.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.url.SharedUrlBus
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.DownloadsKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.WebKey
import com.comst19.dambom.core.navigation.contract.SettingsGraph.SettingsKey
import com.comst19.dambom.feature.home.contract.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

@HiltViewModel
internal class HomeViewModel
    @Inject
    constructor(
        private val navigation: NavigationDispatcher,
        private val settingsRepository: SettingsRepository,
        downloadRepository: DownloadRepository,
        private val sharedUrlBus: SharedUrlBus,
        private val savedStateHandle: SavedStateHandle,
        private val appEventBus: AppEventBus,
    ) : ViewModel() {
        private val url = savedStateHandle.getStateFlow(URL_KEY, "")
        private val clipboardUrl = MutableStateFlow<String?>(null)
        private var lastSuggestedClipboardUrl: String? = null
        private val downloadSummary = downloadRepository.downloads.map(::toHomeDownloadSummary).distinctUntilChanged()

        val uiState: StateFlow<HomeUiState> =
            combine(
                url,
                settingsRepository.settings,
                sharedUrlBus.pendingUrl,
                clipboardUrl,
                downloadSummary,
            ) { url, settings, sharedUrl, clipboardUrl, summary ->
                HomeUiState(
                    url = url,
                    isUrlValid = url.isValidHttpUrl(),
                    showClipboardConsent = !settings.clipboardPromptShown,
                    clipboardSuggestionEnabled = settings.clipboardSuggestionEnabled,
                    clipboardUrl = clipboardUrl,
                    sharedUrl = sharedUrl,
                    downloadSummary = summary,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = HomeUiState(),
            )

        fun updateUrl(value: String) {
            savedStateHandle[URL_KEY] = value
        }

        fun useClipboardText(text: String?) {
            val detectedUrl = text?.extractHttpUrl() ?: return
            savedStateHandle[URL_KEY] = detectedUrl
            clipboardUrl.value = null
        }

        fun suggestClipboardText(text: String?) {
            if (!uiState.value.clipboardSuggestionEnabled) return
            val detectedUrl = text?.extractHttpUrl() ?: return
            if (detectedUrl == lastSuggestedClipboardUrl || detectedUrl == uiState.value.url) return
            lastSuggestedClipboardUrl = detectedUrl
            clipboardUrl.value = detectedUrl
        }

        fun dismissClipboardSuggestion() {
            clipboardUrl.value = null
        }

        fun setClipboardSuggestionEnabled(enabled: Boolean) {
            viewModelScope.launch {
                suspendRunCatching {
                    settingsRepository.setClipboardSuggestion(promptShown = true, enabled = enabled)
                }.onFailure {
                    appEventBus.send(AppEvent.ShowSnackbar(UiText.Resource(R.string.home_clipboard_save_failed)))
                }
            }
        }

        fun analyzeSharedUrl() {
            val sharedUrl = uiState.value.sharedUrl ?: return
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(DetectionResultKey(sharedUrl)))
                sharedUrlBus.clear()
            }
        }

        fun dismissSharedUrl() {
            sharedUrlBus.clear()
        }

        fun analyzeUrl() {
            val currentUrl = uiState.value.url.trim()
            if (currentUrl.isValidHttpUrl()) navigateToDetection(currentUrl)
        }

        fun openSettings() {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(SettingsKey))
            }
        }

        fun openWeb(url: String? = null) {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(WebKey(url)))
            }
        }

        fun openDownloads() {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(DownloadsKey))
            }
        }

        private fun navigateToDetection(url: String) {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(DetectionResultKey(url)))
            }
        }
    }

private fun String.isValidHttpUrl(): Boolean =
    runCatching {
        val uri = URI(trim())
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

private fun String.extractHttpUrl(): String? =
    HTTP_URL_REGEX
        .findAll(this)
        .map { it.value.trimEnd('.', ',', ')', ']', '}') }
        .firstOrNull(String::isValidHttpUrl)

private val HTTP_URL_REGEX = Regex("https?://[^\\s<]+", RegexOption.IGNORE_CASE)
private const val URL_KEY = "url"
private const val STOP_TIMEOUT_MILLIS = 5_000L
