package com.comst19.dambom.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}

private class RecordingScheduler : DownloadWorkScheduler {
    var scheduleCount = 0

    override fun schedule() {
        scheduleCount++
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
