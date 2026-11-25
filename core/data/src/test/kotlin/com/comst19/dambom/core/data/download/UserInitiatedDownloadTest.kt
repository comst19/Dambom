package com.comst19.dambom.core.data.download

import android.app.job.JobInfo
import android.content.Context
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.comst19.dambom.core.data.repository.DefaultDownloadRepository
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.domain.model.SharedLinkQuality
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserInitiatedDownloadTest {
    private lateinit var context: Context
    private lateinit var database: DambomDatabase
    private lateinit var fileStore: DownloadFileStore
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, DambomDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        fileStore = DownloadFileStore(context)
        ShadowStatFs.registerStats(context.filesDir.resolve("download-parts").path, 1_000_000, 1_000_000, 1_000_000)
        server = MockWebServer().apply(MockWebServer::start)
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
        context.filesDir.resolve("download-parts").deleteRecursively()
        context.filesDir.resolve("videos").deleteRecursively()
    }

    @Test
    fun `buildUidtJobInfo configures user initiated persisted network and storage constraints`() {
        val wifiOnlyJob = buildUidtJobInfo(context, wifiOnlyDownloads = true, estimatedDownloadBytes = 50_000_000L)
        assertEquals(DOWNLOAD_UIDT_JOB_ID, wifiOnlyJob.id)
        assertTrue(wifiOnlyJob.isUserInitiated)
        assertTrue(wifiOnlyJob.isPersisted)
        assertTrue(wifiOnlyJob.isRequireStorageNotLow)
        assertEquals(true, wifiOnlyJob.extras.getBoolean(EXTRA_WIFI_ONLY_DOWNLOADS))
        assertTrue(wifiOnlyJob.requiredNetwork!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        assertEquals(50_000_000L, wifiOnlyJob.estimatedNetworkDownloadBytes)
        assertEquals(MIN_NETWORK_CHUNK_BYTES, wifiOnlyJob.minimumNetworkChunkBytes)
        assertEquals(JobInfo.BACKOFF_POLICY_EXPONENTIAL, wifiOnlyJob.backoffPolicy)

        val anyNetworkJob = buildUidtJobInfo(context, wifiOnlyDownloads = false, estimatedDownloadBytes = 0L)
        assertTrue(anyNetworkJob.isUserInitiated)
        assertEquals(false, anyNetworkJob.extras.getBoolean(EXTRA_WIFI_ONLY_DOWNLOADS))
        assertFalse(anyNetworkJob.requiredNetwork!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        assertEquals(DEFAULT_ESTIMATED_DOWNLOAD_BYTES, anyNetworkJob.estimatedNetworkDownloadBytes)
    }

    @Test
    fun `adaptive scheduler dispatches API 34+ to UIDT and API 33 to WorkManager`() =
        runTest {
            val settings = MutableSettingsRepository(wifiOnlyDownloads = true)
            val workManager = RecordingUidtWorkManager()
            val workScheduler = WorkManagerDownloadScheduler(settings, workManager)
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = FakeUidtJobBackend()
            val dao = database.downloadTaskDao()
            dao.insert(testEntity("video-1", server.url("/video-1.mp4").toString(), expectedBytes = 1_000_000L))

            val api34Scheduler =
                AdaptiveDownloadWorkScheduler(
                    settingsRepository = settings,
                    dao = dao,
                    workManagerScheduler = workScheduler,
                    uidtCoordinator = coordinator,
                    uidtBackend = backend,
                    sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                )
            api34Scheduler.schedule()

            assertEquals(listOf("cancel"), workManager.calls)
            assertEquals(1, backend.scheduleCalls.size)
            assertEquals(true to 1_000_000L, backend.scheduleCalls.single())

            val api33WorkManager = RecordingUidtWorkManager()
            val api33Scheduler =
                AdaptiveDownloadWorkScheduler(
                    settingsRepository = settings,
                    dao = dao,
                    workManagerScheduler = WorkManagerDownloadScheduler(settings, api33WorkManager),
                    uidtCoordinator = UserInitiatedDownloadCoordinator(),
                    uidtBackend = FakeUidtJobBackend(),
                    sdkInt = Build.VERSION_CODES.TIRAMISU,
                )
            api33Scheduler.schedule()

            assertEquals(listOf("enqueue:${ExistingWorkPolicy.APPEND_OR_REPLACE}"), api33WorkManager.calls)
        }

    @Test
    fun `adding file B while file A is downloading does not interrupt file A`() =
        runTest {
            val fileAStarted = CountDownLatch(1)
            val releaseFileA = CountDownLatch(1)
            val fileARequests = AtomicInteger()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        when (request.path) {
                            "/a.mp4" -> {
                                fileARequests.incrementAndGet()
                                fileAStarted.countDown()
                                releaseFileA.await(5, TimeUnit.SECONDS)
                                MockResponse()
                                    .setHeader("Content-Type", "video/mp4")
                                    .setBody("file-a-content")
                            }

                            "/b.mp4" -> {
                                MockResponse()
                                    .setHeader("Content-Type", "video/mp4")
                                    .setBody("file-b-content")
                            }

                            else -> {
                                MockResponse().setResponseCode(404)
                            }
                        }
                }

            val dao = database.downloadTaskDao()
            val settings = MutableSettingsRepository(wifiOnlyDownloads = true)
            val workManager = RecordingUidtWorkManager()
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = FakeUidtJobBackend()
            val scheduler =
                AdaptiveDownloadWorkScheduler(
                    settingsRepository = settings,
                    dao = dao,
                    workManagerScheduler = WorkManagerDownloadScheduler(settings, workManager),
                    uidtCoordinator = coordinator,
                    uidtBackend = backend,
                    sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                )
            val runner = createRunner(dao)

            val taskA = testEntity("task-a", server.url("/a.mp4").toString())
            val taskB = testEntity("task-b", server.url("/b.mp4").toString())
            dao.insert(taskA)
            scheduler.schedule()
            assertEquals(1, backend.scheduleCalls.size)

            val finishedResults = mutableListOf<Boolean>()
            val jobToken = Any()
            val execution =
                async(Dispatchers.IO) {
                    coordinator.executeJob(
                        token = jobToken,
                        wifiOnlyDownloads = true,
                        runQueue = { runner.run() },
                        hasPendingWork = { dao.countSchedulable() > 0 },
                        onJobFinished = { finishedResults += it },
                    )
                }

            assertTrue(fileAStarted.await(5, TimeUnit.SECONDS))
            dao.insert(taskB)
            scheduler.schedule()

            // File A stays uninterrupted: JobScheduler.schedule() must NOT be called again.
            assertEquals(1, backend.scheduleCalls.size)
            releaseFileA.countDown()
            execution.await()

            assertEquals(1, fileARequests.get())
            assertEquals(listOf(false), finishedResults)
            assertEquals(DownloadStatus.COMPLETED.name, dao.getById(taskA.id)?.status)
            assertEquals(DownloadStatus.COMPLETED.name, dao.getById(taskB.id)?.status)
        }

    @Test
    fun `file B added between last queue read and jobFinished is drained without being stranded`() =
        runTest {
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        when (request.path) {
                            "/a.mp4" -> MockResponse().setHeader("Content-Type", "video/mp4").setBody("aaa")
                            "/b.mp4" -> MockResponse().setHeader("Content-Type", "video/mp4").setBody("bbb")
                            else -> MockResponse().setResponseCode(404)
                        }
                }

            val dao = database.downloadTaskDao()
            val settings = MutableSettingsRepository(wifiOnlyDownloads = true)
            val workManager = RecordingUidtWorkManager()
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = FakeUidtJobBackend()
            val scheduler =
                AdaptiveDownloadWorkScheduler(
                    settingsRepository = settings,
                    dao = dao,
                    workManagerScheduler = WorkManagerDownloadScheduler(settings, workManager),
                    uidtCoordinator = coordinator,
                    uidtBackend = backend,
                    sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                )
            val runner = createRunner(dao)

            val taskA = testEntity("task-a", server.url("/a.mp4").toString())
            val taskB = testEntity("task-b", server.url("/b.mp4").toString())
            dao.insert(taskA)
            scheduler.schedule()
            assertEquals(1, backend.scheduleCalls.size)

            val injectedB = AtomicBoolean(false)
            val finishedResults = mutableListOf<Boolean>()
            coordinator.executeJob(
                token = Any(),
                wifiOnlyDownloads = true,
                runQueue = { runner.run() },
                hasPendingWork = { dao.countSchedulable() > 0 },
                onJobFinished = { finishedResults += it },
                onBeforeFinishCheck = {
                    if (injectedB.compareAndSet(false, true)) {
                        // Exact closing window: drainQueue() saw empty queue, jobFinished() has not run yet.
                        dao.insert(taskB)
                        scheduler.schedule()
                    }
                },
            )

            assertEquals(1, backend.scheduleCalls.size)
            assertEquals(listOf(false), finishedResults)
            assertEquals(DownloadStatus.COMPLETED.name, dao.getById(taskA.id)?.status)
            assertEquals(DownloadStatus.COMPLETED.name, dao.getById(taskB.id)?.status)
        }

    @Test
    fun `legacy worker yields on API 34+ preserving partial file and UIDT resumes with HTTP 206`() =
        runTest {
            val dao = database.downloadTaskDao()
            val task = testEntity("upgrade-resume", server.url("/movie.mp4").toString())
            dao.insert(task)
            fileStore.partialFile(task.id).writeBytes("0123456789".toByteArray())
            fileStore.partialValidatorFile(task.id).writeText("\"etag-v1\"")

            val legacyWorker =
                TestListenableWorkerBuilder<DownloadQueueWorker>(context)
                    .setWorkerFactory(
                        object : WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: WorkerParameters,
                            ): ListenableWorker =
                                DownloadQueueWorker(
                                    appContext = appContext,
                                    params = workerParameters,
                                    dao = dao,
                                    client = OkHttpClient(),
                                    fileStore = fileStore,
                                    notifier = DownloadNotifier(appContext),
                                    ioDispatcher = Dispatchers.IO,
                                    useUserInitiatedTransfers = true,
                                )
                        },
                    ).build()

            assertEquals(ListenableWorker.Result.success(), legacyWorker.doWork())
            assertEquals(DownloadStatus.QUEUED.name, dao.getById(task.id)?.status)
            assertEquals(10L, fileStore.partialFile(task.id).length())

            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Range", "bytes 10-19/20")
                    .setHeader("ETag", "\"etag-v1\"")
                    .setBody("abcdefghij"),
            )
            val runner = createRunner(dao)
            assertEquals(DownloadQueueRunResult.COMPLETED, runner.run())

            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("bytes=10-", request.getHeader("Range"))
            assertEquals("\"etag-v1\"", request.getHeader("If-Range"))
            assertEquals(DownloadStatus.COMPLETED.name, dao.getById(task.id)?.status)
            assertEquals(
                "0123456789abcdefghij",
                fileStore.completedFile(task.id, task.url, task.mimeType).readText(),
            )
        }

    @Test
    fun `runner serializes overlapping execution so previous finally cleanup finishes before next run`() =
        runTest {
            val baseDao = database.downloadTaskDao()
            val firstFinallyStarted = CompletableDeferred<Unit>()
            val releaseFirstFinally = CompletableDeferred<Unit>()
            val secondRunStarted = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val resetCount = AtomicInteger()

            val trackedDao =
                object : DownloadTaskDao by baseDao {
                    override suspend fun resetInterrupted(updatedAtMillis: Long) {
                        val call = resetCount.incrementAndGet()
                        when (call) {
                            2 -> {
                                events += "first-finally-start"
                                firstFinallyStarted.complete(Unit)
                                releaseFirstFinally.await()
                                baseDao.resetInterrupted(updatedAtMillis)
                                events += "first-finally-end"
                            }

                            3 -> {
                                events += "second-run-start"
                                secondRunStarted.complete(Unit)
                                baseDao.resetInterrupted(updatedAtMillis)
                            }

                            else -> {
                                baseDao.resetInterrupted(updatedAtMillis)
                            }
                        }
                    }
                }

            val runner = createRunner(trackedDao)
            val firstRun = async(Dispatchers.IO) { runner.run() }
            withContext(Dispatchers.IO) { withTimeout(5_000L) { firstFinallyStarted.await() } }

            val secondRun = async(Dispatchers.IO) { runner.run() }
            withContext(Dispatchers.IO) { delay(50L) }
            assertFalse(secondRunStarted.isCompleted)

            releaseFirstFinally.complete(Unit)
            firstRun.await()
            secondRun.await()

            assertEquals(
                listOf("first-finally-start", "first-finally-end", "second-run-start"),
                events,
            )
        }

    @Test
    fun `RESULT_FAILURE leaves work queued and recovers on next visible ensurePendingDownloadsScheduled`() =
        runTest {
            val dao = database.downloadTaskDao()
            val settings = MutableSettingsRepository(wifiOnlyDownloads = true)
            val workManager = RecordingUidtWorkManager()
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = FakeUidtJobBackend(nextScheduleResults = ArrayDeque(listOf(false, true)))
            val scheduler =
                AdaptiveDownloadWorkScheduler(
                    settingsRepository = settings,
                    dao = dao,
                    workManagerScheduler = WorkManagerDownloadScheduler(settings, workManager),
                    uidtCoordinator = coordinator,
                    uidtBackend = backend,
                    sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                )
            val repository =
                DefaultDownloadRepository(
                    dao = dao,
                    scheduler = scheduler,
                    fileStore = fileStore,
                    ioDispatcher = Dispatchers.IO,
                    privateVideoSession = PrivateVideoSession(),
                )

            val task = testEntity("recover-after-failure", server.url("/recover.mp4").toString())
            dao.insert(task)
            fileStore.partialFile(task.id).writeBytes("partial".toByteArray())

            scheduler.schedule()
            assertTrue(coordinator.hasPendingRecoverySchedule())
            assertEquals(DownloadStatus.QUEUED.name, dao.getById(task.id)?.status)
            assertEquals(7L, fileStore.partialFile(task.id).length())

            // Next visible state recovers the pending schedule.
            repository.ensurePendingDownloadsScheduled()
            assertFalse(coordinator.hasPendingRecoverySchedule())
            assertEquals(2, backend.scheduleCalls.size)
        }

    @Test
    fun `network-bound download routes sockets and DNS through the provided job network`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/mp4")
                    .setBody("network-bound-payload"),
            )
            val dao = database.downloadTaskDao()
            val task = testEntity("bound-network", server.url("/bound.mp4").toString())
            dao.insert(task)

            val binding = RecordingNetworkBinding()
            val runner = createRunner(dao)

            assertEquals(
                DownloadQueueRunResult.COMPLETED,
                runner.run(networkSession = DownloadNetworkSession(binding)),
            )
            assertTrue(binding.socketCreations.get() > 0)
            assertTrue(binding.dnsLookups.get() > 0)
            assertEquals(DownloadStatus.COMPLETED.name, dao.getById(task.id)?.status)
        }

    @Test
    fun `repeated connectivity constraint losses do not increment retryCount and fourth attempt completes`() =
        runTest {
            val payload = ByteArray(128 * 1024) { index -> (index % 251).toByte() }
            val constraintSatisfied = AtomicBoolean(true)
            val disconnectOnNextRequest = AtomicBoolean(true)
            val requestsServed = AtomicInteger()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val attempt = requestsServed.incrementAndGet()
                        val start =
                            request
                                .getHeader("Range")
                                ?.removePrefix("bytes=")
                                ?.removeSuffix("-")
                                ?.toIntOrNull()
                                ?: 0
                        if (disconnectOnNextRequest.get() && attempt <= 3) {
                            constraintSatisfied.set(false)
                            val chunkEnd = (start + 16 * 1024).coerceAtMost(payload.size)
                            val chunk = payload.copyOfRange(start, chunkEnd)
                            val builder =
                                MockResponse()
                                    .setHeader("Content-Type", "video/mp4")
                                    .setHeader("ETag", "\"etag-repeat\"")
                                    .setHeader("Content-Length", (payload.size - start).toString())
                                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                                    .setBody(okio.Buffer().write(chunk))
                            if (start > 0) {
                                builder
                                    .setResponseCode(206)
                                    .setHeader("Content-Range", "bytes $start-${payload.lastIndex}/${payload.size}")
                            }
                            return builder
                        }
                        val remaining = payload.copyOfRange(start, payload.size)
                        return MockResponse()
                            .setResponseCode(if (start > 0) 206 else 200)
                            .setHeader("Content-Type", "video/mp4")
                            .setHeader("ETag", "\"etag-repeat\"")
                            .apply {
                                if (start > 0) {
                                    setHeader("Content-Range", "bytes $start-${payload.lastIndex}/${payload.size}")
                                }
                            }.setBody(okio.Buffer().write(remaining))
                    }
                }

            val dao = database.downloadTaskDao()
            val task = testEntity("repeat-disconnect", server.url("/repeat.mp4").toString())
            dao.insert(task)
            val binding = RecordingNetworkBinding(availableProvider = { constraintSatisfied.get() })
            val runner = createRunner(dao)

            repeat(3) {
                constraintSatisfied.set(true)
                val result =
                    runner.run(
                        networkSession = DownloadNetworkSession(binding),
                        isConstraintSatisfied = { binding.isAvailableFor(wifiOnlyDownloads = true) },
                    )
                assertEquals(DownloadQueueRunResult.RETRY, result)
                val stored = dao.getById(task.id)!!
                assertEquals(DownloadStatus.QUEUED.name, stored.status)
                assertEquals(0, stored.retryCount)
            }

            disconnectOnNextRequest.set(false)
            constraintSatisfied.set(true)
            val finalResult =
                runner.run(
                    networkSession = DownloadNetworkSession(binding),
                    isConstraintSatisfied = { binding.isAvailableFor(wifiOnlyDownloads = true) },
                )
            assertEquals(DownloadQueueRunResult.COMPLETED, finalResult)
            val completed = dao.getById(task.id)!!
            assertEquals(DownloadStatus.COMPLETED.name, completed.status)
            assertEquals(0, completed.retryCount)
            assertEquals(payload.size.toLong(), completed.downloadedBytes)
            assertTrue(
                payload.contentEquals(fileStore.completedFile(task.id, task.url, task.mimeType).readBytes()),
            )
        }

    @Test
    fun `delayed ConnectivityService update after socket IOException still avoids incrementing retryCount`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Length", "1024")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    .setBody("short"),
            )
            val dao = database.downloadTaskDao()
            val task = testEntity("delayed-constraint-loss", server.url("/delayed.mp4").toString())
            dao.insert(task)
            val checkCount = AtomicInteger()
            // First two checks (pre-transfer + immediate catch after IOException) still report true,
            // simulating ConnectivityService lagging ~50ms behind wlan0 teardown; recheck returns false.
            val binding = RecordingNetworkBinding(availableProvider = { checkCount.incrementAndGet() <= 3 })
            val runner = createRunner(dao)

            val result =
                runner.run(
                    networkSession = DownloadNetworkSession(binding),
                    isConstraintSatisfied = { binding.isAvailableFor(wifiOnlyDownloads = true) },
                    constraintRecheckDelayMillis = 25L,
                )
            assertEquals(DownloadQueueRunResult.RETRY, result)
            val stored = dao.getById(task.id)!!
            assertEquals(DownloadStatus.QUEUED.name, stored.status)
            assertEquals(0, stored.retryCount)
        }

    @Test
    fun `server IOException while network constraint stays satisfied still increments retryCount`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Length", "1024")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    .setBody("short"),
            )
            val dao = database.downloadTaskDao()
            val task = testEntity("real-io-error", server.url("/error.mp4").toString())
            dao.insert(task)
            val binding = RecordingNetworkBinding()
            val runner = createRunner(dao)

            val result =
                runner.run(
                    networkSession = DownloadNetworkSession(binding),
                    isConstraintSatisfied = { binding.isAvailableFor(wifiOnlyDownloads = true) },
                    constraintRecheckDelayMillis = 25L,
                )
            assertEquals(DownloadQueueRunResult.RETRY, result)
            val stored = dao.getById(task.id)!!
            assertEquals(DownloadStatus.QUEUED.name, stored.status)
            assertEquals(1, stored.retryCount)
        }

    @Test
    fun `missing job network defers transfer without falling back to default socket or DNS`() =
        runTest {
            val dao = database.downloadTaskDao()
            val task = testEntity("missing-network", server.url("/missing.mp4").toString())
            dao.insert(task)
            val runner = createRunner(dao)
            val missingSession = DownloadNetworkSession(initialNetwork = null)

            val result = runner.run(networkSession = missingSession)
            assertEquals(DownloadQueueRunResult.RETRY, result)
            assertEquals(0, server.requestCount)
            val stored = dao.getById(task.id)!!
            assertEquals(DownloadStatus.QUEUED.name, stored.status)
            assertEquals(0, stored.retryCount)

            val callFactory = OkHttpClient().bindToNetworkCallFactory(missingSession)
            val failure =
                runCatching {
                    callFactory.newCall(Request.Builder().url(server.url("/direct.mp4")).build()).execute()
                }.exceptionOrNull()
            assertTrue(failure is MissingJobNetworkException)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `switching network on DownloadNetworkSession evicts pooled connections and uses new network`() {
        server.enqueue(MockResponse().setBody("first"))
        server.enqueue(MockResponse().setBody("reused"))
        server.enqueue(MockResponse().setBody("second"))
        val binding1 = RecordingNetworkBinding()
        val binding2 = RecordingNetworkBinding()
        val session = DownloadNetworkSession(binding1)
        val callFactory = OkHttpClient().bindToNetworkCallFactory(session)
        try {
            callFactory.newCall(Request.Builder().url(server.url("/one")).build()).execute().use { response ->
                assertEquals("first", response.body.string())
            }
            val binding1CountAfterFirst = binding1.socketCreations.get()
            assertTrue(binding1CountAfterFirst > 0)
            assertEquals(0, binding2.socketCreations.get())

            // Before switching network, the second request reuses binding1's pooled keep-alive socket.
            callFactory.newCall(Request.Builder().url(server.url("/reused")).build()).execute().use { response ->
                assertEquals("reused", response.body.string())
            }
            assertEquals(binding1CountAfterFirst, binding1.socketCreations.get())

            assertTrue(session.update(binding2))
            callFactory.newCall(Request.Builder().url(server.url("/two")).build()).execute().use { response ->
                assertEquals("second", response.body.string())
            }
            assertEquals(binding1CountAfterFirst, binding1.socketCreations.get())
            assertTrue(binding2.socketCreations.get() > 0)
            assertEquals(0, server.takeRequest(5, TimeUnit.SECONDS)!!.sequenceNumber)
            assertEquals(1, server.takeRequest(5, TimeUnit.SECONDS)!!.sequenceNumber)
            assertEquals(0, server.takeRequest(5, TimeUnit.SECONDS)!!.sequenceNumber)
        } finally {
            session.evictAll()
        }
    }

    @Test
    fun `switching network while response on old network is still open uses new network pool for next request`() {
        server.enqueue(MockResponse().setBody("first"))
        server.enqueue(MockResponse().setBody("second"))
        val binding1 = RecordingNetworkBinding()
        val binding2 = RecordingNetworkBinding()
        val session = DownloadNetworkSession(binding1)
        val oldGeneration = session.currentGeneration()
        val callFactory = OkHttpClient().bindToNetworkCallFactory(session)
        try {
            val firstResponse = callFactory.newCall(Request.Builder().url(server.url("/one")).build()).execute()
            val binding1CountAfterOpen = binding1.socketCreations.get()
            assertTrue(binding1CountAfterOpen > 0)
            assertEquals(0, binding2.socketCreations.get())

            // Switch network BEFORE firstResponse is read or closed (in-flight connection on binding1).
            assertTrue(session.update(binding2))

            firstResponse.use { response ->
                assertEquals("first", response.body.string())
            }
            assertEquals(0, oldGeneration.connectionPool.idleConnectionCount())

            // Next request to the same host must open a new socket on binding2, never reusing binding1's socket.
            callFactory.newCall(Request.Builder().url(server.url("/two")).build()).execute().use { response ->
                assertEquals("second", response.body.string())
            }
            assertEquals(binding1CountAfterOpen, binding1.socketCreations.get())
            assertTrue(binding2.socketCreations.get() > 0)
            assertEquals(0, server.takeRequest(5, TimeUnit.SECONDS)!!.sequenceNumber)
            assertEquals(0, server.takeRequest(5, TimeUnit.SECONDS)!!.sequenceNumber)
        } finally {
            session.evictAll()
        }
    }

    @Test
    fun `adding file B while same-policy UIDT job is pending preserves pending job and drains both files`() =
        runTest {
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        when (request.path) {
                            "/a.mp4" -> MockResponse().setHeader("Content-Type", "video/mp4").setBody("aaa")
                            "/b.mp4" -> MockResponse().setHeader("Content-Type", "video/mp4").setBody("bbb")
                            else -> MockResponse().setResponseCode(404)
                        }
                }

            val dao = database.downloadTaskDao()
            val settings = MutableSettingsRepository(wifiOnlyDownloads = true)
            val workManager = RecordingUidtWorkManager()
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = FakeUidtJobBackend()
            val scheduler =
                AdaptiveDownloadWorkScheduler(
                    settingsRepository = settings,
                    dao = dao,
                    workManagerScheduler = WorkManagerDownloadScheduler(settings, workManager),
                    uidtCoordinator = coordinator,
                    uidtBackend = backend,
                    sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                )
            val runner = createRunner(dao)

            val taskA = testEntity("pending-a", server.url("/a.mp4").toString())
            val taskB = testEntity("pending-b", server.url("/b.mp4").toString())
            dao.insert(taskA)
            scheduler.schedule()
            assertEquals(1, backend.scheduleCalls.size)
            assertEquals(true, backend.pendingPolicy)

            dao.insert(taskB)
            scheduler.schedule()
            assertEquals(1, backend.scheduleCalls.size)

            val finishedResults = mutableListOf<Boolean>()
            coordinator.executeJob(
                token = Any(),
                wifiOnlyDownloads = true,
                runQueue = { runner.run() },
                hasPendingWork = { dao.countSchedulable() > 0 },
                onJobFinished = { finishedResults += it },
            )

            assertEquals(listOf(false), finishedResults)
            assertEquals(DownloadStatus.COMPLETED.name, dao.getById(taskA.id)?.status)
            assertEquals(DownloadStatus.COMPLETED.name, dao.getById(taskB.id)?.status)
        }

    @Test
    fun `scheduling right after jobFinished without reschedule ignores stale pending job`() =
        runTest {
            server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("aaa"))
            val dao = database.downloadTaskDao()
            val settings = MutableSettingsRepository(wifiOnlyDownloads = true)
            val workManager = RecordingUidtWorkManager()
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = FakeUidtJobBackend()
            val scheduler =
                AdaptiveDownloadWorkScheduler(
                    settingsRepository = settings,
                    dao = dao,
                    workManagerScheduler = WorkManagerDownloadScheduler(settings, workManager),
                    uidtCoordinator = coordinator,
                    uidtBackend = backend,
                    sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                )
            val runner = createRunner(dao)

            dao.insert(testEntity("stale-a", server.url("/a.mp4").toString()))
            scheduler.schedule()
            assertEquals(1, backend.scheduleCalls.size)

            // Leave backend.pendingPolicy == true even after jobFinished(false) to simulate async JobScheduler removal.
            coordinator.executeJob(
                token = Any(),
                wifiOnlyDownloads = true,
                runQueue = { runner.run() },
                hasPendingWork = { dao.countSchedulable() > 0 },
                onJobFinished = { _ -> },
            )

            dao.insert(testEntity("stale-b", server.url("/b.mp4").toString()))
            scheduler.schedule()
            assertEquals(2, backend.scheduleCalls.size)
        }

    @Test
    fun `reschedule cancels existing UIDT job and schedules with updated Wi-Fi policy`() =
        runTest {
            val dao = database.downloadTaskDao()
            val settings = MutableSettingsRepository(wifiOnlyDownloads = true)
            val workManager = RecordingUidtWorkManager()
            val coordinator = UserInitiatedDownloadCoordinator()
            val backend = FakeUidtJobBackend()
            val scheduler =
                AdaptiveDownloadWorkScheduler(
                    settingsRepository = settings,
                    dao = dao,
                    workManagerScheduler = WorkManagerDownloadScheduler(settings, workManager),
                    uidtCoordinator = coordinator,
                    uidtBackend = backend,
                    sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                )

            scheduler.schedule()
            settings.setWifiOnlyDownloads(false)
            scheduler.reschedule()

            assertEquals(1, backend.cancelCount)
            assertEquals(listOf(true to 0L, false to 0L), backend.scheduleCalls)
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

private class FakeUidtJobBackend(
    private val nextScheduleResults: ArrayDeque<Boolean> = ArrayDeque(),
) : UidtJobBackend {
    val scheduleCalls = mutableListOf<Pair<Boolean, Long>>()
    var cancelCount = 0
    var pendingPolicy: Boolean? = null

    override fun schedule(
        wifiOnlyDownloads: Boolean,
        estimatedDownloadBytes: Long,
    ): Boolean {
        scheduleCalls += wifiOnlyDownloads to estimatedDownloadBytes
        val succeeded = if (nextScheduleResults.isEmpty()) true else nextScheduleResults.removeFirst()
        pendingPolicy = if (succeeded) wifiOnlyDownloads else null
        return succeeded
    }

    override fun hasPendingJob(wifiOnlyDownloads: Boolean): Boolean = pendingPolicy == wifiOnlyDownloads

    override fun cancel() {
        cancelCount++
        pendingPolicy = null
    }
}

private class RecordingUidtWorkManager : DownloadWorkManager {
    val calls = mutableListOf<String>()

    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): Operation {
        calls += "enqueue:$existingWorkPolicy"
        return completedOperation()
    }

    override fun cancelUniqueWork(uniqueWorkName: String): Operation {
        calls += "cancel"
        return completedOperation()
    }

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

private class MutableSettingsRepository(
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

private class RecordingNetworkBinding(
    private val availableProvider: () -> Boolean = { true },
) : DownloadNetworkBinding {
    val socketCreations = AtomicInteger()
    val dnsLookups = AtomicInteger()

    override fun isAvailableFor(wifiOnlyDownloads: Boolean): Boolean = availableProvider()

    override val socketFactory: SocketFactory =
        object : SocketFactory() {
            private val delegate = getDefault()

            override fun createSocket(): Socket {
                socketCreations.incrementAndGet()
                return delegate.createSocket()
            }

            override fun createSocket(
                host: String,
                port: Int,
            ): Socket {
                socketCreations.incrementAndGet()
                return delegate.createSocket(host, port)
            }

            override fun createSocket(
                host: String,
                port: Int,
                localHost: InetAddress,
                localPort: Int,
            ): Socket {
                socketCreations.incrementAndGet()
                return delegate.createSocket(host, port, localHost, localPort)
            }

            override fun createSocket(
                host: InetAddress,
                port: Int,
            ): Socket {
                socketCreations.incrementAndGet()
                return delegate.createSocket(host, port)
            }

            override fun createSocket(
                address: InetAddress,
                port: Int,
                localAddress: InetAddress,
                localPort: Int,
            ): Socket {
                socketCreations.incrementAndGet()
                return delegate.createSocket(address, port, localAddress, localPort)
            }
        }

    override fun getAllByName(hostname: String): Array<InetAddress> {
        dnsLookups.incrementAndGet()
        return InetAddress.getAllByName(hostname)
    }
}

private fun testEntity(
    id: String,
    url: String,
    expectedBytes: Long? = null,
): DownloadTaskEntity =
    DownloadTaskEntity(
        id = id,
        url = url,
        sourcePageUrl = url,
        host = url.substringAfter("://").substringBefore(':').substringBefore('/'),
        title = id,
        mimeType = "video/mp4",
        expectedBytes = expectedBytes,
        downloadedBytes = 0L,
        quality = "original",
        status = DownloadStatus.QUEUED.name,
        failureReason = null,
        retryCount = 0,
        deletePending = false,
        localFileName = null,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
