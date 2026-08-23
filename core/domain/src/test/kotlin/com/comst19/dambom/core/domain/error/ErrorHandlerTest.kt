package com.comst19.dambom.core.domain.error

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

class ErrorHandlerTest {
    @Test
    fun `handled error is forwarded to the app error stream`() =
        runTest {
            val handler = ErrorHandler()
            val error = IllegalStateException("failure")

            handler.handle(error)

            assertSame(error, handler.errors.first())
        }
}
