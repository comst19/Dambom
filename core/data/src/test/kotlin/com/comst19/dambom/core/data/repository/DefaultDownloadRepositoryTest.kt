package com.comst19.dambom.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.data.download.DownloadFileStore
import com.comst19.dambom.core.data.download.DownloadWorkScheduler
import com.comst19.dambom.core.data.download.selectNextDownload
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
    fun `startup scheduling check restores queued work`() =
        runTest {
            repository = createRepository(testScheduler)
            database.downloadTaskDao().insert(entity(TEST_ID, "media.example"))

            repository.ensureDownloadsScheduled()

            assertEquals(1, scheduler.successfulEnsureCount)
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
    fun `delete removes the saved task and local file`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val localFile =
                File(context.filesDir, "videos/video-1.mp4").apply {
                    parentFile?.mkdirs()
                    writeText("video")
                }
            val thumbnailFile = File(localFile.absolutePath + ".thumbnail.jpg").apply { writeText("thumbnail") }
            val unavailableFile = File(localFile.absolutePath + ".thumbnail.unavailable").apply { writeText("") }
            val temporaryFile = File(localFile.absolutePath + ".thumbnail.jpg.tmp").apply { writeText("temporary") }
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
    localFileName = null,
    createdAtMillis = 1L,
    updatedAtMillis = 1L,
)

private const val TEST_ID = "video-1"
