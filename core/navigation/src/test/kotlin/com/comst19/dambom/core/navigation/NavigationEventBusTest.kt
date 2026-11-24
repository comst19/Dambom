package com.comst19.dambom.core.navigation

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationEventBusTest {
    @Test
    fun `dispatch preserves event order`() =
        runTest {
            val dispatcher = ChannelNavigationDispatcher()
            val first = NavigationEvent.Navigate(TestDestinationKey)
            val second = NavigationEvent.Back
            dispatcher.dispatch(first)
            dispatcher.dispatch(second)

            dispatcher.events.test {
                assertEquals(first, awaitItem())
                assertEquals(second, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private data object TestDestinationKey : AppNavKey
