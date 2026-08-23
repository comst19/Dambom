package com.comst19.dambom.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.SharedUrlBus
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.WebKey
import com.comst19.dambom.core.navigation.contract.SettingsGraph.SettingsKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
        private val sharedUrlBus: SharedUrlBus,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val url = savedStateHandle.getStateFlow(URL_KEY, "")
        private val clipboardUrl = MutableStateFlow<String?>(null)

        val uiState: StateFlow<HomeUiState> =
            combine(
                url,
                settingsRepository.settings,
                sharedUrlBus.pendingUrl,
                clipboardUrl,
            ) { url, settings, sharedUrl, clipboardUrl ->
                HomeUiState(
                    url = url,
                    isUrlValid = url.isValidHttpUrl(),
                    showClipboardConsent = !settings.clipboardPromptShown,
                    clipboardSuggestionEnabled = settings.clipboardSuggestionEnabled,
                    clipboardUrl = clipboardUrl,
                    sharedUrl = sharedUrl,
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
            if (detectedUrl != uiState.value.url) clipboardUrl.value = detectedUrl
        }

        fun dismissClipboardSuggestion() {
            clipboardUrl.value = null
        }

        fun setClipboardSuggestionEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setClipboardSuggestion(promptShown = true, enabled = enabled)
            }
        }

        fun openSharedUrlInWeb() {
            val sharedUrl = uiState.value.sharedUrl ?: return
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(WebKey(sharedUrl)))
                sharedUrlBus.clear()
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

        private fun navigateToDetection(url: String) {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(DetectionResultKey(url)))
            }
        }
    }

internal data class HomeUiState(
    val url: String = "",
    val isUrlValid: Boolean = false,
    val showClipboardConsent: Boolean = false,
    val clipboardSuggestionEnabled: Boolean = false,
    val clipboardUrl: String? = null,
    val sharedUrl: String? = null,
)

private fun String.isValidHttpUrl(): Boolean =
    runCatching {
        val uri = URI(trim())
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

private fun String.extractHttpUrl(): String? = HTTP_URL_REGEX.find(this)?.value?.trimEnd('.', ',', ')', ']', '}')

private val HTTP_URL_REGEX = Regex("https?://[^\\s<]+", RegexOption.IGNORE_CASE)
private const val URL_KEY = "url"
private const val STOP_TIMEOUT_MILLIS = 5_000L
