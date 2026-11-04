package com.comst19.dambom.core.common.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VideoExportGateTest {
    private val gate = VideoExportGate(1)

    @Test
    fun failedExportReleasesThePermit() =
        runTest {
            try {
                gate.run<Unit> { error("export failed") }
                fail("Expected export failure")
            } catch (failure: IllegalStateException) {
                assertEquals("export failed", failure.message)
            }

            assertEquals("available", gate.run { "available" })
        }

    @Test
    fun exportsWaitUntilTheActiveExportFinishes() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val first =
                launch {
                    gate.run {
                        events += "first"
                        release.await()
                        events += "released"
                    }
                }
            runCurrent()
            val second =
                async {
                    gate.run {
                        events += "second"
                        42
                    }
                }
            runCurrent()
            assertEquals(listOf("first"), events)
            assertFalse(second.isCompleted)

            release.complete(Unit)
            first.join()

            assertEquals(42, second.await())
            assertEquals(listOf("first", "released", "second"), events)
        }

    @Test
    fun cancellationFinishesCleanupBeforeReleasingThePermit() =
        runTest {
            var cleaned = false
            val active =
                launch {
                    gate.run {
                        try {
                            awaitCancellation()
                        } finally {
                            cleaned = true
                        }
                    }
                }
            runCurrent()
            val next = async { gate.run { cleaned } }
            runCurrent()

            active.cancelAndJoin()

            assertTrue(next.await())
        }

    @Test
    fun cancellingAWaitingExportDoesNotRunItOrLoseThePermit() =
        runTest {
            val active = launch { gate.run { awaitCancellation() } }
            runCurrent()
            var started = false
            val waiting = launch { gate.run { started = true } }
            runCurrent()

            waiting.cancelAndJoin()
            active.cancelAndJoin()

            assertFalse(started)
            assertEquals("available", gate.run { "available" })
        }
}
