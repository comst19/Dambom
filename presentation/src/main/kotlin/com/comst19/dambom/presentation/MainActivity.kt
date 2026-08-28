package com.comst19.dambom.presentation

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.url.SharedUrlBus
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.MessageContent
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.presentation.contract.AppStartupState
import com.comst19.dambom.presentation.navigation.AppNavigationConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var navigationDispatcher: NavigationDispatcher

    @Inject lateinit var appEventBus: AppEventBus

    @Inject lateinit var sharedUrlBus: SharedUrlBus

    private val mainViewModel: MainViewModel by viewModels()
    private var videoFullscreenOrientationState = VideoFullscreenOrientationState()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        applyOrientationPolicy(resources.configuration.smallestScreenWidthDp)
        splashScreen.setKeepOnScreenCondition {
            mainViewModel.startupState.value is AppStartupState.Initializing
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (savedInstanceState == null) handleSharedText(intent)
        setContent {
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            val networkAccess by mainViewModel.networkAccess.collectAsStateWithLifecycle()
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
                            appEventBus = appEventBus,
                            networkAccess = networkAccess,
                            onVideoFullscreenChange = ::onVideoFullscreenChange,
                            onVideoRotate = ::onVideoRotate,
                        )
                    }

                    is AppStartupState.Failed -> {
                        MessageContent(message = getString(R.string.startup_initialization_failed))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedText(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationPolicy(newConfig.smallestScreenWidthDp)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            videoFullscreenOrientationState = videoFullscreenOrientationState.withPictureInPictureEntered()
            applyOrientationPolicy(resources.configuration.smallestScreenWidthDp)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun applyOrientationPolicy(smallestScreenWidthDp: Int) {
        val orientation = requestedOrientationFor(smallestScreenWidthDp, videoFullscreenOrientationState)
        if (requestedOrientation != orientation) requestedOrientation = orientation
    }

    private fun onVideoFullscreenChange(isVideoFullscreen: Boolean) {
        videoFullscreenOrientationState = videoFullscreenOrientationState.withFullscreen(isVideoFullscreen)
        applyOrientationPolicy(resources.configuration.smallestScreenWidthDp)
    }

    private fun onVideoRotate() {
        videoFullscreenOrientationState = videoFullscreenOrientationState.rotate(resources.configuration.orientation)
        applyOrientationPolicy(resources.configuration.smallestScreenWidthDp)
    }

    private fun handleSharedText(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        lifecycleScope.launch {
            navigationDispatcher.dispatch(NavigationEvent.NavigateTopLevel(HomeKey))
            sharedUrlBus.offer(sharedText)
        }
    }
}

internal fun requestedOrientationFor(
    smallestScreenWidthDp: Int,
    isVideoFullscreen: Boolean = false,
): Int = requestedOrientationFor(smallestScreenWidthDp, VideoFullscreenOrientationState(isVideoFullscreen))

internal fun requestedOrientationFor(
    smallestScreenWidthDp: Int,
    state: VideoFullscreenOrientationState,
): Int =
    when {
        smallestScreenWidthDp >= ROTATION_MIN_SMALLEST_WIDTH_DP -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        state.manualOrientation != null -> state.manualOrientation
        state.isFullscreen -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

internal data class VideoFullscreenOrientationState(
    val isFullscreen: Boolean = false,
    val manualOrientation: Int? = null,
) {
    fun withFullscreen(isFullscreen: Boolean): VideoFullscreenOrientationState =
        if (isFullscreen) copy(isFullscreen = true) else VideoFullscreenOrientationState()

    fun rotate(currentOrientation: Int): VideoFullscreenOrientationState {
        if (!isFullscreen) return this
        return copy(
            manualOrientation =
                if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                },
        )
    }

    fun withPictureInPictureEntered(): VideoFullscreenOrientationState = copy(manualOrientation = null)
}

private const val ROTATION_MIN_SMALLEST_WIDTH_DP = 600
