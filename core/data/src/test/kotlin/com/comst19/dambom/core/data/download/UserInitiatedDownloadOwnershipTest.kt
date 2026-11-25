package com.comst19.dambom.core.data.download

import android.app.job.JobParameters
import android.content.Context
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.SharedLinkQuality
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserInitiatedDownloadOwnershipTest {
    private lateinit var context: Context
    private lateinit var database: DambomDatabase
    private lateinit var fileStore: DownloadFileStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, DambomDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        fileStore = DownloadFileStore(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.filesDir.resolve("download-parts").deleteRecursively()
        context.filesDir.resolve("videos").deleteRecursively()
    }

    @Test
    fun `replaced previous token cannot overwrite active job or enter queue`() =
        runTest {
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = OwnershipTestUidtBackend()
            val oldToken = Any()
            val newToken = Any()

            coordinator.onJobStarting(oldToken, wifiOnlyDownloads = true)
            coordinator.onJobStarting(newToken, wifiOnlyDownloads = true)

            var oldQueueCalls = 0
            val oldFinished = mutableListOf<Boolean>()
            coordinator.executeJob(
                token = oldToken,
                wifiOnlyDownloads = true,
                runQueue = {
                    oldQueueCalls++
                    DownloadQueueRunResult.COMPLETED
                },
                hasPendingWork = { false },
                onJobFinished = { oldFinished += it },
            )

            assertEquals(0, oldQueueCalls)
            assertTrue(oldFinished.isEmpty())
            assertTrue(coordinator.schedule(wifiOnlyDownloads = true, estimatedDownloadBytes = 0L, backend = backend))
            assertTrue(backend.scheduleCalls.isEmpty())

            var newQueueCalls = 0
            val newFinished = mutableListOf<Boolean>()
            coordinator.executeJob(
                token = newToken,
                wifiOnlyDownloads = true,
                runQueue = {
                    newQueueCalls++
                    DownloadQueueRunResult.COMPLETED
                },
                hasPendingWork = { false },
                onJobFinished = { newFinished += it },
            )

            assertEquals(2, newQueueCalls)
            assertEquals(listOf(false), newFinished)

            coordinator.executeJob(
                token = oldToken,
                wifiOnlyDownloads = true,
                runQueue = {
                    oldQueueCalls++
                    DownloadQueueRunResult.COMPLETED
                },
                hasPendingWork = { false },
                onJobFinished = { oldFinished += it },
            )
            assertEquals(0, oldQueueCalls)
            assertTrue(oldFinished.isEmpty())
        }

    @Test
    fun `initial DAO read failure releases ownership finishes once without reschedule and allows rescheduling`() =
        runTest {
            val baseDao = database.downloadTaskDao()
            val failingDao =
                object : DownloadTaskDao by baseDao {
                    override suspend fun resetInterrupted(updatedAtMillis: Long) = error("Initial DAO read failed")
                }
            val settings = OwnershipTestSettingsRepository(wifiOnlyDownloads = true)
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = OwnershipTestUidtBackend()
            val scheduler =
                AdaptiveDownloadWorkScheduler(
                    settingsRepository = settings,
                    dao = baseDao,
                    workManagerScheduler = WorkManagerDownloadScheduler(settings, OwnershipTestWorkManager()),
                    uidtCoordinator = coordinator,
                    uidtBackend = backend,
                    sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                )
            val runner = createRunner(failingDao)

            scheduler.schedule()
            assertEquals(1, backend.scheduleCalls.size)

            val token = Any()
            val finishedResults = mutableListOf<Boolean>()
            coordinator.onJobStarting(token, wifiOnlyDownloads = true)
            val error =
                runCatching {
                    coordinator.executeJob(
                        token = token,
                        wifiOnlyDownloads = true,
                        runQueue = { runner.run() },
                        hasPendingWork = { baseDao.countSchedulable() > 0 },
                        onJobFinished = { finishedResults += it },
                    )
                }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertEquals(listOf(false), finishedResults)

            scheduler.schedule()
            assertEquals(2, backend.scheduleCalls.size)
        }

    @Test
    fun `runner cleanup DAO failure releases ownership finishes once without reschedule and allows rescheduling`() =
        runTest {
            val baseDao = database.downloadTaskDao()
            val resetCalls = AtomicInteger()
            val cleanupFailingDao =
                object : DownloadTaskDao by baseDao {
                    override suspend fun resetInterrupted(updatedAtMillis: Long) {
                        if (resetCalls.incrementAndGet() == 2) error("Cleanup DAO failure")
                        baseDao.resetInterrupted(updatedAtMillis)
                    }
                }
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = OwnershipTestUidtBackend()
            val runner = createRunner(cleanupFailingDao)
            val token = Any()
            val finishedResults = mutableListOf<Boolean>()

            assertTrue(coordinator.schedule(wifiOnlyDownloads = true, estimatedDownloadBytes = 0L, backend = backend))
            coordinator.onJobStarting(token, wifiOnlyDownloads = true)
            val error =
                runCatching {
                    coordinator.executeJob(
                        token = token,
                        wifiOnlyDownloads = true,
                        runQueue = { runner.run() },
                        hasPendingWork = { baseDao.countSchedulable() > 0 },
                        onJobFinished = { finishedResults += it },
                    )
                }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertEquals(listOf(false), finishedResults)
            assertTrue(coordinator.schedule(wifiOnlyDownloads = true, estimatedDownloadBytes = 0L, backend = backend))
            assertEquals(2, backend.scheduleCalls.size)
        }

    @Test
    fun `hasPendingWork failure releases ownership finishes once without reschedule and allows rescheduling`() =
        runTest {
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = OwnershipTestUidtBackend()
            val token = Any()
            val finishedResults = mutableListOf<Boolean>()

            assertTrue(coordinator.schedule(wifiOnlyDownloads = true, estimatedDownloadBytes = 0L, backend = backend))
            coordinator.onJobStarting(token, wifiOnlyDownloads = true)
            val error =
                runCatching {
                    coordinator.executeJob(
                        token = token,
                        wifiOnlyDownloads = true,
                        runQueue = { DownloadQueueRunResult.COMPLETED },
                        hasPendingWork = { error("countSchedulable failed") },
                        onJobFinished = { finishedResults += it },
                    )
                }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertEquals(listOf(false), finishedResults)
            assertTrue(coordinator.schedule(wifiOnlyDownloads = true, estimatedDownloadBytes = 0L, backend = backend))
            assertEquals(2, backend.scheduleCalls.size)
        }

    @Test
    fun `job service execution does not swallow cancellation during WorkManager cancel await`() =
        runTest {
            val coordinator = UserInitiatedDownloadCoordinator()
            val oldToken = Any()
            val newToken = Any()
            val cancelStarted = CompletableDeferred<Unit>()
            val neverCompletes = CompletableDeferred<Unit>()
            val oldStopped = AtomicBoolean(false)
            var oldQueueCalls = 0
            val oldFinished = mutableListOf<Boolean>()
            val oldFailures = mutableListOf<Throwable>()

            coordinator.onJobStarting(oldToken, wifiOnlyDownloads = true)
            val oldJob =
                launch(Dispatchers.IO) {
                    runUidtJobExecution(
                        token = oldToken,
                        wifiOnlyDownloads = true,
                        cancelLegacyWork = {
                            cancelStarted.complete(Unit)
                            neverCompletes.await()
                        },
                        isStopped = { oldStopped.get() },
                        coordinator = coordinator,
                        runQueue = {
                            oldQueueCalls++
                            DownloadQueueRunResult.COMPLETED
                        },
                        hasPendingWork = { false },
                        onJobFinished = { oldFinished += it },
                        onFailure = { oldFailures += it },
                    )
                }

            withContext(Dispatchers.IO) { withTimeout(5_000L) { cancelStarted.await() } }
            oldStopped.set(true)
            coordinator.onJobStopped(oldToken)
            oldJob.cancel()
            oldJob.join()

            assertEquals(0, oldQueueCalls)
            assertTrue(oldFinished.isEmpty())
            assertTrue(oldFailures.isEmpty())

            var newQueueCalls = 0
            val newFinished = mutableListOf<Boolean>()
            coordinator.onJobStarting(newToken, wifiOnlyDownloads = true)
            runUidtJobExecution(
                token = newToken,
                wifiOnlyDownloads = true,
                cancelLegacyWork = {},
                isStopped = { false },
                coordinator = coordinator,
                runQueue = {
                    newQueueCalls++
                    DownloadQueueRunResult.COMPLETED
                },
                hasPendingWork = { false },
                onJobFinished = { newFinished += it },
            )

            assertEquals(1, newQueueCalls)
            assertEquals(listOf(false), newFinished)
        }

    @Test
    fun `job service execution handles queue failure with single non-rescheduling jobFinished`() =
        runTest {
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = OwnershipTestUidtBackend()
            val token = Any()
            val finishedResults = mutableListOf<Boolean>()
            val failures = mutableListOf<Throwable>()

            assertTrue(coordinator.schedule(wifiOnlyDownloads = true, estimatedDownloadBytes = 0L, backend = backend))
            coordinator.onJobStarting(token, wifiOnlyDownloads = true)
            runUidtJobExecution(
                token = token,
                wifiOnlyDownloads = true,
                cancelLegacyWork = { error("WorkManager cancel warning") },
                isStopped = { false },
                coordinator = coordinator,
                runQueue = { error("Queue crashed") },
                hasPendingWork = { false },
                onJobFinished = { finishedResults += it },
                onFailure = { failures += it },
            )

            assertEquals(1, failures.size)
            assertEquals(listOf(false), finishedResults)
            assertTrue(coordinator.schedule(wifiOnlyDownloads = true, estimatedDownloadBytes = 0L, backend = backend))
            assertEquals(2, backend.scheduleCalls.size)
        }

    @Test
    fun `system stop skips jobFinished even if teardown throws and user stop does not reschedule`() =
        runTest {
            assertFalse(shouldRescheduleUidtOnStop(JobParameters.STOP_REASON_CANCELLED_BY_APP))
            assertFalse(shouldRescheduleUidtOnStop(JobParameters.STOP_REASON_USER))
            assertTrue(shouldRescheduleUidtOnStop(JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY))
            assertTrue(shouldRescheduleUidtOnStop(JobParameters.STOP_REASON_TIMEOUT))

            val coordinator = UserInitiatedDownloadCoordinator()
            val token = Any()
            val finishedResults = mutableListOf<Boolean>()
            coordinator.onJobStarting(token, wifiOnlyDownloads = true)

            val error =
                runCatching {
                    coordinator.executeJob(
                        token = token,
                        wifiOnlyDownloads = true,
                        runQueue = {
                            coordinator.onJobStopped(token)
                            error("Teardown error after system stop")
                        },
                        hasPendingWork = { false },
                        onJobFinished = { finishedResults += it },
                    )
                }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertTrue(finishedResults.isEmpty())
        }

    @Test
    fun `new request handed off to active job reschedules once on failure without infinite retry`() =
        runTest {
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = OwnershipTestUidtBackend()
            val firstToken = Any()
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val firstFinished = mutableListOf<Boolean>()

            coordinator.onJobStarting(firstToken, wifiOnlyDownloads = true)
            val firstJob =
                launch(Dispatchers.IO) {
                    runUidtJobExecution(
                        token = firstToken,
                        wifiOnlyDownloads = true,
                        cancelLegacyWork = {},
                        isStopped = { false },
                        coordinator = coordinator,
                        runQueue = {
                            firstStarted.complete(Unit)
                            releaseFirst.await()
                            error("Transient failure while handling handoff")
                        },
                        hasPendingWork = { false },
                        onJobFinished = { firstFinished += it },
                    )
                }

            withContext(Dispatchers.IO) { withTimeout(5_000L) { firstStarted.await() } }
            val acceptedByActive =
                coordinator.schedule(
                    wifiOnlyDownloads = true,
                    estimatedDownloadBytes = 1024L,
                    backend = backend,
                )
            assertTrue(acceptedByActive)
            assertTrue(backend.scheduleCalls.isEmpty())

            releaseFirst.complete(Unit)
            firstJob.join()

            assertEquals(listOf(true), firstFinished)

            // Follow-up job with no new request stops without rescheduling when the failure persists.
            val secondToken = Any()
            val secondFinished = mutableListOf<Boolean>()
            coordinator.onJobStarting(secondToken, wifiOnlyDownloads = true)
            runUidtJobExecution(
                token = secondToken,
                wifiOnlyDownloads = true,
                cancelLegacyWork = {},
                isStopped = { false },
                coordinator = coordinator,
                runQueue = { error("Persistent failure") },
                hasPendingWork = { false },
                onJobFinished = { secondFinished += it },
            )

            assertEquals(listOf(false), secondFinished)
        }

    @Test
    fun `stale or unregistered token cannot enter queue even after 70 subsequent jobs finish`() =
        runTest {
            val coordinator = UserInitiatedDownloadCoordinator()
            val originalToken = Any()
            coordinator.onJobStarting(originalToken, wifiOnlyDownloads = true)

            repeat(70) {
                val token = Any()
                coordinator.onJobStarting(token, wifiOnlyDownloads = true)
                coordinator.executeJob(
                    token = token,
                    wifiOnlyDownloads = true,
                    runQueue = { DownloadQueueRunResult.COMPLETED },
                    hasPendingWork = { false },
                    onJobFinished = {},
                )
            }

            var staleQueueCalls = 0
            val staleFinished = mutableListOf<Boolean>()
            coordinator.executeJob(
                token = originalToken,
                wifiOnlyDownloads = true,
                runQueue = {
                    staleQueueCalls++
                    DownloadQueueRunResult.COMPLETED
                },
                hasPendingWork = { false },
                onJobFinished = { staleFinished += it },
            )
            coordinator.executeJob(
                token = Any(),
                wifiOnlyDownloads = true,
                runQueue = {
                    staleQueueCalls++
                    DownloadQueueRunResult.COMPLETED
                },
                hasPendingWork = { false },
                onJobFinished = { staleFinished += it },
            )

            assertEquals(0, staleQueueCalls)
            assertTrue(staleFinished.isEmpty())
        }

    private fun createRunner(dao: DownloadTaskDao): DownloadQueueRunner =
        DownloadQueueRunner(
            appContext = context,
            dao = dao,
            client = OkHttpClient(),
            fileStore = fileStore,
            notifier = DownloadNotifier(context),
            ioDispatcher = Dispatchers.IO,
        )
}

