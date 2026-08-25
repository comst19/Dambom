package com.comst19.dambom.feature.library.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.LibraryKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoDetailKey
import com.comst19.dambom.feature.library.LibraryDetailPlaceholderRoute
import com.comst19.dambom.feature.library.LibraryRoute
import com.comst19.dambom.feature.library.VideoPlayerRoute

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.libraryEntries(
    isDetailPaneVisible: () -> Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
) {
    entry<LibraryKey>(
        metadata =
            ListDetailSceneStrategy.listPane(
                detailPlaceholder = { LibraryDetailPlaceholderRoute() },
            ),
    ) {
        LibraryRoute(
            isDetailPaneVisible = isDetailPaneVisible(),
            onDetailPaneVisibilityChange = onDetailPaneVisibilityChange,
        )
    }
    entry<VideoDetailKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
        VideoPlayerRoute(key.id)
    }
}
