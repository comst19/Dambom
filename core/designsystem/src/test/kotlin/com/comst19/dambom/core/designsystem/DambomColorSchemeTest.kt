package com.comst19.dambom.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class DambomColorSchemeTest {
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
