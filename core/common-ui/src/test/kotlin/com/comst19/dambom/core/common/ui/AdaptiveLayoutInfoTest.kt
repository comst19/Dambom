package com.comst19.dambom.core.common.ui

import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutInfoTest {
    @Test
    fun `expanded width with compact height uses a single pane`() {
        val layoutInfo = WindowSizeClass(900, 400).toAdaptiveLayoutInfo()

        assertTrue(layoutInfo.isCompactHeight)
        assertFalse(layoutInfo.supportsMultiplePanes)
    }

    @Test
    fun `expanded width with medium height supports multiple panes`() {
        val layoutInfo = WindowSizeClass(900, 500).toAdaptiveLayoutInfo()

        assertFalse(layoutInfo.isCompactHeight)
        assertTrue(layoutInfo.supportsMultiplePanes)
    }

    @Test
    fun `compact width stays single pane regardless of height`() {
        val layoutInfo = WindowSizeClass(411, 891).toAdaptiveLayoutInfo()

        assertFalse(layoutInfo.isCompactHeight)
        assertFalse(layoutInfo.supportsMultiplePanes)
    }
}
