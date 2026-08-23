package com.comst19.dambom.presentation

import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationConfigTest {
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
