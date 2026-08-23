package com.comst19.dambom.feature.web

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

@HiltViewModel
internal class WebViewModel
    @Inject
    constructor(
        private val mediaDetectionRepository: MediaDetectionRepository,
        private val navigation: NavigationDispatcher,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(WebUiState())
        val uiState: StateFlow<WebUiState> = mutableUiState.asStateFlow()
        private val savedWebStates = mutableMapOf<Long, Bundle>()
        private val detectedMediaUrls = mutableMapOf<Long, MutableSet<String>>()
        private var nextTabId = 2L
        private var initialUrlApplied = false

        fun applyInitialUrl(url: String?) {
            if (initialUrlApplied) return
            initialUrlApplied = true
            val normalizedUrl = url?.normalizeAddress() ?: return
            navigateCurrentTab(normalizedUrl)
        }

        fun createTab(url: String? = null) {
            val tab = WebTab(id = nextTabId++, url = url?.normalizeAddress())
            mutableUiState.update { state ->
                state.copy(
                    tabs = state.tabs + tab,
                    currentTabId = tab.id,
                )
            }
        }

        fun selectTab(id: Long) {
            if (uiState.value.tabs.none { it.id == id }) return
            mutableUiState.update { it.copy(currentTabId = id) }
        }

        fun closeTab(id: Long) {
            savedWebStates.remove(id)
            detectedMediaUrls.remove(id)
            mutableUiState.update { state ->
                if (state.tabs.size == 1) {
                    val emptyTab = WebTab(id = nextTabId++)
                    WebUiState(
                        tabs = listOf(emptyTab),
                        currentTabId = emptyTab.id,
                        recentPages = state.recentPages,
                    )
                } else {
                    val closedIndex = state.tabs.indexOfFirst { it.id == id }
                    val remainingTabs = state.tabs.filterNot { it.id == id }
                    val currentTabId =
                        if (state.currentTabId == id) {
                            remainingTabs[closedIndex.coerceAtMost(remainingTabs.lastIndex)].id
                        } else {
                            state.currentTabId
                        }
                    state.copy(tabs = remainingTabs, currentTabId = currentTabId)
                }
            }
        }

        fun navigateCurrentTab(value: String) {
            val url = value.normalizeAddress() ?: return
            updateCurrentTab { tab ->
                tab.copy(
                    url = url,
                    title = url.hostLabel(),
                    detectionState = WebDetectionState.Idle,
                )
            }
        }

        fun updatePage(
            tabId: Long,
            url: String?,
            title: String?,
        ) {
            val safeUrl = url?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
            mutableUiState.update { state ->
                val tabs =
                    state.tabs.map { tab ->
                        if (tab.id == tabId) {
                            tab.copy(
                                url = safeUrl,
                                title = title?.takeIf(String::isNotBlank) ?: safeUrl.hostLabel(),
                            )
                        } else {
                            tab
                        }
                    }
                val currentTab = tabs.firstOrNull { it.id == tabId }
                val recentPages =
                    if (currentTab == null) {
                        state.recentPages
                    } else {
                        listOf(RecentPage(currentTab.title, safeUrl)) +
                            state.recentPages.filterNot { it.url == safeUrl }.take(MAX_RECENT_PAGES - 1)
                    }
                state.copy(tabs = tabs, recentPages = recentPages)
            }
        }

        fun detectCurrentTab() {
            val tab = uiState.value.currentTab ?: return
            val url = tab.url ?: return
            if (tab.detectionState is WebDetectionState.Found) {
                openDetection(url)
                return
            }
            updateCurrentTab { it.copy(detectionState = WebDetectionState.Scanning) }
            viewModelScope.launch {
                when (val result = mediaDetectionRepository.detect(url)) {
                    is MediaDetectionResult.Success -> {
                        updateCurrentTabIf(tab.id) {
                            it.copy(detectionState = WebDetectionState.Found(result.candidates.size))
                        }
                    }

                    is MediaDetectionResult.Unsupported -> {
                        updateCurrentTabIf(tab.id) {
                            it.copy(detectionState = WebDetectionState.NotFound(result.reason))
                        }
                    }
                }
            }
        }

        fun onMediaRequest(
            tabId: Long,
            url: String,
        ) {
            if (!url.hasVideoExtension()) return
            val urls = synchronized(detectedMediaUrls) { detectedMediaUrls.getOrPut(tabId, ::mutableSetOf).apply { add(url) }.size }
            updateCurrentTabIf(tabId) { it.copy(detectionState = WebDetectionState.Found(urls)) }
        }

        fun saveWebState(
            tabId: Long,
            state: Bundle,
        ) {
            savedWebStates[tabId] = state
        }

        fun savedWebState(tabId: Long): Bundle? = savedWebStates[tabId]

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }

        private fun openDetection(url: String) {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(DetectionResultKey(url)))
            }
        }

        private fun updateCurrentTab(transform: (WebTab) -> WebTab) {
            val currentTabId = uiState.value.currentTabId
            updateCurrentTabIf(currentTabId, transform)
        }

        private fun updateCurrentTabIf(
            tabId: Long,
            transform: (WebTab) -> WebTab,
        ) {
            mutableUiState.update { state ->
                state.copy(tabs = state.tabs.map { if (it.id == tabId) transform(it) else it })
            }
        }
    }

internal data class WebUiState(
    val tabs: List<WebTab> = listOf(WebTab(id = 1L)),
    val currentTabId: Long = 1L,
    val recentPages: List<RecentPage> = emptyList(),
) {
    val currentTab: WebTab?
        get() = tabs.firstOrNull { it.id == currentTabId }
}

internal data class WebTab(
    val id: Long,
    val title: String = "새 탭",
    val url: String? = null,
    val detectionState: WebDetectionState = WebDetectionState.Idle,
)

internal data class RecentPage(
    val title: String,
    val url: String,
)

internal sealed interface WebDetectionState {
    data object Idle : WebDetectionState

    data object Scanning : WebDetectionState

    data class Found(
        val count: Int,
    ) : WebDetectionState

    data class NotFound(
        val reason: UnsupportedReason,
    ) : WebDetectionState
}

private fun String.normalizeAddress(): String? =
    runCatching {
        val value = trim()
        val candidate = if (value.contains("://")) value else "https://$value"
        val uri = URI(candidate)
        if ((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) candidate else null
    }.getOrNull()

private fun String.hostLabel(): String = runCatching { URI(this).host.removePrefix("www.") }.getOrDefault(this)

private fun String.hasVideoExtension(): Boolean =
    VIDEO_EXTENSIONS.any { extension -> substringBefore('?').endsWith(extension, ignoreCase = true) }

private val VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".mov", ".m4v")
private const val MAX_RECENT_PAGES = 8
