package com.comst19.dambom.presentation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.navigation.NavigationConfig
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationState
import com.comst19.dambom.core.navigation.Navigator
import com.comst19.dambom.core.navigation.rememberNavigationState
import com.comst19.dambom.core.navigation.toEntries
import com.comst19.dambom.feature.detection.DetectionPlaceholder
import com.comst19.dambom.feature.detection.navigation.detectionEntries
import com.comst19.dambom.feature.downloads.navigation.downloadEntries
import com.comst19.dambom.feature.home.navigation.homeEntries
import com.comst19.dambom.feature.library.api.VideoDetailKey
import com.comst19.dambom.feature.library.navigation.LibraryPaneState
import com.comst19.dambom.feature.library.navigation.libraryEntries
import com.comst19.dambom.feature.settings.navigation.settingsEntries
import com.comst19.dambom.feature.web.navigation.webEntries
import com.comst19.dambom.presentation.component.AppScaffold
import com.comst19.dambom.presentation.event.ObserveAppEvents
import com.comst19.dambom.presentation.navigation.AppNavigationConfig

/**
 * 앱의 NavigationState, Navigator, entry provider와 공통 Scaffold를 한 번만 조립하는 최상위 Composable입니다.
 */
@Composable
internal fun DambomApp(
    navigationConfig: NavigationConfig,
    dispatcher: NavigationDispatcher,
    appEventBus: AppEventBus,
    networkAccess: NetworkAccessState,
    onVideoFullscreenChange: (Boolean) -> Unit,
    onVideoRotate: () -> Unit,
) {
    val state =
        rememberNavigationState(
            config = navigationConfig,
            serializersModule = AppNavigationConfig.serializersModule,
        )
    val navigator = remember(state) { Navigator(state) }
    val snackbarHostState = remember { SnackbarHostState() }
    val currentNetworkAccess = rememberUpdatedState(networkAccess)
    var isLibraryDetailPaneVisible by rememberSaveable { mutableStateOf(true) }
    var isHomeResultPaneVisible by rememberSaveable { mutableStateOf(true) }
    var isVideoFullscreen by rememberSaveable { mutableStateOf(false) }
    val activeVideoFullscreen = isVideoFullscreen && state.currentKey is VideoDetailKey
    val updateVideoFullscreen: (Boolean) -> Unit = { fullscreen ->
        isVideoFullscreen = fullscreen
        onVideoFullscreenChange(fullscreen)
    }

    LaunchedEffect(state.currentKey, isVideoFullscreen) {
        if (isVideoFullscreen && state.currentKey !is VideoDetailKey) {
            updateVideoFullscreen(false)
        }
    }

    // 모든 feature가 발행한 명령은 이 단일 collector에서 순서대로 NavigationState에 반영합니다.
    LaunchedEffect(dispatcher, navigator) {
        dispatcher.events.collect(navigator::handle)
    }
    ObserveAppEvents(appEventBus, snackbarHostState)

    val entryContext =
        DambomAppEntryContext(
            state = state,
            currentNetworkAccess = { currentNetworkAccess.value },
            panes =
                DambomAppPaneBindings(
                    isHomeResultPaneVisible = { isHomeResultPaneVisible },
                    onHomeResultPaneVisibilityChange = { isHomeResultPaneVisible = it },
                    isLibraryDetailPaneVisible = { isLibraryDetailPaneVisible },
                    onLibraryDetailPaneVisibilityChange = { isLibraryDetailPaneVisible = it },
                ),
            isVideoFullscreen = { isVideoFullscreen && state.currentKey is VideoDetailKey },
            onVideoFullscreenChange = updateVideoFullscreen,
            onVideoRotate = onVideoRotate,
        )
    val entryProviderFactory = LocalDambomEntryProviderFactory.current ?: ::defaultDambomEntryProvider
    val entries = state.toEntries(entryProviderFactory(entryContext))

    AppScaffold(
        state = state,
        navigator = navigator,
        dispatcher = dispatcher,
        entries = entries,
        snackbarHostState = snackbarHostState,
        networkAccess = networkAccess,
        isLibraryDetailPaneVisible = isLibraryDetailPaneVisible,
        isHomeResultPaneVisible = isHomeResultPaneVisible,
        isVideoFullscreen = activeVideoFullscreen,
    )
}

internal val LocalDambomEntryProviderFactory =
    staticCompositionLocalOf<((DambomAppEntryContext) -> (NavKey) -> NavEntry<NavKey>)?> { null }

internal class DambomAppPaneBindings(
    val isHomeResultPaneVisible: () -> Boolean,
    val onHomeResultPaneVisibilityChange: (Boolean) -> Unit,
    val isLibraryDetailPaneVisible: () -> Boolean,
    val onLibraryDetailPaneVisibilityChange: (Boolean) -> Unit,
)

internal class DambomAppEntryContext(
    val state: NavigationState,
    val currentNetworkAccess: () -> NetworkAccessState,
    val panes: DambomAppPaneBindings,
    val isVideoFullscreen: () -> Boolean,
    val onVideoFullscreenChange: (Boolean) -> Unit,
    val onVideoRotate: () -> Unit,
)

private fun defaultDambomEntryProvider(context: DambomAppEntryContext): (NavKey) -> NavEntry<NavKey> =
    entryProvider<NavKey> {
        detectionEntries(context.currentNetworkAccess)
        downloadEntries(context.currentNetworkAccess)
        homeEntries(
            resultPlaceholder = { DetectionPlaceholder() },
            isResultPaneVisible = context.panes.isHomeResultPaneVisible,
            onResultPaneVisibilityChange = context.panes.onHomeResultPaneVisibilityChange,
            networkAccess = context.currentNetworkAccess,
        )
        libraryEntries(
            paneState = {
                LibraryPaneState(
                    context.panes.isLibraryDetailPaneVisible(),
                    (context.state.currentKey as? VideoDetailKey)?.id,
                )
            },
            onDetailPaneVisibilityChange = context.panes.onLibraryDetailPaneVisibilityChange,
            isVideoFullscreen = context.isVideoFullscreen,
            onVideoFullscreenChange = context.onVideoFullscreenChange,
            onVideoRotate = context.onVideoRotate,
        )
        settingsEntries()
        webEntries()
    }
