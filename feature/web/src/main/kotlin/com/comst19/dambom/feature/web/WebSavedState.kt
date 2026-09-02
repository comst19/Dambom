package com.comst19.dambom.feature.web

import androidx.lifecycle.SavedStateHandle
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.feature.web.contract.MAX_WEB_TABS
import com.comst19.dambom.feature.web.contract.RecentPage
import com.comst19.dambom.feature.web.contract.WebDetectionState
import com.comst19.dambom.feature.web.contract.WebTab
import com.comst19.dambom.feature.web.contract.WebUiState
import kotlinx.collections.immutable.toPersistentList

internal fun SavedStateHandle.restoreWebUiState(): WebUiState {
    val ids = get<LongArray>(TAB_IDS_KEY) ?: longArrayOf()
    val titles = get<ArrayList<String>>(TAB_TITLES_KEY).orEmpty()
    val urls = get<ArrayList<String>>(TAB_URLS_KEY).orEmpty()
    val detectionStates = get<ArrayList<String>>(TAB_DETECTION_STATES_KEY).orEmpty()
    val restoredTabs =
        ids
            .mapIndexed { index, id ->
                WebTab(
                    id = id,
                    title =
                        titles.getOrNull(index).orEmpty().let { title ->
                            if (title == "새 탭" || title == "New tab") "" else title
                        },
                    url = urls.getOrNull(index)?.takeIf(String::isNotBlank),
                    detectionState = detectionStates.getOrNull(index).toDetectionState(),
                )
            }.ifEmpty { listOf(WebTab(id = 1L)) }
    val restoredCurrentTabId =
        get<Long>(CURRENT_TAB_ID_KEY)?.takeIf { id -> restoredTabs.any { it.id == id } }
            ?: restoredTabs.first().id
    val tabs = restoredTabs.boundedTabs(restoredCurrentTabId).toPersistentList()
    val currentTabId = restoredCurrentTabId.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id
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

private fun List<WebTab>.boundedTabs(currentTabId: Long): List<WebTab> {
    if (size <= MAX_WEB_TABS) return this
    if (take(MAX_WEB_TABS).any { it.id == currentTabId }) return take(MAX_WEB_TABS)
    return take(MAX_WEB_TABS - 1) + first { it.id == currentTabId }
}

internal fun SavedStateHandle.persist(
    state: WebUiState,
    nextTabId: Long,
) {
    this[TAB_IDS_KEY] = state.tabs.map(WebTab::id).toLongArray()
    this[TAB_TITLES_KEY] = ArrayList(state.tabs.map(WebTab::title))
    this[TAB_URLS_KEY] = ArrayList(state.tabs.map { it.url ?: NULL_URL })
    this[TAB_DETECTION_STATES_KEY] = ArrayList(state.tabs.map { it.detectionState.persistedValue() })
    this[CURRENT_TAB_ID_KEY] = state.currentTabId
    this[RECENT_TITLES_KEY] = ArrayList(state.recentPages.map(RecentPage::title))
    this[RECENT_URLS_KEY] = ArrayList(state.recentPages.map(RecentPage::url))
    this[NEXT_TAB_ID_KEY] = nextTabId
}

private fun WebDetectionState.persistedValue(): String =
    when (this) {
        WebDetectionState.Idle, WebDetectionState.Scanning -> DETECTION_IDLE
        is WebDetectionState.Found -> "$DETECTION_FOUND_PREFIX$count"
        is WebDetectionState.NotFound -> "$DETECTION_NOT_FOUND_PREFIX${reason.name}"
    }

private fun String?.toDetectionState(): WebDetectionState =
    when {
        this == null || this == DETECTION_IDLE -> {
            WebDetectionState.Idle
        }

        startsWith(DETECTION_FOUND_PREFIX) -> {
            val count = removePrefix(DETECTION_FOUND_PREFIX).toIntOrNull() ?: return WebDetectionState.Idle
            WebDetectionState.Found(count)
        }

        startsWith(DETECTION_NOT_FOUND_PREFIX) -> {
            val reason =
                runCatching { UnsupportedReason.valueOf(removePrefix(DETECTION_NOT_FOUND_PREFIX)) }
                    .getOrNull()
                    ?: return WebDetectionState.Idle
            WebDetectionState.NotFound(reason)
        }

        else -> {
            WebDetectionState.Idle
        }
    }

private const val TAB_IDS_KEY = "web-tab-ids"
private const val TAB_TITLES_KEY = "web-tab-titles"
private const val TAB_URLS_KEY = "web-tab-urls"
private const val TAB_DETECTION_STATES_KEY = "web-tab-detection-states"
private const val CURRENT_TAB_ID_KEY = "web-current-tab-id"
private const val RECENT_TITLES_KEY = "web-recent-titles"
private const val RECENT_URLS_KEY = "web-recent-urls"
private const val NEXT_TAB_ID_KEY = "web-next-tab-id"
private const val NULL_URL = ""
private const val DETECTION_IDLE = "idle"
private const val DETECTION_FOUND_PREFIX = "found:"
private const val DETECTION_NOT_FOUND_PREFIX = "not-found:"
