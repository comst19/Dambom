package com.comst19.dambom.presentation.component

import com.comst19.dambom.core.navigation.contract.HomeGraph.DetectionResultKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.WebKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.LibraryKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoDetailKey
import com.comst19.dambom.core.navigation.contract.SettingsGraph.SettingsKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDetectionSceneTest {
    private val result = DetectionResultKey("https://example.com/video")

    @Test
    fun `home placeholder and direct result belong to the same scene`() {
        assertTrue(usesHomeDetectionScene(listOf(HomeKey)))
        assertTrue(usesHomeDetectionScene(listOf(HomeKey, result)))
    }

    @Test
    fun `web results and unrelated destinations do not expose the input pane`() {
        assertFalse(usesHomeDetectionScene(listOf(HomeKey, WebKey(), result)))
        assertFalse(usesHomeDetectionScene(listOf(result)))
        assertFalse(usesHomeDetectionScene(listOf(HomeKey, result, SettingsKey)))
        assertFalse(usesHomeDetectionScene(listOf(LibraryKey, VideoDetailKey("video"))))
    }

    @Test
    fun `direct results keep the rail only when a home pane is visible`() {
        assertTrue(shouldShowNavigation(result, false, true, isHomeDetectionScene = true))
        assertFalse(shouldShowNavigation(result, false, false, isHomeDetectionScene = true))
        assertFalse(shouldShowNavigation(result, false, true, isHomeDetectionScene = false))
    }
}