private class OwnershipTestUidtBackend : UidtJobBackend {
    val scheduleCalls = mutableListOf<Pair<Boolean, Long>>()
    var pendingPolicy: Boolean? = null

    override fun schedule(
        wifiOnlyDownloads: Boolean,
        estimatedDownloadBytes: Long,
    ): Boolean {
        scheduleCalls += wifiOnlyDownloads to estimatedDownloadBytes
        pendingPolicy = wifiOnlyDownloads
        return true
    }

    override fun hasPendingJob(wifiOnlyDownloads: Boolean): Boolean = pendingPolicy == wifiOnlyDownloads

    override fun cancel() {
        pendingPolicy = null
    }
}

private class OwnershipTestWorkManager : DownloadWorkManager {
    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): Operation = completedOperation()

    override fun cancelUniqueWork(uniqueWorkName: String): Operation = completedOperation()

    private fun completedOperation(): Operation =
        object : Operation {
            private val state = MutableLiveData<Operation.State>(Operation.SUCCESS)
            private val future =
                SettableFuture.create<Operation.State.SUCCESS>().apply {
                    set(Operation.SUCCESS)
                }

            override fun getState(): LiveData<Operation.State> = state

            override fun getResult(): ListenableFuture<Operation.State.SUCCESS> = future
        }
}

private class OwnershipTestSettingsRepository(
    wifiOnlyDownloads: Boolean,
) : SettingsRepository {
    private val _settings = MutableStateFlow(AppSettings(wifiOnlyDownloads = wifiOnlyDownloads))
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun setPlaybackSpeed(speed: Float) = Unit

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    ) = Unit

    override suspend fun setSharedLinkAutoDownload(enabled: Boolean) = Unit

    override suspend fun setSharedLinkQuality(
        quality: SharedLinkQuality,
        resolution: Int,
    ) = Unit

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        _settings.value = _settings.value.copy(wifiOnlyDownloads = enabled)
    }

    override suspend fun setDownloadLocation(
        enabled: Boolean,
        treeUri: String?,
    ) = Unit
}
