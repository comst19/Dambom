package com.comst19.dambom.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NavigationConfigTest {
    @Test
    fun `home start contains home only once`() {
        val config =
            NavigationConfig(
                startKey = HomeKey,
                bottomHomeKey = HomeKey,
                topLevelKeys = setOf(HomeKey, SettingsKey, StandaloneKey),
                bottomBarKeys = setOf(HomeKey, SettingsKey),
                topLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome,
            )

        assertEquals(listOf(HomeKey), config.initialTopLevelKeys)
    }

    @Test
    fun `bottom tab start keeps home behind selected tab`() {
        val config =
            NavigationConfig(
                startKey = SettingsKey,
                bottomHomeKey = HomeKey,
                topLevelKeys = setOf(HomeKey, SettingsKey, StandaloneKey),
                bottomBarKeys = setOf(HomeKey, SettingsKey),
                topLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome,
            )

        assertEquals(listOf(HomeKey, SettingsKey), config.initialTopLevelKeys)
    }

    @Test
    fun `non bottom start does not keep home behind it`() {
        val config =
            NavigationConfig(
                startKey = StandaloneKey,
                bottomHomeKey = HomeKey,
                topLevelKeys = setOf(HomeKey, SettingsKey, StandaloneKey),
                bottomBarKeys = setOf(HomeKey, SettingsKey),
                topLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome,
            )

        assertEquals(listOf(StandaloneKey), config.initialTopLevelKeys)
    }

    @Test
    fun `start key must be included in top level keys`() {
        assertThrows(IllegalArgumentException::class.java) {
            NavigationConfig(
                startKey = HomeKey,
                bottomHomeKey = SettingsKey,
                topLevelKeys = setOf(SettingsKey),
                bottomBarKeys = setOf(SettingsKey),
                topLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome,
            )
        }
    }

    @Test
    fun `bottom home key must be included in top level keys`() {
        assertThrows(IllegalArgumentException::class.java) {
            NavigationConfig(
                startKey = HomeKey,
                bottomHomeKey = SettingsKey,
                topLevelKeys = setOf(HomeKey),
                bottomBarKeys = setOf(SettingsKey),
                topLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome,
            )
        }
    }

    @Test
    fun `bottom bar keys must be included in top level keys`() {
        assertThrows(IllegalArgumentException::class.java) {
            NavigationConfig(
                startKey = HomeKey,
                bottomHomeKey = HomeKey,
                topLevelKeys = setOf(HomeKey),
                bottomBarKeys = setOf(HomeKey, SettingsKey),
                topLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome,
            )
        }
    }
}
