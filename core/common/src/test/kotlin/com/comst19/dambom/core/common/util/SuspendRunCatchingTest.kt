package com.comst19.dambom.core.common.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SuspendRunCatchingTest {
    @Test
    fun `returns success when block completes`() =
        runTest {
            val result = suspendRunCatching { "success" }

            assertEquals("success", result.getOrThrow())
        }

    @Test
    fun `rethrows cancellation exception`() {
        val cancellation = CancellationException("cancelled")

        val thrown =
            assertThrows(CancellationException::class.java) {
                runTest {
                    suspendRunCatching { throw cancellation }
                }
            }

        assertSame(cancellation, thrown)
    }

    @Test
    fun `returns failure for other exceptions`() =
        runTest {
            val exception = IllegalStateException("failed")

            val result = suspendRunCatching<Unit> { throw exception }

            assertSame(exception, result.exceptionOrNull())
        }
}
