package com.comst19.dambom.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationConfigTest {
    @Test
    fun `home and library are the only top level destinations`() {
        assertEquals(2, AppNavigationConfig.topLevelDestinations.size)
    }

    @Test
    fun `bottom destinations provide a localized label and icons`() {
        AppNavigationConfig.topLevelDestinations
            .filter { it.bottomBarLabelRes != null }
            .forEach { destination ->
                assertTrue(destination.selectedIcon != null)
                assertTrue(destination.unselectedIcon != null)
            }
    }
}
