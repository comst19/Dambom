package com.comst19.dambom.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.comst19.dambom.core.common.io.videoThumbnailFile
import com.comst19.dambom.core.common.io.videoThumbnailTemporaryFile
import com.comst19.dambom.core.common.io.videoThumbnailUnavailableFile
import com.comst19.dambom.core.data.download.DownloadFileStore
import com.comst19.dambom.core.data.download.DownloadWorkScheduler
import com.comst19.dambom.core.data.download.selectNextDownload
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultDownloadRepositoryTest {
    private lateinit var database: DambomDatabase
    private lateinit var scheduler: RecordingScheduler
    private lateinit var repository: DefaultDownloadRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, DambomDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        scheduler = RecordingScheduler()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `enqueue persists unique downloads and schedules work`() =
        runTest {
            repository =
                DefaultDownloadRepository(
                    dao = database.downloadTaskDao(),
                    scheduler = scheduler,
                    fileStore = DownloadFileStore(ApplicationProvider.getApplicationContext()),
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )
            val request = testRequest()

            val first = repository.enqueue(listOf(request))
            val duplicate = repository.enqueue(listOf(request))

            assertEquals(1, first.addedCount)
            assertEquals(1, duplicate.duplicateCount)
            assertEquals(1, scheduler.scheduleCount)
            assertEquals(
                DownloadStatus.QUEUED,
                repository
                    .downloads
                    .first()
                    .single()
                    .status,
            )
        }

    @Test
    fun `different quality jobs coexist while the same source quality stays duplicate`() =
        runTest {
            repository =
                DefaultDownloadRepository(
                    database.downloadTaskDao(),
                    scheduler,
                    DownloadFileStore(ApplicationProvider.getApplicationContext()),
                    StandardTestDispatcher(testScheduler),
                )
            val first = testRequest().copy(id = "quality-720", quality = "720p")
            val second = first.copy(id = "quality-1080", url = first.url + "?quality=1080", quality = "1080p")
            assertEquals(2, repository.enqueue(listOf(first, second)).addedCount)
            assertEquals(1, repository.enqueue(listOf(first.copy(id = "duplicate-request"))).duplicateCount)
            assertEquals(2, repository.downloads.first().size)
        }

    @Test
    fun `pause and resume persist state and reschedule work`() =
        runTest {
            repository =
                DefaultDownloadRepository(
                    dao = database.downloadTaskDao(),
                    scheduler = scheduler,
                    fileStore = DownloadFileStore(ApplicationProvider.getApplicationContext()),
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )
            repository.enqueue(listOf(testRequest()))

            repository.pause(TEST_ID)
            assertEquals(
                DownloadStatus.PAUSED,
                repository
                    .downloads
                    .first()
                    .single()
                    .status,
            )

            repository.resume(TEST_ID)
            assertEquals(
                DownloadStatus.QUEUED,
                repository
                    .downloads
                    .first()
                    .single()
                    .status,
            )
            assertEquals(2, scheduler.scheduleCount)
        }

    @Test
    fun `network policy refresh replaces work only while downloads are schedulable`() =
        runTest {
            repository = createRepository(testScheduler)
            repository.enqueue(listOf(testRequest()))

            repository.refreshNetworkPolicy()
            assertEquals(1, scheduler.rescheduleCount)

            repository.pause(TEST_ID)
            repository.refreshNetworkPolicy()
            assertEquals(1, scheduler.rescheduleCount)
        }

    @Test
    fun `paused download cannot be overwritten by a late failure`() =
        runTest {
            val dao = database.downloadTaskDao()
            dao.insert(entity(TEST_ID, "media.example").copy(status = DownloadStatus.DOWNLOADING.name))
            dao.pause(TEST_ID, 2L)

            dao.updateProgress(TEST_ID, 512L, 1024L, 3L)
            dao.markFailed(TEST_ID, "NETWORK", 3L)

            repository = createRepository(testScheduler)
            val task = repository.downloads.first().single()
            assertEquals(DownloadStatus.PAUSED, task.status)
            assertEquals(0L, task.downloadedBytes)
        }

    @Test
    fun `duplicate enqueue repairs scheduling after an earlier scheduler failure`() =
        runTest {
            repository = createRepository(testScheduler)
            scheduler.failSchedule = true
            assertTrue(runCatching { repository.enqueue(listOf(testRequest())) }.isFailure)
            scheduler.failSchedule = false

            val result = repository.enqueue(listOf(testRequest()))

            assertEquals(1, result.duplicateCount)
            assertEquals(1, scheduler.successfulEnsureCount)
        }

    @Test
    fun `unrelated row invalidates SQL without emitting duplicate detail data`() =
        runTest {
            database.close()
            val queryCount = AtomicInteger()
            val requery = CompletableDeferred<Unit>()
            database =
                Room
                    .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DambomDatabase::class.java)
                    .allowMainThreadQueries()
                    .setQueryCallback(
                        { sql, _ ->
                            if (
                                sql.startsWith("SELECT * FROM download_tasks WHERE id") &&
                                queryCount.incrementAndGet() > 1
                            ) {
                                requery.complete(Unit)
                            }
                        },
                        { it.run() },
                    ).build()
            repository = createRepository(testScheduler)
            val dao = database.downloadTaskDao()
            dao.insert(entity(TEST_ID, "media.example"))
            dao.insert(entity("other", "media.example"))

            repository.observeDownload(TEST_ID).test {
                assertEquals(TEST_ID, awaitItem()?.id)
                dao.updateTitle("other", "unrelated", 2L)
                requery.await()
                dao.updateTitle(TEST_ID, "renamed", 3L)
                assertEquals("renamed", awaitItem()?.title)
                assertTrue(queryCount.get() >= 2)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `single download subscription observes rename and deletion without unrelated rows`() =
        runTest {
            repository = createRepository(testScheduler)
            val dao = database.downloadTaskDao()
            dao.insert(entity(TEST_ID, "media.example"))
            dao.insert(entity("other", "media.example"))

            repository.observeDownload(TEST_ID).test {
                assertEquals(TEST_ID, awaitItem()?.id)
                dao.updateTitle("other", "unrelated", 2L)
                dao.updateTitle(TEST_ID, "renamed", 3L)
                val renamed = awaitItem()
                assertEquals(TEST_ID, renamed?.id)
                assertEquals("renamed", renamed?.title)
                dao.delete(TEST_ID)
                assertEquals(null, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `single download observation returns only requested row and tracks deletion`() =
        runTest {
            repository = createRepository(testScheduler)
            val dao = database.downloadTaskDao()
            assertEquals(null, repository.observeDownload(TEST_ID).first())
            dao.insert(entity(TEST_ID, "media.example"))
            dao.insert(entity("other", "media.example"))

            assertEquals(TEST_ID, repository.observeDownload(TEST_ID).first()?.id)
            dao.updateTitle(TEST_ID, "renamed", 2L)
            assertEquals("renamed", repository.observeDownload(TEST_ID).first()?.title)
            dao.delete(TEST_ID)

            assertEquals(null, repository.observeDownload(TEST_ID).first())
            assertEquals("other", repository.observeDownload("other").first()?.id)
        }

    @Test
    fun `startup scheduling check restores queued work`() =
        runTest {
            repository = createRepository(testScheduler)
            database.downloadTaskDao().insert(entity(TEST_ID, "media.example"))

            repository.recoverPendingDownloads()

            assertEquals(1, scheduler.successfulEnsureCount)
        }

    @Test
    fun `startup finishes interrupted deletion with or without remaining files`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dao = database.downloadTaskDao()
            for (hasFile in listOf(true, false)) {
                val localFile = File(context.filesDir, "videos/recovery.mp4")
                if (hasFile) {
                    localFile.parentFile?.mkdirs()
                    localFile.writeText("video")
                }
                dao.insert(
                    entity(TEST_ID, "media.example").copy(
                        status = DownloadStatus.COMPLETED.name,
                        localFileName = localFile.name,
                        deletePending = true,
                    ),
                )
                repository = createRepository(testScheduler)

                repository.recoverPendingDownloads()
                repository.recoverPendingDownloads()

                assertTrue(dao.getById(TEST_ID) == null)
                assertTrue(!localFile.exists())
            }
        }

    @Test
    fun `startup retains failed deletion and still schedules other downloads`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val localFile = File(context.filesDir, "videos/recovery.mp4").apply { mkdirs() }
            val child = File(localFile, "blocked").apply { writeText("video") }
            val dao = database.downloadTaskDao()
            dao.insert(entity("other", "media.example"))
            dao.insert(
                entity(TEST_ID, "media.example").copy(
                    status = DownloadStatus.COMPLETED.name,
                    localFileName = localFile.name,
                    deletePending = true,
                ),
            )
            repository = createRepository(testScheduler)

            repository.recoverPendingDownloads()

            assertEquals(true, dao.getById(TEST_ID)?.deletePending)
            assertTrue(child.exists())
            assertEquals(1, scheduler.successfulEnsureCount)
            child.delete()

            repository.recoverPendingDownloads()

            assertTrue(dao.getById(TEST_ID) == null)
            assertTrue(dao.getById("other") != null)
        }

    @Test
    fun `rename updates the saved title`() =
        runTest {
            repository = createRepository(testScheduler)
            repository.enqueue(listOf(testRequest()))

            repository.rename(TEST_ID, "새 이름")

            assertEquals(
                "새 이름",
                repository
                    .downloads
                    .first()
                    .single()
                    .title,
            )
        }

    @Test
    fun `completed downloads excludes active rows`() =
        runTest {
            val dao = database.downloadTaskDao()
            dao.insert(entity("queued", "media.example"))
            dao.insert(
                entity("completed", "media.example").copy(
                    status = DownloadStatus.COMPLETED.name,
                    localFileName = "completed.mp4",
                ),
            )
            repository = createRepository(testScheduler)

            val completed = repository.completedDownloads.first()

            assertEquals(listOf("completed"), completed.map { it.id })
        }

    @Test
    fun `normal and pending download flows never contain the same task`() =
        runTest {
            val normal = entity("normal", "media.example")
            val pending = entity("pending", "media.example").copy(deletePending = true)
            val dao =
                Proxy.newProxyInstance(
                    DownloadTaskDao::class.java.classLoader,
                    arrayOf(DownloadTaskDao::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "observeAll" -> flowOf(listOf(normal, pending))
                        "observeCompleted" -> flowOf(emptyList<DownloadTaskEntity>())
                        "observePendingDeletions" -> flowOf(listOf(pending))
                        else -> error("Unexpected DAO call: ${method.name}")
                    }
                } as DownloadTaskDao
            repository =
                DefaultDownloadRepository(
                    dao = dao,
                    scheduler = scheduler,
                    fileStore = DownloadFileStore(ApplicationProvider.getApplicationContext()),
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )

            val downloads = repository.downloads.first()
            val pendingDownloads = repository.deletionPendingDownloads.first()

            assertEquals(listOf("normal"), downloads.map { it.id })
            assertEquals(listOf("pending"), pendingDownloads.map { it.id })
            assertTrue(downloads.map { it.id }.intersect(pendingDownloads.map { it.id }.toSet()).isEmpty())
        }

    @Test
    fun `deletion claim is exclusive and reset does not revive pending work`() =
        runTest {
            val dao = database.downloadTaskDao()
            dao.insert(entity(TEST_ID, "media.example").copy(status = DownloadStatus.DOWNLOADING.name))

            assertEquals(1, dao.claimForDeletion(TEST_ID, 2L))
            assertEquals(0, dao.claimForDeletion(TEST_ID, 3L))
            dao.resetInterrupted(4L)

            val task = dao.getById(TEST_ID)
            assertEquals(true, task?.deletePending)
            assertEquals(DownloadStatus.DOWNLOADING.name, task?.status)
        }

    @Test
    fun `delete removes the saved task and local file`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val localFile =
                File(context.filesDir, "videos/video-1.mp4").apply {
                    parentFile?.mkdirs()
                    writeText("video")
                }
            val thumbnailFile = localFile.videoThumbnailFile().apply { writeText("thumbnail") }
            val unavailableFile = localFile.videoThumbnailUnavailableFile().apply { writeText("") }
            val temporaryFile = localFile.videoThumbnailTemporaryFile().apply { writeText("temporary") }
            val partialFile =
                File(context.filesDir, "download-parts/$TEST_ID.part").apply {
                    parentFile?.mkdirs()
                    writeText("partial")
                }
            val validatorFile =
                File(context.filesDir, "download-parts/$TEST_ID.part.validator").apply {
                    writeText("validator")
                }
            database.downloadTaskDao().insert(
                entity(TEST_ID, "media.example").copy(
                    status = DownloadStatus.COMPLETED.name,
                    downloadedBytes = localFile.length(),
                    localFileName = localFile.name,
                ),
            )
            repository = createRepository(testScheduler)

            repository.delete(TEST_ID)

            assertTrue(repository.downloads.first().isEmpty())
            assertTrue(!localFile.exists())
            assertTrue(!thumbnailFile.exists())
            assertTrue(!unavailableFile.exists())
            assertTrue(!temporaryFile.exists())
            assertTrue(!partialFile.exists())
            assertTrue(!validatorFile.exists())
        }

    @Test
    fun `delete keeps the row when file cleanup is incomplete and succeeds on retry`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val localFile =
                File(context.filesDir, "videos/video-1.mp4").apply {
                    parentFile?.mkdirs()
                    writeText("video")
                }
            val blockingSidecar = localFile.videoThumbnailFile().apply { mkdirs() }
            val blockingChild = File(blockingSidecar, "blocked").apply { writeText("blocked") }
            database.downloadTaskDao().insert(
                entity(TEST_ID, "media.example").copy(
                    status = DownloadStatus.COMPLETED.name,
                    localFileName = localFile.name,
                ),
            )
            repository = createRepository(testScheduler)

            var failure: IOException? = null
            try {
                repository.delete(TEST_ID)
            } catch (caught: IOException) {
                failure = caught
            }

            assertTrue(failure != null)
            assertEquals(DownloadStatus.COMPLETED.name, database.downloadTaskDao().getById(TEST_ID)?.status)
            assertTrue(database.downloadTaskDao().getById(TEST_ID)?.deletePending!!)
            assertTrue(!localFile.exists())
            assertTrue(blockingChild.exists())
            assertTrue(repository.completedDownloads.first().isEmpty())
            assertTrue(repository.downloads.first().isEmpty())
            val pending = repository.deletionPendingDownloads.first().single()
            assertEquals(TEST_ID, pending.id)
            assertEquals(true, pending.deletePending)
            assertEquals(null, pending.localFilePath)

            blockingChild.delete()
            blockingSidecar.delete()
            repository.delete(TEST_ID)

            assertTrue(database.downloadTaskDao().getById(TEST_ID) == null)
        }

    @Test
    fun `concurrent delete calls have one live file cleanup owner`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val localFile =
                File(context.filesDir, "videos/video-1.mp4").apply {
                    parentFile?.mkdirs()
                    writeText("video")
                }
            val gatedFileStore = GatedDownloadFileStore(context)
            database.downloadTaskDao().insert(
                entity(TEST_ID, "media.example").copy(
                    status = DownloadStatus.COMPLETED.name,
                    localFileName = localFile.name,
                ),
            )
            repository =
                DefaultDownloadRepository(
                    dao = database.downloadTaskDao(),
                    scheduler = scheduler,
                    fileStore = gatedFileStore,
                    ioDispatcher = Dispatchers.IO,
                )

            val first = async(Dispatchers.IO) { repository.delete(TEST_ID) }
            assertTrue(withContext(Dispatchers.IO) { gatedFileStore.started.await(2L, TimeUnit.SECONDS) })
            val second = async(Dispatchers.IO) { repository.delete(TEST_ID) }
            assertEquals(1, gatedFileStore.callCount.get())
            gatedFileStore.release.countDown()
            first.await()
            second.await()

            assertEquals(1, gatedFileStore.callCount.get())
            assertEquals(1, gatedFileStore.maxConcurrent.get())
            assertTrue(database.downloadTaskDao().getById(TEST_ID) == null)
        }

    @Test
    fun `delete claims and removes an active download`() =
        runTest {
            database.downloadTaskDao().insert(
                entity(TEST_ID, "media.example").copy(status = DownloadStatus.DOWNLOADING.name),
            )
            repository = createRepository(testScheduler)

            repository.delete(TEST_ID)

            assertTrue(database.downloadTaskDao().getById(TEST_ID) == null)
        }

    @Test
    fun `explicit retry resets the persisted network retry count`() =
        runTest {
            database.downloadTaskDao().insert(
                entity(TEST_ID, "media.example").copy(
                    status = DownloadStatus.FAILED.name,
                    failureReason = "NETWORK",
                    retryCount = 3,
                ),
            )
            repository = createRepository(testScheduler)

            repository.retry(TEST_ID)

            val task = database.downloadTaskDao().getById(TEST_ID)
            assertEquals(DownloadStatus.QUEUED.name, task?.status)
            assertEquals(0, task?.retryCount)
        }

    @Test
    fun `selection respects two downloads per host`() {
        val queued =
            listOf(
                entity("a-3", "a.example"),
                entity("b-1", "b.example"),
            )

        val selected =
            selectNextDownload(
                queued = queued,
                runningIds = setOf("a-1", "a-2"),
                activeHosts = mapOf("a.example" to 2),
            )

        assertEquals("b-1", selected?.id)
        assertTrue(selected?.host != "a.example")
    }

    private fun createRepository(testScheduler: TestCoroutineScheduler) =
        DefaultDownloadRepository(
            dao = database.downloadTaskDao(),
            scheduler = scheduler,
            fileStore = DownloadFileStore(ApplicationProvider.getApplicationContext()),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
}

