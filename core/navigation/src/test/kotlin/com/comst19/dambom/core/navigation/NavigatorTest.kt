package com.comst19.dambom.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NavigatorTest {
    @Test
    fun `navigate duplicate replace pop and back update current stack`() {
        val state = testState()
        val navigator = Navigator(state)

        navigator.navigate(FirstTestKey)
        navigator.navigate(FirstTestKey)
        assertEquals(listOf(HomeKey, FirstTestKey), state.currentStack.toList())

        navigator.replace(SecondTestKey)
        assertEquals(SecondTestKey, state.currentKey)

        navigator.navigate(DetailTestKey(1))
        navigator.popTo(SecondTestKey, inclusive = false)
        assertEquals(SecondTestKey, state.currentKey)

        navigator.goBack()
        assertEquals(HomeKey, state.currentKey)
    }

    @Test
    fun `top level stacks stay independent and exit through home`() {
        val state = testState(TopLevelBackBehavior.ExitThroughHome)
        val navigator = Navigator(state)
        navigator.navigate(FirstTestKey)
        navigator.navigateTopLevel(SettingsKey)
        navigator.navigate(SettingsChildTestKey)
        navigator.navigateTopLevel(HomeKey)

        assertEquals(listOf(HomeKey, FirstTestKey), state.currentStack.toList())
        navigator.navigateTopLevel(SettingsKey)
        assertEquals(listOf(SettingsKey, SettingsChildTestKey), state.currentStack.toList())
        navigator.goBack()
        assertEquals(SettingsKey, state.currentKey)
        navigator.goBack()
        assertEquals(HomeKey, state.currentTopLevel)
    }

    @Test
    fun `exit through start keeps only home and current tab in use`() {
        val state = testState(TopLevelBackBehavior.ExitThroughHome)
        val navigator = Navigator(state)

        navigator.navigateTopLevel(SettingsKey)
        navigator.navigateTopLevel(SomethingTestKey)

        assertEquals(listOf(HomeKey, SomethingTestKey), state.topLevelsInUse)
        navigator.goBack()
        assertEquals(HomeKey, state.currentTopLevel)
    }

    @Test
    fun `exit from current keeps only selected tab and root back leaves state unchanged`() {
        val state = testState(TopLevelBackBehavior.ExitFromCurrent)
        val navigator = Navigator(state)

        navigator.navigateTopLevel(SettingsKey)
        navigator.navigateTopLevel(SomethingTestKey)

        assertEquals(listOf(SomethingTestKey), state.topLevelsInUse)
        navigator.goBack()
        assertEquals(SomethingTestKey, state.currentTopLevel)
    }

    @Test
    fun `non bottom top level does not add home to back history`() {
        val state = testState()

        state.selectTopLevel(LoginKey)

        assertEquals(listOf(LoginKey), state.topLevelsInUse)
    }

    @Test
    fun `deep link replaces current stack with synthetic back stack`() {
        val state = testState()
        val navigator = Navigator(state)
        navigator.navigate(SecondTestKey)

        navigator.handle(
            NavigationEvent.NavigateDeepLink(
                topLevelKey = HomeKey,
                backStack = listOf(HomeKey, FirstTestKey, DetailTestKey(42)),
            ),
        )

        assertEquals(listOf(HomeKey, FirstTestKey, DetailTestKey(42)), state.currentStack.toList())
        navigator.goBack()
        assertEquals(FirstTestKey, state.currentKey)
        navigator.goBack()
        assertEquals(HomeKey, state.currentKey)
    }

    @Test
    fun `synthetic navigation discards previous top level and exits through home`() {
        val state = testState(TopLevelBackBehavior.ExitThroughHome)
        val navigator = Navigator(state)
        navigator.navigateTopLevel(SettingsKey)
        navigator.navigate(SettingsChildTestKey)

        navigator.handle(
            NavigationEvent.NavigateDeepLink(
                topLevelKey = SomethingTestKey,
                backStack = listOf(SomethingTestKey, SomethingChildTestKey),
            ),
        )

        assertEquals(listOf(HomeKey, SomethingTestKey), state.topLevelsInUse)
        assertEquals(listOf(SettingsKey), state.backStacks.getValue(SettingsKey).toList())
        navigator.goBack()
        assertEquals(SomethingTestKey, state.currentKey)
        navigator.goBack()
        assertEquals(listOf(HomeKey), state.topLevelsInUse)
        assertEquals(HomeKey, state.currentKey)
    }

    @Test
    fun `synthetic back stack rejects a mismatched root`() {
        val navigator = Navigator(testState())

        assertThrows(IllegalArgumentException::class.java) {
            navigator.navigateDeepLink(
                NavigationEvent.NavigateDeepLink(
                    topLevelKey = SettingsKey,
                    backStack = listOf(HomeKey, FirstTestKey),
                ),
            )
        }
    }

    @Test
    fun `shared destination follows the current top level context`() {
        val state = testState()
        val navigator = Navigator(state)
        navigator.navigateTopLevel(SettingsKey)

        navigator.navigate(FirstTestKey)
        assertEquals(listOf(SettingsKey, FirstTestKey), state.currentStack.toList())

        navigator.replace(SecondTestKey)
        assertEquals(listOf(SettingsKey, SecondTestKey), state.currentStack.toList())
    }

    @Test
    fun `reset clears every stack and installs a new root`() {
        val state = testState()
        val navigator = Navigator(state)
        navigator.navigate(FirstTestKey)
        navigator.navigateTopLevel(SettingsKey)
        navigator.navigate(SettingsChildTestKey)

        navigator.handle(NavigationEvent.SetRoot(HomeKey))

        assertEquals(listOf(HomeKey), state.topLevelsInUse)
        assertEquals(listOf(HomeKey), state.currentStack.toList())
        assertEquals(listOf(SettingsKey), state.backStacks.getValue(SettingsKey).toList())
    }
}

private fun testState(topLevelBackBehavior: TopLevelBackBehavior = TopLevelBackBehavior.ExitThroughHome): NavigationState =
    NavigationState(
        bottomHomeKey = HomeKey,
        bottomBarKeys = setOf(HomeKey, SettingsKey, SomethingTestKey),
        topLevelHistory = NavBackStack<NavKey>(HomeKey),
        backStacks =
            mapOf(
                HomeKey to NavBackStack<NavKey>(HomeKey),
                SettingsKey to NavBackStack<NavKey>(SettingsKey),
                SomethingTestKey to NavBackStack<NavKey>(SomethingTestKey),
                LoginKey to NavBackStack<NavKey>(LoginKey),
            ),
        topLevelBackBehavior = topLevelBackBehavior,
    )

private data object SomethingTestKey : TopLevelNavKey

private data object FirstTestKey : AppNavKey

private data object SecondTestKey : AppNavKey

private data object SettingsChildTestKey : AppNavKey

private data object SomethingChildTestKey : AppNavKey

private data class DetailTestKey(
    val id: Long,
) : AppNavKey
