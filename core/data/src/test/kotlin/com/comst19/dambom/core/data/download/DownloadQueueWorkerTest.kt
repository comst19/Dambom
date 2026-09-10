package com.comst19.dambom.core.data.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskDao
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
import org.robolectric.shadows.ShadowStatFs
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
        ShadowStatFs.registerStats(context.filesDir.resolve("download-parts").path, 1_000_000, 1_000_000, 1_000_000)
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
    fun `replacement truncates stale partial before checking remaining space`() =
        runTest {
            successfulServer.enqueue(
                MockResponse().setHeader("Content-Type", "video/mp4").setHeader("ETag", "\"v2\"").setBody("new"),
            )
            val task = entity("reclaim-space", successfulServer.url("/video.mp4").toString())
            database.downloadTaskDao().insert(task)
            fileStore.partialFile(task.id).writeText("old-generation")
            fileStore.partialValidatorFile(task.id).writeText("\"v1\"")
            ShadowStatFs.registerStats(context.filesDir.resolve("download-parts").path, 1, 0, 0)

            createWorker().doWork()

            assertEquals(0L, fileStore.partialFile(task.id).length())
            assertFalse(fileStore.partialValidatorFile(task.id).exists())
            assertEquals("INSUFFICIENT_STORAGE", database.downloadTaskDao().getById(task.id)?.failureReason)
        }

    @Test
    fun `unknown length transfer stops when storage drops after opening`() =
        runTest {
            successfulServer.enqueue(
                MockResponse().setHeader("Content-Type", "video/mp4").setChunkedBody("video", 2),
            )
            val task = entity("space-drops", successfulServer.url("/video.mp4").toString())
            val dao = database.downloadTaskDao()
            dao.insert(task)
            val lowSpaceDao =
                object : DownloadTaskDao by dao {
                    override suspend fun updateProgress(
                        id: String,
                        downloadedBytes: Long,
                        expectedBytes: Long?,
                        updatedAtMillis: Long,
                    ): Int {
                        ShadowStatFs.registerStats(context.filesDir.resolve("download-parts").path, 1, 0, 0)
                        return dao.updateProgress(id, downloadedBytes, expectedBytes, updatedAtMillis)
                    }
                }

            createWorker(dao = lowSpaceDao).doWork()

            val saved = dao.getById(task.id)
            assertEquals("INSUFFICIENT_STORAGE", saved?.failureReason)
            assertEquals(DownloadStatus.FAILED.name, saved?.status)
            assertEquals(0, saved?.retryCount)
            assertEquals(0L, fileStore.partialFile(task.id).length())
        }

    @Test
    fun `replacement response clears old bytes before publishing new validator`() =
        runTest {
            successfulServer.enqueue(
                MockResponse().setHeader("Content-Type", "video/mp4").setHeader("ETag", "\"v2\"").setBody("new"),
            )
            val task = entity("replacement-order", successfulServer.url("/video.mp4").toString())
            val dao = database.downloadTaskDao()
            dao.insert(task)
            fileStore.partialFile(task.id).writeText("old-generation")
            fileStore.partialValidatorFile(task.id).writeText("\"v1\"")
            var checked = false
            val checkpointDao =
                object : DownloadTaskDao by dao {
                    override suspend fun updateProgress(
                        id: String,
                        downloadedBytes: Long,
                        expectedBytes: Long?,
                        updatedAtMillis: Long,
                    ): Int {
                        assertEquals(0L, fileStore.partialFile(id).length())
                        assertEquals("\"v2\"", fileStore.partialValidatorFile(id).readText())
                        checked = true
                        dao.pause(id, updatedAtMillis)
                        return 0
                    }
                }

            createWorker(dao = checkpointDao).doWork()

            assertEquals(true, checked)
            assertEquals(DownloadStatus.PAUSED.name, dao.getById(task.id)?.status)
            assertEquals("\"v1\"", successfulServer.takeRequest().getHeader("If-Range"))
        }

    @Test
    fun `file open failure is storage failure without network retry`() =
        runTest {
            successfulServer.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("video"))
            val task = entity("storage-error", successfulServer.url("/video.mp4").toString())
            database.downloadTaskDao().insert(task)
            fileStore.partialFile(task.id).mkdirs()
            fileStore.partialValidatorFile(task.id).writeText("\"v1\"")

            val result = createWorker().doWork()

            val saved = database.downloadTaskDao().getById(task.id)
            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(DownloadStatus.FAILED.name, saved?.status)
            assertEquals("STORAGE", saved?.failureReason)
            assertEquals(0, saved?.retryCount)
        }

    @Test
    fun `pause between rename and completion preserves the finished file for resume`() =
        runTest {
            successfulServer.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("video"))
            val task = entity("finish-pause", successfulServer.url("/finish.mp4").toString())
            val dao = database.downloadTaskDao()
            dao.insert(task)
            val pausingDao =
                object : DownloadTaskDao by dao {
                    override suspend fun markCompleted(
                        id: String,
                        downloadedBytes: Long,
                        localFileName: String,
                        updatedAtMillis: Long,
                    ): Int {
                        dao.pause(id, updatedAtMillis)
                        return dao.markCompleted(id, downloadedBytes, localFileName, updatedAtMillis)
                    }
                }
            createWorker(dao = pausingDao).doWork()
            assertEquals(DownloadStatus.PAUSED.name, dao.getById(task.id)?.status)
            assertEquals("video", fileStore.completedFile(task.id, task.url, task.mimeType).readText())
            dao.queueAgain(task.id, 3L)
            withContext(Dispatchers.IO) { withTimeout(2_000L) { createWorker().doWork() } }
            assertEquals(DownloadStatus.COMPLETED.name, dao.getById(task.id)?.status)
            assertEquals(1, successfulServer.requestCount)
        }

    @Test
    fun `recovery finalizes a renamed file without downloading it again`() =
        runTest {
            val task =
                entity("renamed", successfulServer.url("/renamed.mp4").toString())
                    .copy(status = DownloadStatus.DOWNLOADING.name)
            database.downloadTaskDao().insert(task)
            fileStore.completedFile(task.id, task.url, task.mimeType).writeText("complete video")
            successfulServer.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("replacement"))
            createWorker().doWork()
            assertEquals("complete video", fileStore.completedFile(task.id, task.url, task.mimeType).readText())
            assertEquals(0, successfulServer.requestCount)
        }

    @Test
    fun `pause interrupts stalled headers without counting a network failure`() =
        runTest {
            successfulServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val task = entity("stalled", successfulServer.url("/stalled.mp4").toString())
            val dao = database.downloadTaskDao()
            dao.insert(task)
            val work = async(Dispatchers.IO) { createWorker().doWork() }
            assertNotNull(successfulServer.takeRequest(2L, TimeUnit.SECONDS))
            dao.pause(task.id, 2L)
            withContext(Dispatchers.IO) { withTimeout(2_000L) { work.await() } }
            assertEquals(DownloadStatus.PAUSED.name, dao.getById(task.id)?.status)
            assertEquals(0, dao.getById(task.id)?.retryCount)
        }

    @Test
    fun `pause interrupts a stalled body and preserves its partial data`() =
        runTest {
            successfulServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("ETag", "\"v1\"")
                    .setBody("v".repeat(32 * 1024))
                    .throttleBody(8192, 3L, TimeUnit.SECONDS),
            )
            val task = entity("stalled-body", successfulServer.url("/stalled-body.mp4").toString())
            val dao = database.downloadTaskDao()
            dao.insert(task)
            val work = async(Dispatchers.IO) { createWorker().doWork() }
            awaitCondition { fileStore.partialFile(task.id).length() >= 8192L }
            dao.pause(task.id, 2L)
            withContext(Dispatchers.IO) { withTimeout(2_000L) { work.await() } }
            assertEquals(8192L, fileStore.partialFile(task.id).length())
            assertEquals(DownloadStatus.PAUSED.name, dao.getById(task.id)?.status)
            assertEquals(0, dao.getById(task.id)?.retryCount)
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
    fun `new task remains retryable when WorkManager attempt count is already two`() =
        runTest {
            failingServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val task = entity("fresh", failingServer.url("/fresh.mp4").toString())
            val dao = database.downloadTaskDao()
            dao.insert(task)

            val result = createWorker(runAttemptCount = 2).doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            assertEquals(DownloadStatus.QUEUED.name, dao.getById(task.id)?.status)
            assertEquals(1, dao.getById(task.id)?.retryCount)
        }

    @Test
    fun `network retry budgets are independent per task`() =
        runTest {
            repeat(6) {
                failingServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            }
            val dao = database.downloadTaskDao()
            val first = entity("first", failingServer.url("/first.mp4").toString())
            val second = entity("second", failingServer.url("/second.mp4").toString())
            dao.insert(first)
            dao.insert(second)

            repeat(2) {
                assertEquals(ListenableWorker.Result.retry(), createWorker().doWork())
                assertEquals(1 + it, dao.getById(first.id)?.retryCount)
                assertEquals(1 + it, dao.getById(second.id)?.retryCount)
            }

            assertEquals(ListenableWorker.Result.success(), createWorker().doWork())
            assertEquals(DownloadStatus.FAILED.name, dao.getById(first.id)?.status)
            assertEquals(DownloadStatus.FAILED.name, dao.getById(second.id)?.status)
            assertEquals(3, dao.getById(first.id)?.retryCount)
            assertEquals(3, dao.getById(second.id)?.retryCount)
        }

    @Test
    fun `foreground state only changes for a displayed title or progress change`() {
        val initial = foregroundNotificationState("title", 100L, 1_000L)

        assertEquals(initial, foregroundNotificationState("title", 109L, 1_000L))
        assertFalse(initial == foregroundNotificationState("renamed", 109L, 1_000L))
        assertFalse(initial == foregroundNotificationState("title", 110L, 1_000L))
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
    fun `weak saved validator restarts without if range`() =
        runTest {
            successfulServer.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("new"))
            val task = entity("weak-validator", successfulServer.url("/weak.mp4").toString())
            database.downloadTaskDao().insert(task)
            fileStore.partialFile(task.id).writeText("old")
            fileStore.partialValidatorFile(task.id).writeText("W/\"v1\"")
            createWorker().doWork()
            val request = successfulServer.takeRequest()
            assertEquals(null, request.getHeader("If-Range"))
            assertEquals(null, request.getHeader("Range"))
            assertEquals("new", fileStore.completedFile(task.id, task.url, task.mimeType).readText())
        }

    @Test
    fun `interrupted weak etag response restarts without combining file generations`() =
        runTest {
            successfulServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("ETag", "W/\"v1\"")
                    .setHeader("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT")
                    .setBody("abcdef")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            successfulServer.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("replacement"))
            val task = entity("weak-interrupted", successfulServer.url("/weak.mp4").toString())
            database.downloadTaskDao().insert(task)
            assertEquals(ListenableWorker.Result.retry(), createWorker().doWork())
            assertFalse(fileStore.partialValidatorFile(task.id).exists())
            createWorker().doWork()
            successfulServer.takeRequest()
            val request = successfulServer.takeRequest()
            assertEquals(null, request.getHeader("If-Range"))
            assertEquals(null, request.getHeader("Range"))
            assertEquals("replacement", fileStore.completedFile(task.id, task.url, task.mimeType).readText())
        }

    @Test
    fun `html at a video url is never completed`() =
        runTest {
            successfulServer.enqueue(
                MockResponse().setHeader("Content-Type", "text/html").setBody("<html>login</html>"),
            )
            val task = entity("html-video", successfulServer.url("/login.mp4").toString())
            database.downloadTaskDao().insert(task)
            createWorker().doWork()
            assertEquals(DownloadStatus.FAILED.name, database.downloadTaskDao().getById(task.id)?.status)
            assertFalse(fileStore.completedFile(task.id, task.url, task.mimeType).exists())
        }

    @Test
    fun `binary prefix is not JSON solely because its first byte is a bracket`() =
        runTest {
            for (first in listOf('{', '[')) {
                val payload = "$first\u0000\u0000\u0018mdat\u0000\u0001\u0002\u0003"
                successfulServer.enqueue(
                    MockResponse().setHeader("Content-Type", "application/octet-stream").setBody(payload),
                )
                val task =
                    entity("binary-${first.code}", successfulServer.url("/binary.mp4?first=${first.code}").toString())
                database.downloadTaskDao().insert(task)

                createWorker().doWork()

                assertEquals(DownloadStatus.COMPLETED.name, database.downloadTaskDao().getById(task.id)?.status)
                assertEquals(payload, fileStore.completedFile(task.id, task.url, task.mimeType).readText())
            }
        }

    @Test
    fun `mislabelled error documents are rejected and octet stream video is preserved`() =
        runTest {
            val payloads = listOf(" \n<!DOCTYPE html><html>login</html>", "{\"error\":\"denied\"}")
            payloads.forEachIndexed { index, payload ->
                successfulServer.enqueue(
                    MockResponse().setHeader("Content-Type", "application/octet-stream").setBody(payload),
                )
                val task = entity("error-$index", successfulServer.url("/error-$index.mp4").toString())
                database.downloadTaskDao().insert(task)
                createWorker().doWork()
                assertEquals(DownloadStatus.FAILED.name, database.downloadTaskDao().getById(task.id)?.status)
                assertFalse(fileStore.completedFile(task.id, task.url, task.mimeType).exists())
            }
            val payload = "\u0000\u0000\u0000\u0018ftypisom\u0000\u0000\u0000\u0000isomiso2"
            successfulServer.enqueue(
                MockResponse().setHeader("Content-Type", "application/octet-stream").setBody(payload),
            )
            val task = entity("octet-video", successfulServer.url("/video.mp4").toString())
            database.downloadTaskDao().insert(task)
            createWorker().doWork()
            assertEquals(DownloadStatus.COMPLETED.name, database.downloadTaskDao().getById(task.id)?.status)
            assertEquals(payload, fileStore.completedFile(task.id, task.url, task.mimeType).readText())
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

    private fun createWorker(
        runAttemptCount: Int = 0,
        dao: DownloadTaskDao = database.downloadTaskDao(),
    ): DownloadQueueWorker =
        TestListenableWorkerBuilder<DownloadQueueWorker>(context, runAttemptCount = runAttemptCount)
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
        retryCount = 0,
        deletePending = false,
        localFileName = null,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
