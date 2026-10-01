package com.comst19.dambom.feature.library.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.LibraryKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoDetailKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoTrimKey
import com.comst19.dambom.feature.library.LibraryDetailPlaceholderRoute
import com.comst19.dambom.feature.library.LibraryRoute
import com.comst19.dambom.feature.library.VideoPlayerRoute
import com.comst19.dambom.feature.library.trim.VideoTrimRoute

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun EntryProviderScope<NavKey>.libraryEntries(
    paneState: () -> LibraryPaneState,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
    isVideoFullscreen: () -> Boolean,
    onVideoFullscreenChange: (Boolean) -> Unit,
    onVideoRotate: () -> Unit,
) {
    entry<VideoTrimKey> { key -> VideoTrimRoute(key.id) }
    entry<LibraryKey>(
        metadata =
            ListDetailSceneStrategy.listPane(
                detailPlaceholder = { LibraryDetailPlaceholderRoute() },
            ),
    ) {
        val currentPaneState = paneState()
        LibraryRoute(
            isDetailPaneVisible = currentPaneState.isVisible,
            activeVideoId = currentPaneState.activeVideoId,
            onDetailPaneVisibilityChange = onDetailPaneVisibilityChange,
        )
    }
    entry<VideoDetailKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
        VideoPlayerRoute(
            id = key.id,
            isVideoFullscreen = isVideoFullscreen(),
            onVideoFullscreenChange = onVideoFullscreenChange,
            onVideoRotate = onVideoRotate,
        )
    }
}
