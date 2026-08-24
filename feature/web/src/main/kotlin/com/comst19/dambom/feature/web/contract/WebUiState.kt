package com.comst19.dambom.feature.web.contract

import com.comst19.dambom.core.domain.model.UnsupportedReason

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
