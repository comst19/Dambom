package com.comst19.dambom.presentation.system

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle

internal class VideoOrientationCoordinator(
    private val activity: Activity,
) {
    private var state = VideoFullscreenOrientationState()

    fun applyInitialPolicy(savedInstanceState: Bundle? = null) {
        state = restoreState(savedInstanceState)
        if (activity.isInPictureInPictureMode) {
            state = state.withPictureInPictureEntered()
        }
        applyPolicy(activity.resources.configuration.smallestScreenWidthDp)
    }

    fun saveState(outState: Bundle) {
        outState.putBoolean(KEY_IS_FULLSCREEN, state.isFullscreen)
        val manualOrientation = state.manualOrientation
        if (!state.isInPictureInPicture && manualOrientation != null) {
            outState.putInt(KEY_MANUAL_ORIENTATION, manualOrientation)
        } else {
            outState.remove(KEY_MANUAL_ORIENTATION)
        }
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

    private fun restoreState(savedInstanceState: Bundle?): VideoFullscreenOrientationState {
        if (savedInstanceState == null) return VideoFullscreenOrientationState()
        val isFullscreen = savedInstanceState.getBoolean(KEY_IS_FULLSCREEN, false)
        val manualOrientation =
            if (isFullscreen && savedInstanceState.containsKey(KEY_MANUAL_ORIENTATION)) {
                savedInstanceState.getInt(KEY_MANUAL_ORIENTATION)
            } else {
                null
            }
        return VideoFullscreenOrientationState(
            isFullscreen = isFullscreen,
            manualOrientation = manualOrientation,
        )
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
        state.isInPictureInPicture -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        state.manualOrientation != null -> state.manualOrientation
        state.isFullscreen -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

internal data class VideoFullscreenOrientationState(
    val isFullscreen: Boolean = false,
    val manualOrientation: Int? = null,
    val isInPictureInPicture: Boolean = false,
) {
    fun withFullscreen(isFullscreen: Boolean): VideoFullscreenOrientationState =
        if (isFullscreen) {
            copy(isFullscreen = true)
        } else {
            copy(
                isFullscreen = false,
                manualOrientation =
                    if (isInPictureInPicture) {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        null
                    },
            )
        }

    fun rotate(currentOrientation: Int): VideoFullscreenOrientationState {
        if (!isFullscreen || isInPictureInPicture) return this
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
        copy(
            manualOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            isInPictureInPicture = true,
        )

    fun withPictureInPictureExited(): VideoFullscreenOrientationState =
        copy(
            manualOrientation = null,
            isInPictureInPicture = false,
        )
}

private const val KEY_IS_FULLSCREEN = "dambom.video_orientation.is_fullscreen"
private const val KEY_MANUAL_ORIENTATION = "dambom.video_orientation.manual_orientation"
private const val ROTATION_MIN_SMALLEST_WIDTH_DP = 600
