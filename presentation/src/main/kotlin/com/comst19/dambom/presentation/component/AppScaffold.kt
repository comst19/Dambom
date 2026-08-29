package com.comst19.dambom.presentation.component

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.comst19.dambom.core.common.ui.LocalAppScaffoldPadding
import com.comst19.dambom.core.common.ui.currentAdaptiveLayoutInfo
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.domain.model.NetworkRestriction
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.NavigationState
import com.comst19.dambom.core.navigation.Navigator
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoDetailKey
import com.comst19.dambom.presentation.R
import com.comst19.dambom.presentation.navigation.AppChrome
import com.comst19.dambom.presentation.navigation.AppNavigationConfig
import com.comst19.dambom.presentation.navigation.appChrome
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
    networkAccess: NetworkAccessState,
    isLibraryDetailPaneVisible: Boolean,
    isVideoFullscreen: Boolean,
) {
    val defaultChrome = appChrome(state.currentKey)
    val chrome =
        defaultChrome.copy(
            showBottomBar =
                shouldShowBottomBar(
                    currentKey = state.currentKey,
                    defaultVisible = defaultChrome.showBottomBar,
                    supportsMultiplePanes = currentAdaptiveLayoutInfo().supportsMultiplePanes,
                ),
        )
    val policy = appScaffoldPolicy(chrome, state.isAtRoot, isVideoFullscreen)
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

    BackHandler(enabled = policy.showRootBackHandler) {
        val nowMillis = SystemClock.elapsedRealtime()
        if (isSecondRootBackPress(lastRootBackPressedAtMillis, nowMillis)) {
            activity?.finish()
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
            coroutineScope.launch { snackbarHostState.showSnackbar(rootBackMessage) }
        }
        lastRootBackPressedAtMillis = nowMillis
    }
    if (policy.showSystemBarAppearance) {
        SystemBarAppearance(chrome)
    }
    Scaffold(
        contentWindowInsets = if (policy.useSafeDrawingInsets) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0),
        topBar = {
            if (policy.showNetworkRestrictionBanner) {
                networkAccess.restriction?.let { NetworkRestrictionBanner(it) }
            }
        },
        snackbarHost = {
            if (policy.showSnackbarHost) {
                SnackbarHost(snackbarHostState)
            }
        },
        bottomBar = {
            if (policy.showBottomBar) {
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
        val appPadding = if (policy.provideZeroPadding) PaddingValues() else innerPadding
        CompositionLocalProvider(LocalAppScaffoldPadding provides appPadding) {
            // 각 화면이 일반, 목록, 전체 화면 특성에 맞게 이 PaddingValues를 적용합니다.
            AppNavDisplay(
                entries = entries,
                navigator = navigator,
                isLibraryDetailPaneVisible = isLibraryDetailPaneVisible,
                isVideoFullscreen = isVideoFullscreen,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal data class AppScaffoldPolicy(
    val showRootBackHandler: Boolean,
    val showSystemBarAppearance: Boolean,
    val showNetworkRestrictionBanner: Boolean,
    val showSnackbarHost: Boolean,
    val showBottomBar: Boolean,
    val useSafeDrawingInsets: Boolean,
    val provideZeroPadding: Boolean,
)

internal fun shouldShowBottomBar(
    currentKey: NavKey,
    defaultVisible: Boolean,
    supportsMultiplePanes: Boolean,
): Boolean = defaultVisible || (supportsMultiplePanes && currentKey is VideoDetailKey)

internal fun appScaffoldPolicy(
    chrome: AppChrome,
    isAtRoot: Boolean,
    isVideoFullscreen: Boolean,
): AppScaffoldPolicy =
    if (isVideoFullscreen) {
        AppScaffoldPolicy(
            showRootBackHandler = false,
            showSystemBarAppearance = false,
            showNetworkRestrictionBanner = false,
            showSnackbarHost = false,
            showBottomBar = false,
            useSafeDrawingInsets = false,
            provideZeroPadding = true,
        )
    } else {
        AppScaffoldPolicy(
            showRootBackHandler = isAtRoot,
            showSystemBarAppearance = true,
            showNetworkRestrictionBanner = true,
            showSnackbarHost = true,
            showBottomBar = chrome.showBottomBar,
            useSafeDrawingInsets = true,
            provideZeroPadding = false,
        )
    }

@Composable
private fun NetworkRestrictionBanner(restriction: NetworkRestriction) {
    val offline = restriction == NetworkRestriction.OFFLINE
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        color = if (offline) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (offline) Icons.Outlined.SignalWifiOff else Icons.Outlined.WifiFind,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text =
                    stringResource(
                        if (offline) R.string.network_offline_banner else R.string.network_unmetered_banner,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
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
