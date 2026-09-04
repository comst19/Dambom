package com.comst19.dambom.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DambomColorSchemeTest {
    @Test
    fun `primary and tonal button labels meet normal text contrast in both themes`() {
        listOf(DambomLightColorScheme, DambomDarkColorScheme).forEach { scheme ->
            listOf(scheme.primary to scheme.onPrimary, scheme.primaryContainer to scheme.onPrimaryContainer)
                .forEach { (backgroundColor, foregroundColor) ->
                    val foreground = foregroundColor.luminance()
                    val background = backgroundColor.luminance()
                    val contrast =
                        (maxOf(foreground, background) + 0.05f) / (minOf(foreground, background) + 0.05f)
                    assertTrue("Button label contrast was $contrast", contrast >= 4.5f)
                }
        }
    }

    @Test
    fun `light scheme provides Dambom palette roles`() {
        assertEquals(Color(0xFF1B64DA), DambomLightColorScheme.primary)
        assertEquals(Color(0xFFF2F4F6), DambomLightColorScheme.surfaceContainer)
        assertEquals(Color(0xFFE42939), DambomLightColorScheme.error)
        assertEquals(Color(0xFFE8F3FF), DambomLightColorScheme.primaryFixed)
    }

    @Test
    fun `dark scheme provides Dambom palette roles`() {
        assertEquals(Color(0xFF4593FC), DambomDarkColorScheme.primary)
        assertEquals(Color(0xFF252C35), DambomDarkColorScheme.surfaceContainer)
        assertEquals(Color(0xFFF66570), DambomDarkColorScheme.error)
        assertEquals(Color(0xFFE8F3FF), DambomDarkColorScheme.primaryFixed)
    }
}
