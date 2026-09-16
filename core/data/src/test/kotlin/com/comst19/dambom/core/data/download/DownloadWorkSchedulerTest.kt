package com.comst19.dambom.core.data.download

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadWorkSchedulerTest {
    @Test
    fun `wifi setting selects the matching WorkManager network constraint`() {
        assertEquals(NetworkType.CONNECTED, requiredNetworkType(wifiOnlyDownloads = false))
        assertEquals(NetworkType.UNMETERED, requiredNetworkType(wifiOnlyDownloads = true))
    }

    @Test
    fun `checkpoint rate is bounded by time without starving slow downloads`() {
        assertFalse(shouldCheckpoint(bytesSinceLastCheckpoint = 1024L * 1024L, millisSinceLastCheckpoint = 499L))
        assertTrue(shouldCheckpoint(bytesSinceLastCheckpoint = 512L * 1024L, millisSinceLastCheckpoint = 1_000L))
        assertTrue(shouldCheckpoint(bytesSinceLastCheckpoint = 32L * 1024L, millisSinceLastCheckpoint = 500L))
        assertFalse(shouldCheckpoint(bytesSinceLastCheckpoint = 0L, millisSinceLastCheckpoint = 1_000L))
        assertTrue(shouldCheckpoint(bytesSinceLastCheckpoint = 1024L * 1024L, millisSinceLastCheckpoint = 500L))
    }

    @Test
    fun `schedule waits for enqueue success`() =
        runTest {
            val operation = TestOperation()
            val workManager = RecordingWorkManager(enqueueOperations = ArrayDeque(listOf(operation)))
            val scheduler = WorkManagerDownloadScheduler(TestSettingsRepository, workManager)

            val scheduling = async { scheduler.schedule() }
            runCurrent()

            assertFalse(scheduling.isCompleted)
            operation.succeed()
            runCurrent()
            scheduling.await()
            assertEquals(listOf("enqueue:${ExistingWorkPolicy.APPEND_OR_REPLACE}"), workManager.calls)
        }

    @Test
    fun `ensure scheduled propagates enqueue failure`() =
        runTest {
            supervisorScope {
                val failure = IllegalStateException("enqueue failed")
                val operation = TestOperation()
                val workManager = RecordingWorkManager(enqueueOperations = ArrayDeque(listOf(operation)))
                val scheduler = WorkManagerDownloadScheduler(TestSettingsRepository, workManager)
                val scheduling = async { scheduler.ensureScheduled() }
                runCurrent()

                operation.fail(failure)

                try {
                    scheduling.await()
                    throw AssertionError("Expected enqueue failure")
                } catch (caught: IllegalStateException) {
                    assertEquals(failure.message, caught.message)
                }
            }
        }

    @Test
    fun `reschedule waits for cancel before enqueue and then waits for enqueue`() =
        runTest {
            val cancel = TestOperation()
            val enqueue = TestOperation()
            val workManager =
                RecordingWorkManager(
                    cancelOperation = cancel,
                    enqueueOperations = ArrayDeque(listOf(enqueue)),
                )
            val scheduler = WorkManagerDownloadScheduler(TestSettingsRepository, workManager)
            val scheduling = async { scheduler.reschedule() }
            runCurrent()

            assertEquals(listOf("cancel"), workManager.calls)
            assertFalse(scheduling.isCompleted)
            cancel.succeed()
            runCurrent()
            assertEquals(listOf("cancel", "enqueue:${ExistingWorkPolicy.REPLACE}"), workManager.calls)
            assertFalse(scheduling.isCompleted)
            enqueue.succeed()
            scheduling.await()
        }
}

private class RecordingWorkManager(
    private val cancelOperation: TestOperation = TestOperation().apply(TestOperation::succeed),
    private val enqueueOperations: ArrayDeque<TestOperation>,
) : DownloadWorkManager {
    val calls = mutableListOf<String>()

    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): Operation {
        calls += "enqueue:$existingWorkPolicy"
        return enqueueOperations.removeFirst()
    }

    override fun cancelUniqueWork(uniqueWorkName: String): Operation {
        calls += "cancel"
        return cancelOperation
    }
}

private class TestOperation : Operation {
    private val state = MutableLiveData<Operation.State>(Operation.IN_PROGRESS)
    private val result = SettableFuture.create<Operation.State.SUCCESS>()

    override fun getState(): LiveData<Operation.State> = state

    override fun getResult(): ListenableFuture<Operation.State.SUCCESS> = result

    fun succeed() {
        result.set(Operation.SUCCESS)
    }

    fun fail(failure: Throwable) {
        result.setException(failure)
    }
}

private object TestSettingsRepository : SettingsRepository {
    override val settings = flowOf(AppSettings())

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    ) = Unit

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit

    override suspend fun setDownloadLocation(
        enabled: Boolean,
        treeUri: String?,
    ) = Unit
}
