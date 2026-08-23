package com.comst19.dambom.presentation.component

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.common.ui.LocalAppScaffoldPadding
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.NavigationState
import com.comst19.dambom.core.navigation.Navigator
import com.comst19.dambom.presentation.AppNavigationConfig
import com.comst19.dambom.presentation.R
import com.comst19.dambom.presentation.appChrome
import com.comst19.dambom.presentation.system.SystemBarAppearance
import kotlinx.coroutines.launch

/**
 * NavigationState에 맞춰 공통 바텀바, 시스템 바, Snackbar와 앱 종료 경계 Back을 표시합니다.
 * destination 내부 UI는 [entries]를 표시하는 AppNavDisplay에 위임합니다.
 */
@Composable
internal fun AppScaffold(
    state: NavigationState,
    navigator: Navigator,
    dispatcher: NavigationDispatcher,
    entries: List<NavEntry<NavKey>>,
    snackbarHostState: SnackbarHostState,
) {
    val chrome = appChrome(state.currentKey)
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val rootBackMessage = stringResource(R.string.root_back_hint)
    var lastRootBackPressedAtMillis by
        remember(
            RootBackPressResetKey(
                currentTopLevel = state.currentTopLevel,
                isAtRoot = state.isAtRoot,
            ),
        ) {
            mutableLongStateOf(0L)
        }

    BackHandler(enabled = state.isAtRoot) {
        val nowMillis = SystemClock.elapsedRealtime()
        if (isSecondRootBackPress(lastRootBackPressedAtMillis, nowMillis)) {
            activity?.finish()
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
            coroutineScope.launch { snackbarHostState.showSnackbar(rootBackMessage) }
        }
        lastRootBackPressedAtMillis = nowMillis
    }
    SystemBarAppearance(chrome)
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (chrome.showBottomBar) {
                NavigationBar {
                    AppNavigationConfig.topLevelDestinations.forEach { destination ->
                        val labelRes = destination.bottomBarLabelRes ?: return@forEach
                        val selectedIcon = destination.selectedIcon ?: return@forEach
                        val unselectedIcon = destination.unselectedIcon ?: return@forEach
                        val isSelected = state.currentTopLevel == destination.key
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    dispatcher.dispatch(NavigationEvent.NavigateTopLevel(destination.key))
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) selectedIcon else unselectedIcon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalAppScaffoldPadding provides innerPadding) {
            // 각 화면이 일반, 목록, 전체 화면 특성에 맞게 이 PaddingValues를 적용합니다.
            AppNavDisplay(entries, navigator, Modifier.fillMaxSize())
        }
    }
}

/** 루트 Back을 [ROOT_BACK_INTERVAL_MILLIS] 안에 연속으로 눌렀는지 계산합니다. */
internal fun isSecondRootBackPress(
    lastPressedAtMillis: Long,
    nowMillis: Long,
): Boolean =
    lastPressedAtMillis != 0L &&
        nowMillis - lastPressedAtMillis <= ROOT_BACK_INTERVAL_MILLIS

internal data class RootBackPressResetKey(
    val currentTopLevel: NavKey,
    val isAtRoot: Boolean,
)

private const val ROOT_BACK_INTERVAL_MILLIS = 2_000L
