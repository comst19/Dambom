package com.comst19.dambom.presentation

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.SnackbarEventBus
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.MessageContent
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.navigation.NavigationDispatcher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var navigationDispatcher: NavigationDispatcher

    @Inject lateinit var snackbarEventBus: SnackbarEventBus

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            mainViewModel.startupState.value is AppStartupState.Initializing
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            val startupState by mainViewModel.startupState.collectAsStateWithLifecycle()
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
                            dispatcher = navigationDispatcher,
                            snackbarEventBus = snackbarEventBus,
                        )
                    }

                    is AppStartupState.Failed -> {
                        MessageContent(message = getString(R.string.startup_initialization_failed))
                    }
                }
            }
        }
    }
}
