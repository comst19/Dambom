package com.comst19.dambom.presentation.component

import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.LibraryKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoDetailKey
import com.comst19.dambom.core.navigation.contract.SettingsGraph.SettingsKey
import com.comst19.dambom.presentation.navigation.AppChrome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScaffoldTest {
    @Test
    fun `outgoing destination clears before incoming fade finishes`() {
        assertTrue(NAVIGATION_EXIT_DURATION_MILLIS < NAVIGATION_ENTER_DURATION_MILLIS)
        assertTrue(NAVIGATION_EXIT_DURATION_MILLIS <= 120)
    }

    @Test
    fun `fullscreen collapses Fold list detail to one pane`() {
        assertTrue(shouldUseSinglePane(supportsMultiplePanes = true, isVideoFullscreen = true))
    }

    @Test
    fun `normal Fold keeps multiple panes and phone stays single`() {
        assertFalse(shouldUseSinglePane(supportsMultiplePanes = true, isVideoFullscreen = false))
        assertTrue(shouldUseSinglePane(supportsMultiplePanes = false, isVideoFullscreen = false))
    }

    @Test
    fun `Library detail keeps bottom navigation only in a multi pane scene`() {
        val detail = VideoDetailKey("video")

        assertFalse(shouldShowBottomBar(currentKey = detail, defaultVisible = false, supportsMultiplePanes = false))
        assertTrue(shouldShowBottomBar(currentKey = detail, defaultVisible = false, supportsMultiplePanes = true))
        assertTrue(shouldShowBottomBar(currentKey = HomeKey, defaultVisible = true, supportsMultiplePanes = false))
    }

    @Test
    fun `Fold list detail limits bottom navigation to the list pane`() {
        val detail = VideoDetailKey("video")

        assertEquals(824, bottomBarWidthPx(detail, true, true, listPaneWidthPx = 824, windowWidthPx = 2_600))
        assertEquals(2_600, bottomBarWidthPx(detail, true, false, listPaneWidthPx = 824, windowWidthPx = 2_600))
        assertEquals(2_600, bottomBarWidthPx(detail, false, true, listPaneWidthPx = 824, windowWidthPx = 2_600))
        assertEquals(2_600, bottomBarWidthPx(HomeKey, true, true, listPaneWidthPx = 824, windowWidthPx = 2_600))
        assertEquals(2_600, bottomBarWidthPx(detail, true, true, listPaneWidthPx = 0, windowWidthPx = 2_600))
    }

    @Test
    fun `Fold library placeholder limits bottom navigation to the list pane`() {
        assertEquals(
            824,
            bottomBarWidthPx(LibraryKey, true, true, listPaneWidthPx = 824, windowWidthPx = 2_600),
        )
    }

    @Test
    fun `normal shell keeps chrome padding and root back handling`() {
        assertEquals(
            AppScaffoldPolicy(
                showRootBackHandler = true,
                showSystemBarAppearance = true,
                showNetworkRestrictionBanner = true,
                showSnackbarHost = true,
                showBottomBar = true,
                useSafeDrawingInsets = true,
                provideZeroPadding = false,
            ),
            appScaffoldPolicy(
                chrome = AppChrome(true, true, true),
                isAtRoot = true,
                isVideoFullscreen = false,
            ),
        )
    }

    @Test
    fun `fullscreen shell suppresses chrome padding and root back handling`() {
        assertEquals(
            AppScaffoldPolicy(
                showRootBackHandler = false,
                showSystemBarAppearance = false,
                showNetworkRestrictionBanner = false,
                showSnackbarHost = false,
                showBottomBar = false,
                useSafeDrawingInsets = false,
                provideZeroPadding = true,
            ),
            appScaffoldPolicy(
                chrome = AppChrome(true, true, true),
                isAtRoot = true,
                isVideoFullscreen = true,
            ),
        )
    }

    @Test
    fun `first root back press does not exit`() {
        assertFalse(isSecondRootBackPress(lastPressedAtMillis = 0L, nowMillis = 1_000L))
    }

    @Test
    fun `second root back press within two seconds exits`() {
        assertTrue(isSecondRootBackPress(lastPressedAtMillis = 1_000L, nowMillis = 3_000L))
    }

    @Test
    fun `root back press after two seconds starts a new interval`() {
        assertFalse(isSecondRootBackPress(lastPressedAtMillis = 1_000L, nowMillis = 3_001L))
    }

    @Test
    fun `replacing a root destination resets the back press timer`() {
        assertNotEquals(
            RootBackPressResetKey(SettingsKey, isAtRoot = true),
            RootBackPressResetKey(HomeKey, isAtRoot = true),
        )
    }
}
