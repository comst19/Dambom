package com.comst19.dambom.presentation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.comst19.dambom.core.common.ui.SnackbarEventBus
import com.comst19.dambom.core.navigation.NavigationConfig
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.Navigator
import com.comst19.dambom.core.navigation.rememberNavigationState
import com.comst19.dambom.core.navigation.toEntries
import com.comst19.dambom.feature.home.navigation.homeEntries
import com.comst19.dambom.feature.library.navigation.libraryEntries
import com.comst19.dambom.feature.settings.navigation.settingsEntries
import com.comst19.dambom.presentation.component.AppScaffold
import com.comst19.dambom.presentation.event.ObserveSnackbarEvents

/**
 * 앱의 NavigationState, Navigator, entry provider와 공통 Scaffold를 한 번만 조립하는 최상위 Composable입니다.
 */
@Composable
internal fun DambomApp(
    navigationConfig: NavigationConfig,
    dispatcher: NavigationDispatcher,
    snackbarEventBus: SnackbarEventBus,
) {
    val state =
        rememberNavigationState(navigationConfig)
    val navigator = remember(state) { Navigator(state) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 모든 feature가 발행한 명령은 이 단일 collector에서 순서대로 NavigationState에 반영합니다.
    LaunchedEffect(dispatcher, navigator) {
        dispatcher.events.collect(navigator::handle)
    }
    ObserveSnackbarEvents(snackbarEventBus, snackbarHostState)

    val entries =
        state.toEntries(
            entryProvider<NavKey> {
                homeEntries()
                libraryEntries()
                settingsEntries()
            },
        )

    AppScaffold(
        state = state,
        navigator = navigator,
        dispatcher = dispatcher,
        entries = entries,
        snackbarHostState = snackbarHostState,
    )
}
