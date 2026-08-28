package com.comst19.dambom.feature.library.pip

import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.app.PictureInPictureParamsCompat
import androidx.core.app.PictureInPictureUiStateCompat
import androidx.core.util.Consumer
import androidx.media3.common.Player
import com.comst19.dambom.core.domain.model.DownloadTask

@Composable
internal fun PipPlatformEffect(
    player: Player,
    task: DownloadTask?,
    isFullscreen: Boolean,
    onPictureInPictureModeChanged: (Boolean) -> Unit,
    content: @Composable (isPipContentOnly: Boolean, onVideoBoundsChanged: (IntRect?) -> Unit) -> Unit,
) {
    val activity = LocalActivity.current as? ComponentActivity
    var isInPictureInPictureMode by remember(activity) { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    var isTransitioningToPip by remember(activity) { mutableStateOf(false) }
    var videoBounds by remember { mutableStateOf<IntRect?>(null) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var videoWidth by remember(player) { mutableStateOf(player.videoSize.width) }
    var videoHeight by remember(player) { mutableStateOf(player.videoSize.height) }
    val latestPlayer by rememberUpdatedState(player)
    val isSystemPipSupported =
        activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
    val latestAspectRatio by rememberUpdatedState(pipAspectRatio(videoWidth, videoHeight))
    val latestSourceBounds by rememberUpdatedState(
        videoBounds?.let { fittedVideoBounds(it, videoWidth, videoHeight) },
    )
    val latestModeChanged by rememberUpdatedState(onPictureInPictureModeChanged)
    val latestPlatformState by rememberUpdatedState(
        pipPlatformState(
            isSystemPipSupported = isSystemPipSupported,
            isFullscreen = isFullscreen,
            isPlaying = isPlaying,
            task = task,
            aspectRatio = latestAspectRatio,
            sourceBounds = latestSourceBounds,
        ),
    )
    val platform = remember(activity) { activity?.let(::ComponentActivityPipPlatform) }
    val controller = remember(platform) { platform?.let(::PipPlatformController) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(activity, controller) {
        if (activity == null || controller == null) return@DisposableEffect onDispose {}
        val modeListener =
            Consumer<PictureInPictureModeChangedInfo> { mode ->
                isInPictureInPictureMode = mode.isInPictureInPictureMode
                if (!mode.isInPictureInPictureMode) isTransitioningToPip = false
                latestModeChanged(mode.isInPictureInPictureMode)
            }
        val uiStateListener =
            Consumer<PictureInPictureUiStateCompat> { uiState ->
                isTransitioningToPip = uiState.isTransitioningToPip
            }
        val userLeaveListener =
            Runnable {
                if (latestPlayer.isPlaying) controller.onUserLeaveHint()
            }
        activity.addOnPictureInPictureModeChangedListener(modeListener)
        activity.addOnPictureInPictureUiStateChangedListener(uiStateListener)
        if (Build.VERSION.SDK_INT in 26..30) activity.addOnUserLeaveHintListener(userLeaveListener)
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(modeListener)
            activity.removeOnPictureInPictureUiStateChangedListener(uiStateListener)
            if (Build.VERSION.SDK_INT in 26..30) activity.removeOnUserLeaveHintListener(userLeaveListener)
            controller.dispose()
        }
    }
    LaunchedEffect(controller, latestPlatformState) {
        controller?.update(latestPlatformState)
    }

    content(
        isVideoOnlyPipContent(
            isInPictureInPictureMode = isInPictureInPictureMode,
            isTransitioningToPip = isTransitioningToPip,
        ),
        { bounds -> videoBounds = bounds },
    )
}

private class ComponentActivityPipPlatform(
    private val activity: ComponentActivity,
) : PipPlatform {
    override val apiLevel: Int = Build.VERSION.SDK_INT

    override fun setParams(params: PipPlatformParams) {
        activity.setPictureInPictureParams(params.toCompat())
    }

    override fun enterPictureInPictureMode() {
        activity.enterPictureInPictureMode(currentParams.toCompat())
    }

    private var currentParams = PipPlatformParams.Disabled

    private fun PipPlatformParams.toCompat(): PictureInPictureParamsCompat {
        currentParams = this
        return PictureInPictureParamsCompat
            .Builder()
            .setEnabled(enabled)
            .setAspectRatio(aspectRatio?.let { Rational(it.numerator, it.denominator) })
            .setSourceRectHint(sourceBounds?.toRect())
            .setSeamlessResizeEnabled(seamlessResizeEnabled)
            .build()
    }
}

private fun IntRect.toRect() = Rect(left, top, right, bottom)
