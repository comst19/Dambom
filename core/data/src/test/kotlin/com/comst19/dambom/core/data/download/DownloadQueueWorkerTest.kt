package com.comst19.dambom.core.data.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.domain.model.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadQueueWorkerTest {
    private lateinit var context: Context
    private lateinit var database: DambomDatabase
    private lateinit var fileStore: DownloadFileStore
    private lateinit var failingServer: MockWebServer
    private lateinit var successfulServer: MockWebServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, DambomDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        fileStore = DownloadFileStore(context)
        failingServer = MockWebServer().apply(MockWebServer::start)
        successfulServer = MockWebServer().apply(MockWebServer::start)
    }

    @After
    fun tearDown() {
        failingServer.shutdown()
        successfulServer.shutdown()
        database.close()
        context.filesDir.resolve("download-parts").deleteRecursively()
        context.filesDir.resolve("videos").deleteRecursively()
    }

    @Test
    fun `retryable failure does not cancel an unrelated download`() =
        runTest {
            failingServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            successfulServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/mp4")
                    .setBody("video")
                    .throttleBody(1L, 50L, TimeUnit.MILLISECONDS),
            )
            val dao = database.downloadTaskDao()
            dao.insert(entity("failed", failingServer.url("/failed.mp4").toString()))
            dao.insert(entity("successful", successfulServer.url("/successful.mp4").toString()))

            createWorker().doWork()

            assertEquals(DownloadStatus.COMPLETED.name, dao.getById("successful")?.status)
            assertEquals("video", fileStore.completedFile("successful", "successful.mp4", "video/mp4").readText())
        }

    @Test
    fun `mismatched content range restarts instead of appending corrupt bytes`() =
        runTest {
            successfulServer.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Range", "bytes 0-2/3")
                    .setBody("new"),
            )
            successfulServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/mp4")
                    .setBody("new"),
            )
            val task = entity("range", successfulServer.url("/range.mp4").toString())
            database.downloadTaskDao().insert(task)
            fileStore.partialFile(task.id).writeText("old")
            fileStore.partialFile(task.id).resolveSibling("${task.id}.part.validator").writeText("\"v1\"")

            createWorker().doWork()

            assertEquals("new", fileStore.completedFile(task.id, task.url, task.mimeType).readText())
            assertEquals(2, successfulServer.requestCount)
        }

    @Test
    fun `resumed download sends the saved validator with if range`() =
        runTest {
            successfulServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/mp4")
                    .setBody("new"),
            )
            val task = entity("validator", successfulServer.url("/validator.mp4").toString())
            database.downloadTaskDao().insert(task)
            fileStore.partialFile(task.id).writeText("old")
            fileStore.partialFile(task.id).resolveSibling("${task.id}.part.validator").writeText("\"v1\"")

            createWorker().doWork()

            assertEquals("\"v1\"", successfulServer.takeRequest().getHeader("If-Range"))
            assertEquals("new", fileStore.completedFile(task.id, task.url, task.mimeType).readText())
        }

    @Test
    fun `quick resume stops the previous transfer and continues from its partial file`() =
        runTest {
            successfulServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("ETag", "\"v1\"")
                    .setBody("v".repeat(2 * 1024 * 1024))
                    .throttleBody(64 * 1024L, 50L, TimeUnit.MILLISECONDS),
            )
            successfulServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/mp4")
                    .setBody("resumed"),
            )
            val task = entity("quick-resume", successfulServer.url("/quick-resume.mp4").toString())
            val dao = database.downloadTaskDao()
            dao.insert(task)
            val work = async(Dispatchers.IO) { createWorker().doWork() }
            awaitCondition {
                dao.getById(task.id)?.status == DownloadStatus.DOWNLOADING.name &&
                    fileStore.partialFile(task.id).length() >= 128 * 1024L
            }

            dao.pause(task.id, 2L)
            dao.queueAgain(task.id, 3L)
            work.await()

            successfulServer.takeRequest()
            val resumedRequest = successfulServer.takeRequest()
            assertNotNull(resumedRequest.getHeader("Range"))
        }

    @Test
    fun `cancelling before the response body removes the late partial file`() =
        runTest {
            successfulServer.enqueue(
                MockResponse()
                    .setHeadersDelay(500L, TimeUnit.MILLISECONDS)
                    .setHeader("Content-Type", "video/mp4")
                    .setBody("video"),
            )
            val task = entity("cancelled", successfulServer.url("/cancelled.mp4").toString())
            val dao = database.downloadTaskDao()
            dao.insert(task)
            val work = async(Dispatchers.IO) { createWorker().doWork() }
            assertNotNull(successfulServer.takeRequest(2L, TimeUnit.SECONDS))

            dao.delete(task.id)
            fileStore.clearPartial(task.id)
            work.await()

            assertFalse(fileStore.partialFile(task.id).exists())
            assertFalse(fileStore.partialValidatorFile(task.id).exists())
        }

    private fun createWorker(): DownloadQueueWorker =
        TestListenableWorkerBuilder<DownloadQueueWorker>(context, runAttemptCount = 0)
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
                            dao = database.downloadTaskDao(),
                            client = OkHttpClient(),
                            fileStore = fileStore,
                            notifier = DownloadNotifier(appContext),
                            ioDispatcher = Dispatchers.IO,
                        )
                },
            ).build()
}

private suspend fun awaitCondition(condition: suspend () -> Boolean) {
    withContext(Dispatchers.IO) {
        withTimeout(5_000L) {
            while (!condition()) delay(10L)
        }
    }
}

private fun entity(
    id: String,
    url: String,
): DownloadTaskEntity =
    DownloadTaskEntity(
        id = id,
        url = url,
        sourcePageUrl = url,
        host = url.substringAfter("://").substringBefore(':').substringBefore('/'),
        title = id,
        mimeType = "video/mp4",
        expectedBytes = null,
        downloadedBytes = 0L,
        quality = "original",
        status = DownloadStatus.QUEUED.name,
        failureReason = null,
        localFileName = null,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
