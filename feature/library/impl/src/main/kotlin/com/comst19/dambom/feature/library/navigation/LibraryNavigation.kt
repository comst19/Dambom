package com.comst19.dambom.feature.library.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.feature.library.LibraryDetailPlaceholderRoute
import com.comst19.dambom.feature.library.LibraryRoute
import com.comst19.dambom.feature.library.VideoPlayerRoute
import com.comst19.dambom.feature.library.api.LibraryKey
import com.comst19.dambom.feature.library.api.VideoDetailKey
import com.comst19.dambom.feature.library.compress.VideoCompressionRoute
import com.comst19.dambom.feature.library.subtitle.SubtitleRoute
import com.comst19.dambom.feature.library.subtitle.SubtitleTranscriptRoute
import com.comst19.dambom.feature.library.subtitle.export.SubtitleExportRoute
import com.comst19.dambom.feature.library.trim.VideoTrimRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val libraryNavSerializersModule: SerializersModule =
    SerializersModule {
        polymorphic(NavKey::class) {
            subclass(LibraryKey::class)
            subclass(VideoDetailKey::class)
            subclass(PrivateLibraryKey::class)
            subclass(VideoSubtitleTranscriptKey::class)
            subclass(VideoSubtitleKey::class)
            subclass(VideoSubtitleExportKey::class)
            subclass(VideoCompressionKey::class)
            subclass(VideoTrimKey::class)
        }
    }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun EntryProviderScope<NavKey>.libraryEntries(
    paneState: () -> LibraryPaneState,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
    isVideoFullscreen: () -> Boolean,
    onVideoFullscreenChange: (Boolean) -> Unit,
    onVideoRotate: () -> Unit,
) {
    entry<PrivateLibraryKey> {
        LibraryRoute(
            isDetailPaneVisible = false,
            activeVideoId = null,
            onDetailPaneVisibilityChange = {},
            privateVault = true,
        )
    }
    entry<VideoSubtitleTranscriptKey> { key -> SubtitleTranscriptRoute(key.id) }
    entry<VideoSubtitleExportKey> { key -> SubtitleExportRoute(key.id) }
    entry<VideoSubtitleKey> { key -> SubtitleRoute(key.id) }
    entry<VideoCompressionKey> { key -> VideoCompressionRoute(key.id) }
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
            isPrivate = key.isPrivate,
            isVideoFullscreen = isVideoFullscreen(),
            onVideoFullscreenChange = onVideoFullscreenChange,
            onVideoRotate = onVideoRotate,
        )
    }
}
