package com.comst19.dambom.presentation

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.presentation.contract.AppStartupState
import com.comst19.dambom.presentation.entry.SharedTextEntryHandler
import com.comst19.dambom.presentation.system.VideoOrientationCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var navigationDispatcher: NavigationDispatcher

    @Inject lateinit var appEventBus: AppEventBus

    @Inject internal lateinit var sharedTextEntryHandler: SharedTextEntryHandler

    private val mainViewModel: MainViewModel by viewModels()
    private val videoOrientationCoordinator by lazy { VideoOrientationCoordinator(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        videoOrientationCoordinator.applyInitialPolicy()
        splashScreen.setKeepOnScreenCondition {
            mainViewModel.startupState.value is AppStartupState.Initializing
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (savedInstanceState == null) handleSharedTextIntent(intent)
        setContent {
            MainActivityContent(
                viewModel = mainViewModel,
                dispatcher = navigationDispatcher,
                appEventBus = appEventBus,
                onVideoFullscreenChange = videoOrientationCoordinator::onFullscreenChanged,
                onVideoRotate = videoOrientationCoordinator::onRotateRequested,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedTextIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        videoOrientationCoordinator.onConfigurationChanged(newConfig)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        videoOrientationCoordinator.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    private fun handleSharedTextIntent(intent: Intent) {
        lifecycleScope.launch {
            sharedTextEntryHandler.handle(intent)
        }
    }
}
