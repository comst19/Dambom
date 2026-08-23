package com.comst19.dambom.core.navigation.contract

import com.comst19.dambom.core.navigation.AppNavKey
import com.comst19.dambom.core.navigation.TopLevelNavKey
import kotlinx.serialization.Serializable

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
    ) : HomeGraph

    @Serializable
    data object DownloadsKey : HomeGraph
}

sealed interface SettingsGraph : AppNavKey {
    @Serializable
    data object SettingsKey : SettingsGraph
}

sealed interface LibraryGraph : AppNavKey {
    @Serializable
    data object LibraryKey : LibraryGraph, TopLevelNavKey

    @Serializable
    data class VideoDetailKey(
        val id: String,
    ) : LibraryGraph
}
