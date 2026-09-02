package com.comst19.dambom.feature.web

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.domain.model.MediaDetectionResult
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        private val savedWebStates = LinkedHashMap<Long, Bundle>()
        private val detectedMediaKeys = mutableMapOf<Long, MutableSet<String>>()
        private val pageGenerations = mutableMapOf<Long, Long>()
        private val readyPageGenerations = mutableMapOf<Long, Long>()
        private val scanJobs = mutableMapOf<Long, Job>()
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
            if (!uiState.value.canCreateTab) return
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
            invalidatePage(id)
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
            invalidatePage(uiState.value.currentTabId)
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
            val previousUrl =
                uiState.value.tabs
                    .firstOrNull { it.id == tabId }
                    ?.url
            val pageChanged = previousUrl != safeUrl
            if (pageChanged) invalidatePage(tabId)
            updateState { state ->
                val tabs =
                    state.tabs
                        .map { tab ->
                            if (tab.id == tabId) {
                                tab.copy(
                                    url = safeUrl,
                                    title = title?.takeIf(String::isNotBlank) ?: safeUrl.hostLabel(),
                                    detectionState = if (pageChanged) WebDetectionState.Idle else tab.detectionState,
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

        fun onPageStarted(
            tabId: Long,
            url: String?,
            title: String?,
            generation: Long,
        ) {
            invalidatePage(tabId)
            pageGenerations[tabId] = generation
            updatePage(tabId, url, title)
            pageGenerations[tabId] = generation
        }

        fun onPageFinished(
            tabId: Long,
            url: String?,
            title: String?,
            generation: Long,
        ) {
            val safeUrl = url?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
            if (pageGenerations[tabId] != generation) return
            if (uiState.value.tabs
                    .firstOrNull { it.id == tabId }
                    ?.url != safeUrl
            ) {
                return
            }
            updatePage(tabId, safeUrl, title)
            readyPageGenerations[tabId] = generation
            if (safeUrl.hasVideoExtension()) onMediaRequest(tabId, generation, safeUrl)
        }

        fun scanCurrentTab() {
            val tab = uiState.value.currentTab ?: return
            val url = tab.url ?: return
            val generation = pageGenerations[tab.id]
            scanJobs.remove(tab.id)?.cancel()
            updateCurrentTab { it.copy(detectionState = WebDetectionState.Scanning) }
            scanJobs[tab.id] =
                viewModelScope.launch {
                    when (val result = mediaDetectionRepository.detect(url)) {
                        is MediaDetectionResult.Success -> {
                            updateTabIfCurrentPage(tab.id, url, generation) {
                                it.copy(detectionState = WebDetectionState.Found(result.candidates.size))
                            }
                        }

                        is MediaDetectionResult.Unsupported -> {
                            updateTabIfCurrentPage(tab.id, url, generation) {
                                it.copy(detectionState = WebDetectionState.NotFound(result.reason))
                            }
                        }
                    }
                }
        }

        fun openDetectedMedia() {
            val tab = uiState.value.currentTab ?: return
            if (tab.detectionState !is WebDetectionState.Found) return
            tab.url?.let(::openDetection)
        }

        fun onMediaRequest(
            tabId: Long,
            generation: Long,
            url: String,
        ) {
            if (!url.hasVideoExtension()) return
            viewModelScope.launch {
                if (readyPageGenerations[tabId] != generation) return@launch
                val keys = detectedMediaKeys.getOrPut(tabId, ::mutableSetOf)
                val changed = keys.add(url.detectedVideoKey())
                if (changed) {
                    updateCurrentTabIf(tabId) { it.copy(detectionState = WebDetectionState.Found(keys.size)) }
                }
            }
        }

        fun saveWebState(
            tabId: Long,
            state: Bundle,
        ) {
            savedWebStates.remove(tabId)
            savedWebStates[tabId] = state
            while (savedWebStates.size > MAX_RETAINED_WEB_STATES) {
                savedWebStates.remove(savedWebStates.keys.first())
            }
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

        private fun updateTabIfCurrentPage(
            tabId: Long,
            url: String,
            generation: Long?,
            transform: (WebTab) -> WebTab,
        ) {
            if (pageGenerations[tabId] != generation) return
            if (uiState.value.tabs
                    .firstOrNull { it.id == tabId }
                    ?.url != url
            ) {
                return
            }
            updateCurrentTabIf(tabId, transform)
        }

        private fun invalidatePage(tabId: Long) {
            scanJobs.remove(tabId)?.cancel()
            detectedMediaKeys.remove(tabId)
            pageGenerations.remove(tabId)
            readyPageGenerations.remove(tabId)
        }

        private fun updateState(transform: (WebUiState) -> WebUiState) {
            mutableUiState.update(transform)
            savedStateHandle.persist(uiState.value, nextTabId)
        }
    }

private const val MAX_RECENT_PAGES = 8
private const val MAX_RETAINED_WEB_STATES = 3
private const val NEXT_TAB_ID_KEY = "web-next-tab-id"
private const val INITIAL_URL_APPLIED_KEY = "web-initial-url-applied"
