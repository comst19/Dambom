package com.comst19.dambom.core.navigation.contract

import kotlinx.serialization.Serializable

const val HOME_DETECTION_DESTINATION = "dambom.homeDetectionDestination"

sealed interface HomeGraph : AppNavKey {
    @Serializable
    data object HomeKey : HomeGraph, TopLevelNavKey

    @Serializable
    data class WebKey(
        val url: String? = null,
    ) : HomeGraph

    @Serializable
    data class DetectionResultKey(
        val url: String,
        val snapshotId: String? = null,
        val requestId: String? = null,
    ) : HomeGraph

    @Serializable
    data object DownloadsKey : HomeGraph
}

sealed interface SettingsGraph : AppNavKey {
    @Serializable
    data object SettingsKey : SettingsGraph

    @Serializable
    data object HelpKey : SettingsGraph
}

sealed interface LibraryGraph : AppNavKey {
    @Serializable
    data object LibraryKey : LibraryGraph, TopLevelNavKey

    @Serializable
    data class VideoDetailKey(
        val id: String,
    ) : LibraryGraph
}
