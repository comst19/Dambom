package com.comst19.dambom.feature.web

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.net.hasVideoFileExtension
import com.comst19.dambom.core.common.net.isUnsupportedVideoService
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaResourceRequest
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.domain.repository.MediaDetectionSnapshots
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
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
internal class WebViewModel
    @Inject
    constructor(
        private val mediaDetectionRepository: MediaDetectionRepository,
        private val navigation: NavigationDispatcher,
        private val savedStateHandle: SavedStateHandle,
        private val snapshots: MediaDetectionSnapshots = MediaDetectionSnapshots(),
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(savedStateHandle.restoreWebUiState())
        val uiState: StateFlow<WebUiState> = mutableUiState.asStateFlow()
        private val savedWebStates = LinkedHashMap<Long, Bundle>()
        private val mediaByTab = mutableMapOf<Long, WebMediaCandidates>()
        private val blockedTabs = mutableMapOf<Long, UnsupportedReason>()
        private val pageGenerations = mutableMapOf<Long, Long>()
        private val automaticallyScannedTabs = mutableSetOf<Long>()
        private val scanJobs = mutableMapOf<Long, Job>()
        private val probeJobs = mutableMapOf<Long, MutableList<Job>>()
        private val probeSlots = Semaphore(2)
        private var nextTabId =
            savedStateHandle[NEXT_TAB_ID_KEY] ?: (
                (
                    uiState.value.tabs.maxOfOrNull(WebTab::id)
                        ?: 0L
                ) + 1L
            )
        private var initialUrlApplied = savedStateHandle[INITIAL_URL_APPLIED_KEY] ?: false

        fun applyInitialUrl(
            url: String?,
            sharedLink: Boolean = false,
        ) {
            if (initialUrlApplied) return
            initialUrlApplied = true
            savedStateHandle[INITIAL_URL_APPLIED_KEY] = true
            val normalizedUrl = url?.normalizeAddress() ?: return
            navigateCurrentTab(normalizedUrl)
            if (sharedLink) {
                savedStateHandle[SHARED_ORIGIN_TAB_ID] = uiState.value.currentTabId
                savedStateHandle[SHARED_ORIGIN_URL] = normalizedUrl
            }
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
            clearSharedOrigin(id)
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
            if (uiState.value.currentTab?.url != url) clearSharedOrigin(uiState.value.currentTabId)
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
            if (pageChanged) {
                clearSharedOrigin(tabId)
                invalidatePage(tabId)
            }
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
            if (url?.isUnsupportedVideoService() == true) blockDownloads(tabId, UnsupportedReason.UNSUPPORTED_SERVICE)
        }

        fun onPageFinished(
            tabId: Long,
            url: String?,
            title: String?,
            generation: Long,
        ) {
            val safeUrl = url?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
            if (pageGenerations[tabId] != generation || tabId in blockedTabs) return
            if (uiState.value.tabs
                    .firstOrNull { it.id == tabId }
                    ?.url != safeUrl
            ) {
                return
            }
            updatePage(tabId, safeUrl, title)
            if (safeUrl.hasVideoExtension()) {
                onMediaRequest(tabId, generation, safeUrl)
            } else if (automaticallyScannedTabs.add(tabId) && scanJobs[tabId]?.isActive != true) {
                scanTab(tabId, automatic = true)
            }
        }

        fun scanCurrentTab() {
            scanTab(uiState.value.currentTabId, automatic = false)
        }

        private fun scanTab(
            tabId: Long,
            automatic: Boolean,
        ) {
            val tab = uiState.value.tabs.firstOrNull { it.id == tabId && tabId !in blockedTabs } ?: return
            val url = tab.url ?: return
            val generation = pageGenerations[tab.id]
            scanJobs.remove(tab.id)?.cancel()
            if (!automatic) updateCurrentTabIf(tabId) { it.copy(detectionState = WebDetectionState.Scanning) }
            scanJobs[tab.id] =
                viewModelScope.launch {
                    when (val result = mediaDetectionRepository.detect(url)) {
                        is MediaDetectionResult.Success -> {
                            updateTabIfCurrentPage(tab.id, url, generation) {
                                val media = mediaByTab.getOrPut(tab.id, ::WebMediaCandidates)
                                media.merge(result.candidates, containsProtectedMedia = result.hasProtectedMedia)
                                it.copy(detectionState = media.detectionState())
                            }
                        }

                        is MediaDetectionResult.Unsupported -> {
                            updateTabIfCurrentPage(tab.id, url, generation) {
                                val media = mediaByTab.getOrPut(tab.id, ::WebMediaCandidates)
                                if (result.reason == UnsupportedReason.DRM_PROTECTED) media.rejectProtected(url)
                                val detectionState =
                                    if (media.count > 0 || media.hasProtectedMedia) {
                                        media.detectionState()
                                    } else if (automatic) {
                                        it.detectionState
                                    } else {
                                        WebDetectionState.NotFound(result.reason)
                                    }
                                it.copy(detectionState = detectionState)
                            }
                        }
                    }
                }
        }

        fun openDetectedMedia() {
            val tab = uiState.value.currentTab?.takeIf { it.detectionState is WebDetectionState.Found }
            val candidates = tab?.let { mediaByTab[it.id]?.candidates }.orEmpty()
            val url = tab?.url ?: return
            if (candidates.isEmpty()) return
            val snapshotId =
                snapshots.save(
                    url,
                    MediaDetectionResult.Success(tab.title, candidates, mediaByTab[tab.id]?.hasProtectedMedia == true),
                )
            val sharedLink =
                savedStateHandle.get<Long>(SHARED_ORIGIN_TAB_ID) == tab.id &&
                    savedStateHandle.get<String>(SHARED_ORIGIN_URL) == url
            viewModelScope.launch {
                navigation.dispatch(
                    NavigationEvent.Navigate(
                        DetectionResultKey(
                            url,
                            snapshotId,
                            sharedLink = sharedLink,
                        ),
                    ),
                )
            }
        }

        fun onMediaRequest(
            tabId: Long,
            generation: Long,
            url: String,
        ) = onMediaRequest(tabId, generation, MediaResourceRequest(url))

        fun onMediaRequest(
            tabId: Long,
            generation: Long,
            request: MediaResourceRequest,
        ) {
            viewModelScope.launch {
                if (pageGenerations[tabId] != generation || tabId in blockedTabs) return@launch
                if (request.isProtected) {
                    val media = mediaByTab.getOrPut(tabId, ::WebMediaCandidates)
                    media.rejectProtected(request.url)
                    updateCurrentTabIf(tabId) { it.copy(detectionState = media.detectionState()) }
                    return@launch
                }
                if (!request.method.equals("GET", true) || !shouldProbeMedia(request.url)) return@launch
                probeJobs.getOrPut(tabId, ::mutableListOf).apply {
                    removeAll { it.isCompleted }
                    add(coroutineContext.job)
                }
                val tab = uiState.value.tabs.firstOrNull { it.id == tabId } ?: return@launch
                val media = mediaByTab.getOrPut(tabId, ::WebMediaCandidates)
                if (!media.claim(request)) return@launch
                val result =
                    if (request.url.hasVideoFileExtension()) {
                        directMediaResult(request, tab.title)
                    } else {
                        probeSlots.withPermit { mediaDetectionRepository.detectRequest(request.url, request.headers) }
                    }
                if (pageGenerations[tabId] != generation || tabId in blockedTabs) return@launch
                applyMediaProbe(tabId, request.url, media, result)
            }
        }

        private fun applyMediaProbe(
            tabId: Long,
            sourceUrl: String,
            media: WebMediaCandidates,
            result: MediaDetectionResult,
        ) {
            when (result) {
                is MediaDetectionResult.Unsupported -> {
                    if (result.reason == UnsupportedReason.DRM_PROTECTED) {
                        media.rejectProtected(sourceUrl)
                        updateCurrentTabIf(tabId) { it.copy(detectionState = media.detectionState()) }
                    }
                }

                is MediaDetectionResult.Success -> {
                    media.merge(
                        result.candidates,
                        preserveExistingTitle = true,
                        containsProtectedMedia = result.hasProtectedMedia,
                    )
                    updateCurrentTabIf(tabId) { it.copy(detectionState = media.detectionState()) }
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
            if (pageGenerations[tabId] != generation || tabId in blockedTabs) return
            if (uiState.value.tabs
                    .firstOrNull { it.id == tabId }
                    ?.url != url
            ) {
                return
            }
            updateCurrentTabIf(tabId, transform)
        }

        private fun clearSharedOrigin(tabId: Long) {
            if (savedStateHandle.get<Long>(SHARED_ORIGIN_TAB_ID) != tabId) return
            savedStateHandle.remove<Long>(SHARED_ORIGIN_TAB_ID)
            savedStateHandle.remove<String>(SHARED_ORIGIN_URL)
        }

        private fun blockDownloads(
            tabId: Long,
            reason: UnsupportedReason,
        ) {
            blockedTabs[tabId] = reason
            scanJobs.remove(tabId)?.cancel()
            probeJobs.remove(tabId)?.forEach(Job::cancel)
            mediaByTab.remove(tabId)
            updateCurrentTabIf(tabId) { it.copy(detectionState = WebDetectionState.NotFound(reason)) }
        }

        private fun invalidatePage(tabId: Long) {
            blockedTabs.remove(tabId)
            scanJobs.remove(tabId)?.cancel()
            automaticallyScannedTabs.remove(tabId)
            probeJobs.remove(tabId)?.forEach(Job::cancel)
            mediaByTab.remove(tabId)
            pageGenerations.remove(tabId)
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

private const val SHARED_ORIGIN_TAB_ID = "sharedOriginTabId"
private const val SHARED_ORIGIN_URL = "sharedOriginUrl"

private fun WebMediaCandidates.detectionState(): WebDetectionState =
    when {
        count > 0 -> WebDetectionState.Found(count, hasProtectedMedia)
        hasProtectedMedia -> WebDetectionState.NotFound(UnsupportedReason.DRM_PROTECTED)
        else -> WebDetectionState.Idle
    }
