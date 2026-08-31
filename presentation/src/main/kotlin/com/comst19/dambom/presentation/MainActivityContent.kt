package com.comst19.dambom.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.MessageContent
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.presentation.contract.AppStartupState
import com.comst19.dambom.presentation.navigation.AppNavigationConfig

@Composable
internal fun MainActivityContent(
    viewModel: MainViewModel,
    dispatcher: NavigationDispatcher,
    appEventBus: AppEventBus,
    onVideoFullscreenChange: (Boolean) -> Unit,
    onVideoRotate: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val networkAccess by viewModel.networkAccess.collectAsStateWithLifecycle()
    val startupState by viewModel.startupState.collectAsStateWithLifecycle()
    val useDarkTheme =
        when (settings.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    DambomTheme(darkTheme = useDarkTheme) {
        when (val state = startupState) {
            AppStartupState.Initializing -> {}

            is AppStartupState.Ready -> {
                DambomApp(
                    navigationConfig = AppNavigationConfig.navigation(state.startKey),
                    dispatcher = dispatcher,
                    appEventBus = appEventBus,
                    networkAccess = networkAccess,
                    onVideoFullscreenChange = onVideoFullscreenChange,
                    onVideoRotate = onVideoRotate,
                )
            }

            is AppStartupState.Failed -> {
                MessageContent(message = stringResource(R.string.startup_initialization_failed))
            }
        }
    }
}
