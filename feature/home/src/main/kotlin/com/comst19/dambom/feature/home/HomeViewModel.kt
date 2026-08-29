package com.comst19.dambom.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.url.SharedUrlBus
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.DownloadsKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.WebKey
import com.comst19.dambom.core.navigation.contract.SettingsGraph.SettingsKey
import com.comst19.dambom.feature.home.contract.HomeDownloadSummary
import com.comst19.dambom.feature.home.contract.HomeUiState
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
        downloadRepository: DownloadRepository,
        private val sharedUrlBus: SharedUrlBus,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val url = savedStateHandle.getStateFlow(URL_KEY, "")
        private val clipboardUrl = MutableStateFlow<String?>(null)
        private var lastSuggestedClipboardUrl: String? = null

        val uiState: StateFlow<HomeUiState> =
            combine(
                url,
                settingsRepository.settings,
                sharedUrlBus.pendingUrl,
                clipboardUrl,
                downloadRepository.downloads,
            ) { url, settings, sharedUrl, clipboardUrl, downloads ->
                val active = downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
                val pausedCount = downloads.count { it.status == DownloadStatus.PAUSED }
                val failedCount = downloads.count { it.status == DownloadStatus.FAILED }
                val measurable = active.filter { (it.expectedBytes ?: 0L) > 0L }
                val totalBytes = measurable.sumOf { it.expectedBytes ?: 0L }
                val downloadedBytes = measurable.sumOf { it.downloadedBytes }
                HomeUiState(
                    url = url,
                    isUrlValid = url.isValidHttpUrl(),
                    showClipboardConsent = !settings.clipboardPromptShown,
                    clipboardSuggestionEnabled = settings.clipboardSuggestionEnabled,
                    clipboardUrl = clipboardUrl,
                    sharedUrl = sharedUrl,
                    downloadSummary =
                        HomeDownloadSummary(
                            activeCount = active.size,
                            pausedCount = pausedCount,
                            failedCount = failedCount,
                            progress =
                                if (totalBytes > 0L) {
                                    (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                } else {
                                    0f
                                },
                        ),
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
                settingsRepository.setClipboardSuggestion(promptShown = true, enabled = enabled)
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
