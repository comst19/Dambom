package com.comst19.dambom.core.common.ui

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppEventBusTest {
    @Test
    fun `events are delivered in order`() =
        runTest {
            val eventBus = AppEventBus()
            val first = AppEvent.ShowSnackbar(UiText.Dynamic("Saved"))
            val second = AppEvent.ShowSnackbar(UiText.Dynamic("Deleted"))

            eventBus.send(first)
            eventBus.send(second)

            eventBus.events.test {
                assertEquals(first, awaitItem())
                assertEquals(second, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
