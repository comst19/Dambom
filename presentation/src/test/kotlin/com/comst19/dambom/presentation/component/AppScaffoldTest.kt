package com.comst19.dambom.presentation.component

import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.core.navigation.contract.SettingsGraph.SettingsKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScaffoldTest {
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
