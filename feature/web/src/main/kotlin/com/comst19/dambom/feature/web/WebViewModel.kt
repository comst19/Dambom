package com.comst19.dambom.feature.web

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.feature.web.contract.RecentPage
import com.comst19.dambom.feature.web.contract.WebDetectionState
import com.comst19.dambom.feature.web.contract.WebTab
import com.comst19.dambom.feature.web.contract.WebUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
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
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(savedStateHandle.restoreWebUiState())
        val uiState: StateFlow<WebUiState> = mutableUiState.asStateFlow()
        private val savedWebStates = mutableMapOf<Long, Bundle>()
        private val detectedMediaUrls = mutableMapOf<Long, MutableSet<String>>()
        private var nextTabId = savedStateHandle[NEXT_TAB_ID_KEY] ?: (uiState.value.tabs.maxOfOrNull(WebTab::id) ?: 0L) + 1L
        private var initialUrlApplied = savedStateHandle[INITIAL_URL_APPLIED_KEY] ?: false

        fun applyInitialUrl(url: String?) {
            if (initialUrlApplied) return
            initialUrlApplied = true
            savedStateHandle[INITIAL_URL_APPLIED_KEY] = true
            val normalizedUrl = url?.normalizeAddress() ?: return
            navigateCurrentTab(normalizedUrl)
        }

        fun createTab(url: String? = null) {
            val tab = WebTab(id = nextTabId++, url = url?.normalizeAddress())
            updateState { state ->
                state.copy(
                    tabs = state.tabs.mutate { it.add(tab) },
                    currentTabId = tab.id,
                )
            }
        }

        fun selectTab(id: Long) {
            if (uiState.value.tabs.none { it.id == id }) return
            updateState { it.copy(currentTabId = id) }
        }

        fun closeTab(id: Long) {
            savedWebStates.remove(id)
            detectedMediaUrls.remove(id)
            updateState { state ->
                if (state.tabs.size == 1) {
                    val emptyTab = WebTab(id = nextTabId++)
                    WebUiState(
                        tabs = persistentListOf(emptyTab),
                        currentTabId = emptyTab.id,
                        recentPages = state.recentPages,
                    )
                } else {
                    val closedIndex = state.tabs.indexOfFirst { it.id == id }
                    val remainingTabs = state.tabs.filterNot { it.id == id }.toPersistentList()
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
            updateState { state ->
                val tabs =
                    state.tabs
                        .map { tab ->
                            if (tab.id == tabId) {
                                tab.copy(
                                    url = safeUrl,
                                    title = title?.takeIf(String::isNotBlank) ?: safeUrl.hostLabel(),
                                )
                            } else {
                                tab
                            }
                        }.toPersistentList()
                val currentTab = tabs.firstOrNull { it.id == tabId }
                val recentPages =
                    if (currentTab == null) {
                        state.recentPages
                    } else {
                        (
                            listOf(RecentPage(currentTab.title, safeUrl)) +
                                state.recentPages.filterNot { it.url == safeUrl }.take(MAX_RECENT_PAGES - 1)
                        ).toPersistentList()
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
            updateState { state ->
                state.copy(tabs = state.tabs.map { if (it.id == tabId) transform(it) else it }.toPersistentList())
            }
        }

        private fun updateState(transform: (WebUiState) -> WebUiState) {
            mutableUiState.update(transform)
            savedStateHandle.persist(uiState.value, nextTabId)
        }
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
private const val TAB_IDS_KEY = "web-tab-ids"
private const val TAB_TITLES_KEY = "web-tab-titles"
private const val TAB_URLS_KEY = "web-tab-urls"
private const val CURRENT_TAB_ID_KEY = "web-current-tab-id"
private const val RECENT_TITLES_KEY = "web-recent-titles"
private const val RECENT_URLS_KEY = "web-recent-urls"
private const val NEXT_TAB_ID_KEY = "web-next-tab-id"
private const val INITIAL_URL_APPLIED_KEY = "web-initial-url-applied"
private const val NULL_URL = ""

private fun SavedStateHandle.restoreWebUiState(): WebUiState {
    val ids = get<LongArray>(TAB_IDS_KEY) ?: longArrayOf()
    val titles = get<ArrayList<String>>(TAB_TITLES_KEY).orEmpty()
    val urls = get<ArrayList<String>>(TAB_URLS_KEY).orEmpty()
    val tabs =
        ids
            .mapIndexed { index, id ->
                WebTab(
                    id = id,
                    title = titles.getOrNull(index).orEmpty().ifBlank { "새 탭" },
                    url = urls.getOrNull(index)?.takeIf(String::isNotBlank),
                )
            }.ifEmpty { listOf(WebTab(id = 1L)) }
            .toPersistentList()
    val currentTabId = get<Long>(CURRENT_TAB_ID_KEY)?.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id
    val recentTitles = get<ArrayList<String>>(RECENT_TITLES_KEY).orEmpty()
    val recentUrls = get<ArrayList<String>>(RECENT_URLS_KEY).orEmpty()
    val recentPages =
        recentUrls
            .mapIndexed { index, url ->
                RecentPage(
                    title = recentTitles.getOrNull(index).orEmpty().ifBlank { url.hostLabel() },
                    url = url,
                )
            }.toPersistentList()
    return WebUiState(tabs = tabs, currentTabId = currentTabId, recentPages = recentPages)
}

private fun SavedStateHandle.persist(
    state: WebUiState,
    nextTabId: Long,
) {
    this[TAB_IDS_KEY] = state.tabs.map(WebTab::id).toLongArray()
    this[TAB_TITLES_KEY] = ArrayList(state.tabs.map(WebTab::title))
    this[TAB_URLS_KEY] = ArrayList(state.tabs.map { it.url ?: NULL_URL })
    this[CURRENT_TAB_ID_KEY] = state.currentTabId
    this[RECENT_TITLES_KEY] = ArrayList(state.recentPages.map(RecentPage::title))
    this[RECENT_URLS_KEY] = ArrayList(state.recentPages.map(RecentPage::url))
    this[NEXT_TAB_ID_KEY] = nextTabId
}
