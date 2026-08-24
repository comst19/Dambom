package com.comst19.dambom.feature.web.contract

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.UnsupportedReason
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class WebUiState(
    val tabs: PersistentList<WebTab> = persistentListOf(WebTab(id = 1L)),
    val currentTabId: Long = 1L,
    val recentPages: PersistentList<RecentPage> = persistentListOf(),
) {
    val currentTab: WebTab?
        get() = tabs.firstOrNull { it.id == currentTabId }
}

@Immutable
internal data class WebTab(
    val id: Long,
    val title: String = "새 탭",
    val url: String? = null,
    val detectionState: WebDetectionState = WebDetectionState.Idle,
)

@Immutable
internal data class RecentPage(
    val title: String,
    val url: String,
)

@Immutable
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
