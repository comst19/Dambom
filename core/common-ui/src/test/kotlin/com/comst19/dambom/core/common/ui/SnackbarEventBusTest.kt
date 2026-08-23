package com.comst19.dambom.core.common.ui

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SnackbarEventBusTest {
    @Test
    fun `events are delivered in order`() =
        runTest {
            val eventBus = SnackbarEventBus()
            val first = SnackbarEvent(UiText.Dynamic("Saved"))
            val second = SnackbarEvent(UiText.Dynamic("Deleted"))

            eventBus.send(first)
            eventBus.send(second)

            eventBus.events.test {
                assertEquals(first, awaitItem())
                assertEquals(second, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
