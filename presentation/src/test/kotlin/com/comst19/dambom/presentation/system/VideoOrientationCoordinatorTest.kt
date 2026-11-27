package com.comst19.dambom.presentation.system

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoOrientationCoordinatorTest {
    @Test
    fun `recreated compact host applies restored fullscreen supports manual rotation and resets on exit`() {
        val activity = createActivity(smallestScreenWidthDp = 411, orientation = Configuration.ORIENTATION_PORTRAIT)
        val coordinator = VideoOrientationCoordinator(activity)

        coordinator.applyInitialPolicy()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)

        coordinator.onFullscreenChanged(true)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, activity.requestedOrientation)

        activity.resources.configuration.orientation = Configuration.ORIENTATION_LANDSCAPE
        coordinator.onRotateRequested()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT, activity.requestedOrientation)

        activity.resources.configuration.orientation = Configuration.ORIENTATION_PORTRAIT
        coordinator.onRotateRequested()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, activity.requestedOrientation)

        coordinator.onFullscreenChanged(false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
    }

    @Test
    fun `saved fullscreen state restores landscape without intermediate portrait request`() {
        val oldActivity = createActivity(smallestScreenWidthDp = 360, orientation = Configuration.ORIENTATION_PORTRAIT)
        val oldCoordinator = VideoOrientationCoordinator(oldActivity)
        oldCoordinator.applyInitialPolicy()
        oldCoordinator.onFullscreenChanged(true)

        val savedState = Bundle()
        oldCoordinator.saveState(savedState)

        val recreatedActivity =
            createActivity(
                smallestScreenWidthDp = 360,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                initialRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
            )
        recreatedActivity.orientationWrites.clear()
        val recreatedCoordinator = VideoOrientationCoordinator(recreatedActivity)

        recreatedCoordinator.applyInitialPolicy(savedState)
        recreatedCoordinator.onFullscreenChanged(true)

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, recreatedActivity.requestedOrientation)
        assertFalse(recreatedActivity.orientationWrites.contains(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT))
    }

    @Test
    fun `saved manual fullscreen rotation survives recreation and resets when fullscreen exits`() {
        val oldActivity = createActivity(smallestScreenWidthDp = 360, orientation = Configuration.ORIENTATION_LANDSCAPE)
        val oldCoordinator = VideoOrientationCoordinator(oldActivity)
        oldCoordinator.applyInitialPolicy()
        oldCoordinator.onFullscreenChanged(true)
        oldCoordinator.onRotateRequested()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT, oldActivity.requestedOrientation)

        val savedState = Bundle()
        oldCoordinator.saveState(savedState)

        val recreatedActivity =
            createActivity(
                smallestScreenWidthDp = 360,
                orientation = Configuration.ORIENTATION_PORTRAIT,
                initialRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
            )
        recreatedActivity.orientationWrites.clear()
        val recreatedCoordinator = VideoOrientationCoordinator(recreatedActivity)

        recreatedCoordinator.applyInitialPolicy(savedState)
        recreatedCoordinator.onFullscreenChanged(true)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT, recreatedActivity.requestedOrientation)
        assertFalse(recreatedActivity.orientationWrites.contains(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT))

        recreatedCoordinator.onFullscreenChanged(false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, recreatedActivity.requestedOrientation)
    }

    @Test
    fun `foldable configuration transitions preserve restored fullscreen orientation policy`() {
        val activity = createActivity(smallestScreenWidthDp = 360, orientation = Configuration.ORIENTATION_PORTRAIT)
        val coordinator = VideoOrientationCoordinator(activity)

        coordinator.applyInitialPolicy()
        coordinator.onFullscreenChanged(true)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, activity.requestedOrientation)

        val unfolded =
            Configuration(activity.resources.configuration).apply {
                smallestScreenWidthDp = 680
            }
        activity.resources.configuration.smallestScreenWidthDp = 680
        coordinator.onConfigurationChanged(unfolded)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)

        val folded =
            Configuration(activity.resources.configuration).apply {
                smallestScreenWidthDp = 360
            }
        activity.resources.configuration.smallestScreenWidthDp = 360
        coordinator.onConfigurationChanged(folded)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, activity.requestedOrientation)

        coordinator.onFullscreenChanged(false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
    }

    @Test
    fun `PiP entry and exit work for both fullscreen and non fullscreen states including recreated host`() {
        val activity = createActivity(smallestScreenWidthDp = 411, orientation = Configuration.ORIENTATION_LANDSCAPE)
        val coordinator = VideoOrientationCoordinator(activity)

        coordinator.applyInitialPolicy()
        coordinator.onFullscreenChanged(true)
        coordinator.onRotateRequested()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT, activity.requestedOrientation)

        coordinator.onPictureInPictureModeChanged(true)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)

        coordinator.onPictureInPictureModeChanged(false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, activity.requestedOrientation)

        coordinator.onFullscreenChanged(false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)

        coordinator.onPictureInPictureModeChanged(true)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)

        coordinator.onFullscreenChanged(false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)

        coordinator.onPictureInPictureModeChanged(false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
    }

    @Test
    fun `recreated host in PiP preserves unspecified orientation until PiP exits`() {
        val oldActivity = createActivity(smallestScreenWidthDp = 411, orientation = Configuration.ORIENTATION_LANDSCAPE)
        val oldCoordinator = VideoOrientationCoordinator(oldActivity)
        oldCoordinator.applyInitialPolicy()
        oldCoordinator.onFullscreenChanged(true)
        oldCoordinator.onPictureInPictureModeChanged(true)

        val savedState = Bundle()
        oldCoordinator.saveState(savedState)

        val activity =
            createActivity(
                smallestScreenWidthDp = 411,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                inPictureInPicture = true,
            )
        val coordinator = VideoOrientationCoordinator(activity)

        coordinator.applyInitialPolicy(savedState)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)

        coordinator.onFullscreenChanged(true)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)

        activity.pipMode = false
        coordinator.onPictureInPictureModeChanged(false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, activity.requestedOrientation)

        val nonPipRecreated =
            createActivity(
                smallestScreenWidthDp = 411,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                inPictureInPicture = false,
            )
        val nonPipCoordinator = VideoOrientationCoordinator(nonPipRecreated)
        nonPipCoordinator.applyInitialPolicy(savedState)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, nonPipRecreated.requestedOrientation)
    }

    private fun createActivity(
        smallestScreenWidthDp: Int,
        orientation: Int,
        inPictureInPicture: Boolean = false,
        initialRequestedOrientation: Int? = null,
    ): TestOrientationActivity =
        Robolectric
            .buildActivity(TestOrientationActivity::class.java)
            .create()
            .get()
            .apply {
                pipMode = inPictureInPicture
                resources.configuration.smallestScreenWidthDp = smallestScreenWidthDp
                resources.configuration.orientation = orientation
                if (initialRequestedOrientation != null) {
                    requestedOrientation = initialRequestedOrientation
                }
            }

    private class TestOrientationActivity : ComponentActivity() {
        var pipMode: Boolean = false
        val orientationWrites = mutableListOf<Int>()

        override fun isInPictureInPictureMode(): Boolean = pipMode

        override fun setRequestedOrientation(requestedOrientation: Int) {
            orientationWrites += requestedOrientation
            super.setRequestedOrientation(requestedOrientation)
        }
    }
}
