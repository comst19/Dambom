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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.navigation.NavigationConfig
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.Navigator
import com.comst19.dambom.core.navigation.rememberNavigationState
import com.comst19.dambom.core.navigation.toEntries
import com.comst19.dambom.feature.detection.navigation.detectionEntries
import com.comst19.dambom.feature.downloads.navigation.downloadEntries
import com.comst19.dambom.feature.home.navigation.homeEntries
import com.comst19.dambom.feature.library.navigation.libraryEntries
import com.comst19.dambom.feature.settings.navigation.settingsEntries
import com.comst19.dambom.feature.web.navigation.webEntries
import com.comst19.dambom.presentation.component.AppScaffold
import com.comst19.dambom.presentation.event.ObserveAppEvents

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
        rememberNavigationState(navigationConfig)
    val navigator = remember(state) { Navigator(state) }
    val snackbarHostState = remember { SnackbarHostState() }
    val currentNetworkAccess = rememberUpdatedState(networkAccess)
    var isLibraryDetailPaneVisible by rememberSaveable { mutableStateOf(true) }
    var isVideoFullscreen by rememberSaveable { mutableStateOf(false) }
    val updateVideoFullscreen: (Boolean) -> Unit = { fullscreen ->
        isVideoFullscreen = fullscreen
        onVideoFullscreenChange(fullscreen)
    }

    // 모든 feature가 발행한 명령은 이 단일 collector에서 순서대로 NavigationState에 반영합니다.
    LaunchedEffect(dispatcher, navigator) {
        dispatcher.events.collect(navigator::handle)
    }
    ObserveAppEvents(appEventBus, snackbarHostState)

    val entries =
        state.toEntries(
            entryProvider<NavKey> {
                detectionEntries { currentNetworkAccess.value }
                downloadEntries { currentNetworkAccess.value }
                homeEntries { currentNetworkAccess.value }
                libraryEntries(
                    isDetailPaneVisible = { isLibraryDetailPaneVisible },
                    onDetailPaneVisibilityChange = { isLibraryDetailPaneVisible = it },
                    isVideoFullscreen = { isVideoFullscreen },
                    onVideoFullscreenChange = updateVideoFullscreen,
                    onVideoRotate = onVideoRotate,
                )
                settingsEntries()
                webEntries()
            },
        )

    AppScaffold(
        state = state,
        navigator = navigator,
        dispatcher = dispatcher,
        entries = entries,
        snackbarHostState = snackbarHostState,
        networkAccess = networkAccess,
        isLibraryDetailPaneVisible = isLibraryDetailPaneVisible,
        isVideoFullscreen = isVideoFullscreen,
    )
}