private class RecordingScheduler : DownloadWorkScheduler {
    var scheduleCount = 0
    var successfulScheduleCount = 0
    var successfulEnsureCount = 0
    var rescheduleCount = 0
    var failSchedule = false

    override suspend fun schedule() {
        scheduleCount++
        if (failSchedule) error("scheduler failure")
        successfulScheduleCount++
    }

    override suspend fun ensureScheduled() {
        successfulEnsureCount++
    }

    override suspend fun reschedule() {
        rescheduleCount++
    }
}

private class GatedDownloadFileStore(
    context: Context,
) : DownloadFileStore(context) {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val callCount = AtomicInteger()
    val maxConcurrent = AtomicInteger()
    private val concurrent = AtomicInteger()

    override fun delete(
        id: String,
        localFileName: String?,
    ): Boolean {
        callCount.incrementAndGet()
        val active = concurrent.incrementAndGet()
        maxConcurrent.updateAndGet { current -> maxOf(current, active) }
        started.countDown()
        check(release.await(2L, TimeUnit.SECONDS))
        return try {
            super.delete(id, localFileName)
        } finally {
            concurrent.decrementAndGet()
        }
    }
}

private fun testRequest() =
    DownloadRequest(
        id = TEST_ID,
        url = "https://media.example/video.mp4",
        sourcePageUrl = "https://media.example",
        title = "video",
        mimeType = "video/mp4",
        expectedBytes = 1024L,
    )

private fun entity(
    id: String,
    host: String,
) = DownloadTaskEntity(
    id = id,
    url = "https://$host/$id.mp4",
    sourcePageUrl = "https://$host",
    host = host,
    title = id,
    mimeType = "video/mp4",
    expectedBytes = 1024L,
    downloadedBytes = 0L,
    quality = "원본",
    status = DownloadStatus.QUEUED.name,
    failureReason = null,
    retryCount = 0,
    deletePending = false,
    localFileName = null,
    createdAtMillis = 1L,
    updatedAtMillis = 1L,
)

private const val TEST_ID = "video-1"
