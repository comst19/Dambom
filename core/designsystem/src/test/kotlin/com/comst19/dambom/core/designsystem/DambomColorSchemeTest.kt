package com.comst19.dambom.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class DambomColorSchemeTest {
    @Test
    fun `light scheme provides Dambom palette roles`() {
        assertEquals(Color(0xFF2563EB), DambomLightColorScheme.primary)
        assertEquals(Color(0xFFF1F5F9), DambomLightColorScheme.surfaceContainer)
        assertEquals(Color(0xFFBA1A1A), DambomLightColorScheme.error)
        assertEquals(Color(0xFFDBEAFE), DambomLightColorScheme.primaryFixed)
    }

    @Test
    fun `dark scheme provides Dambom palette roles`() {
        assertEquals(Color(0xFF60A5FA), DambomDarkColorScheme.primary)
        assertEquals(Color(0xFF1E293B), DambomDarkColorScheme.surfaceContainer)
        assertEquals(Color(0xFFFFB4AB), DambomDarkColorScheme.error)
        assertEquals(Color(0xFFDBEAFE), DambomDarkColorScheme.primaryFixed)
    }
}
