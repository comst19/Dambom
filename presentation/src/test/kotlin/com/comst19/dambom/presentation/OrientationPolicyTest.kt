package com.comst19.dambom.presentation

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.comst19.dambom.presentation.system.VideoFullscreenOrientationState
import com.comst19.dambom.presentation.system.requestedOrientationFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationPolicyTest {
    @Test
    fun `baseline compact and expanded boundaries preserve the existing policy`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationFor(smallestScreenWidthDp = 599),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            requestedOrientationFor(smallestScreenWidthDp = 600),
        )
    }

    @Test
    fun `compact fullscreen maximizes video in landscape and restores portrait on exit`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
            requestedOrientationFor(smallestScreenWidthDp = 599, isVideoFullscreen = true),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationFor(smallestScreenWidthDp = 599, isVideoFullscreen = false),
        )
    }

    @Test
    fun `manual fullscreen rotation overrides compact phone only until fullscreen exits`() {
        val landscape =
            VideoFullscreenOrientationState(isFullscreen = true)
                .rotate(Configuration.ORIENTATION_PORTRAIT)
        val portrait = landscape.rotate(Configuration.ORIENTATION_LANDSCAPE)

        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
            requestedOrientationFor(smallestScreenWidthDp = 599, state = landscape),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
            requestedOrientationFor(smallestScreenWidthDp = 599, state = portrait),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationFor(smallestScreenWidthDp = 599, state = portrait.withFullscreen(false)),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            requestedOrientationFor(smallestScreenWidthDp = 600, state = landscape),
        )
    }

    @Test
    fun `PiP entry clears the compact manual override while preserving fullscreen`() {
        val state =
            VideoFullscreenOrientationState(isFullscreen = true)
                .rotate(Configuration.ORIENTATION_PORTRAIT)

        val cleared = state.withPictureInPictureEntered()

        assertTrue(cleared.isFullscreen)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, cleared.manualOrientation)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            requestedOrientationFor(smallestScreenWidthDp = 599, state = cleared),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
            requestedOrientationFor(smallestScreenWidthDp = 599, state = cleared.withPictureInPictureExited()),
        )
    }
}
