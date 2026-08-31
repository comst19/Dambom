package com.comst19.dambom.presentation.system

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration

internal class VideoOrientationCoordinator(
    private val activity: Activity,
) {
    private var state = VideoFullscreenOrientationState()

    fun applyInitialPolicy() {
        applyPolicy(activity.resources.configuration.smallestScreenWidthDp)
    }

    fun onConfigurationChanged(configuration: Configuration) {
        applyPolicy(configuration.smallestScreenWidthDp)
    }

    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        state =
            if (isInPictureInPictureMode) {
                state.withPictureInPictureEntered()
            } else {
                state.withPictureInPictureExited()
            }
        applyCurrentPolicy()
    }

    fun onFullscreenChanged(isFullscreen: Boolean) {
        state = state.withFullscreen(isFullscreen)
        applyCurrentPolicy()
    }

    fun onRotateRequested() {
        state = state.rotate(activity.resources.configuration.orientation)
        applyCurrentPolicy()
    }

    private fun applyCurrentPolicy() {
        applyPolicy(activity.resources.configuration.smallestScreenWidthDp)
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun applyPolicy(smallestScreenWidthDp: Int) {
        val orientation = requestedOrientationFor(smallestScreenWidthDp, state)
        if (activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
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
        state.isFullscreen -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
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

    fun withPictureInPictureEntered(): VideoFullscreenOrientationState =
        copy(manualOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)

    fun withPictureInPictureExited(): VideoFullscreenOrientationState = if (isFullscreen) copy(manualOrientation = null) else this
}

private const val ROTATION_MIN_SMALLEST_WIDTH_DP = 600
