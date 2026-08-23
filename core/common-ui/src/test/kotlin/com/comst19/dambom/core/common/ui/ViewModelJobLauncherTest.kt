package com.comst19.dambom.core.common.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelJobLauncherTest {
    @Test
    fun `같은 key는 실행 중인 작업을 유지하고 다른 key는 실행한다`() =
        runTest {
            val refreshGate = CompletableDeferred<Unit>()
            val launcher = ViewModelJobLauncher<JobKey>(this)
            var refreshCount = 0
            var saveCount = 0

            launcher.launchIfIdle(JobKey.Refresh) {
                refreshCount++
                refreshGate.await()
            }
            launcher.launchIfIdle(JobKey.Refresh) { refreshCount++ }
            launcher.launchIfIdle(JobKey.Save) { saveCount++ }
            runCurrent()

            assertEquals(1, refreshCount)
            assertEquals(1, saveCount)

            refreshGate.complete(Unit)
            advanceUntilIdle()
            launcher.launchIfIdle(JobKey.Refresh) { refreshCount++ }
            advanceUntilIdle()

            assertEquals(2, refreshCount)
        }

    private enum class JobKey {
        Refresh,
        Save,
    }
}
