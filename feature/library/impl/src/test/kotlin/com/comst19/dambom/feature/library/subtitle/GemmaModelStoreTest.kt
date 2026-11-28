package com.comst19.dambom.feature.library.subtitle

import com.comst19.dambom.feature.library.subtitle.data.GemmaModelDescriptor
import com.comst19.dambom.feature.library.subtitle.data.ModelTransferException
import com.comst19.dambom.feature.library.subtitle.data.downloadVerifiedModel
import com.comst19.dambom.feature.library.subtitle.data.fetchGemmaCatalog
import com.comst19.dambom.feature.library.subtitle.data.parseGemmaCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

class GemmaModelStoreTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private lateinit var output: File
    private val client = OkHttpClient()

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        directory = Files.createTempDirectory("gemma-download-test").toFile()
        output = File(directory, "model.litertlm")
    }

    @After fun cleanup() {
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test fun `verified model is published only after size and SHA match`() =
        runBlocking {
            val body = "test model weights"
            server.enqueue(MockResponse().setBody(body))
            val progress = mutableListOf<Long>()
            downloadVerifiedModel(client, descriptor(body), output, onBytes = progress::add)
            assertEquals(body, output.readText())
            assertEquals(body.length.toLong(), progress.last())
            assertFalse(File(output.path + ".part").exists())
        }

    @Test fun `bad model download keeps the earlier valid version and deletes the part`() =
        runBlocking {
            output.writeText("previous model")
            server.enqueue(MockResponse().setBody("corrupt"))
            val result = runCatching { downloadVerifiedModel(client, descriptor("correct"), output) {} }
            assertTrue(result.isFailure)
            assertEquals("previous model", output.readText())
            assertFalse(File(output.path + ".part").exists())
        }

    @Test fun `cancelled download stops the transfer and never replaces the installed model`() =
        runBlocking {
            output.writeText("previous model")
            val body = "x".repeat(2_000_000)
            server.enqueue(MockResponse().setBody(body).throttleBody(1_048_576, 3, TimeUnit.SECONDS))
            val started = CompletableDeferred<Unit>()
            val task =
                async(Dispatchers.IO) {
                    downloadVerifiedModel(client, descriptor(body), output) { started.complete(Unit) }
                }
            try {
                withTimeout(5_000) { started.await() }
                withTimeout(5_000) { task.cancelAndJoin() }
                assertEquals("previous model", output.readText())
                assertFalse(File(output.path + ".part").exists())
            } finally {
                task.cancelAndJoin()
            }
        }

    @Test fun `oversized catalog is rejected before parsing its contents`() {
        val result = runCatching { parseGemmaCatalog("x".repeat(2 * 1024 * 1024 + 1).toResponseBody()) }
        assertEquals("Model catalog is too large", result.exceptionOrNull()?.message)
    }

    @Test fun `catalog updates select a pinned revision from the known repository`() {
        val revision = "a".repeat(40)
        val sha = "b".repeat(64)
        val catalog = """{"sha":"$revision","siblings":[{"rfilename":"gemma-4-E2B-it.litertlm",
            "size":2588147712,"lfs":{"sha256":"$sha"}}]}"""
        val result = parseGemmaCatalog(catalog)
        assertEquals(sha, result.sha256)
        assertTrue(result.url.endsWith("/resolve/$revision/gemma-4-E2B-it.litertlm"))
        assertTrue(result.url.startsWith("https://huggingface.co/litert-community/"))
        assertTrue(runCatching { parseGemmaCatalog(catalog.replace(revision, "../../other")) }.isFailure)
    }

    @Test fun `temporary server failure is retryable`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503))
            val failure =
                runCatching { downloadVerifiedModel(client, descriptor("model"), output) {} }.exceptionOrNull()
            assertTrue(failure is ModelTransferException)
            assertFalse(File(output.path + ".part").exists())
        }

    @Test fun `disconnected response is retryable without publishing partial model`() =
        runBlocking {
            val body = "x".repeat(100_000)
            server.enqueue(MockResponse().setBody(body).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            val failure =
                runCatching { downloadVerifiedModel(client, descriptor(body), output) {} }.exceptionOrNull()
            assertTrue(failure is ModelTransferException)
            assertFalse(output.exists())
            assertFalse(File(output.path + ".part").exists())
        }

    @Test fun `missing model is a permanent failure`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(404))
            val failure =
                runCatching { downloadVerifiedModel(client, descriptor("model"), output) {} }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
        }

    @Test fun `destination failure is not classified as a network failure`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("model"))
            File(output.path + ".part").mkdir()
            val failure =
                runCatching { downloadVerifiedModel(client, descriptor("model"), output) {} }.exceptionOrNull()
            assertTrue(failure is java.io.IOException)
            assertFalse(failure is ModelTransferException)
        }

    @Test fun `catalog request runs on injected io dispatcher`() =
        runBlocking {
            val revision = "a".repeat(40)
            val sha = "b".repeat(64)
            val catalog =
                """{"sha":"$revision","siblings":[{"rfilename":"gemma-4-E2B-it.litertlm",""" +
                    """"size":2588147712,"lfs":{"sha256":"$sha"}}]}"""
            server.enqueue(MockResponse().setBody(catalog))
            val executor = Executors.newCachedThreadPool()
            val recordedIo = RecordingIoDispatcher(executor.asCoroutineDispatcher())
            try {
                val result = fetchGemmaCatalog(client, server.url("/catalog").toString(), recordedIo)
                assertEquals(sha, result.sha256)
                assertEquals(1, recordedIo.dispatches.get())
            } finally {
                executor.shutdownNow()
            }
        }

    @Test fun `model download and part cleanup run on injected io dispatcher`() =
        runBlocking {
            val body = "test model weights"
            server.enqueue(MockResponse().setBody(body))
            val executor = Executors.newCachedThreadPool()
            val recordedIo = RecordingIoDispatcher(executor.asCoroutineDispatcher())
            try {
                downloadVerifiedModel(client, descriptor(body), output, recordedIo) {}
                assertEquals(body, output.readText())
                assertFalse(File(output.path + ".part").exists())
                assertEquals(2, recordedIo.dispatches.get())
            } finally {
                executor.shutdownNow()
            }
        }

    @Test fun `cancelled download uses injected io dispatcher for transfer and part cleanup`() =
        runBlocking {
            output.writeText("previous model")
            val body = "x".repeat(2_000_000)
            server.enqueue(MockResponse().setBody(body).throttleBody(1_048_576, 30, TimeUnit.SECONDS))
            val canceledCalls = AtomicInteger()
            val cancelTrackingClient =
                client
                    .newBuilder()
                    .eventListener(
                        object : EventListener() {
                            override fun canceled(call: Call) {
                                canceledCalls.incrementAndGet()
                            }
                        },
                    ).build()
            val executor = Executors.newCachedThreadPool()
            val recordedIo = RecordingIoDispatcher(executor.asCoroutineDispatcher())
            val started = CompletableDeferred<Unit>()
            val task =
                async {
                    downloadVerifiedModel(cancelTrackingClient, descriptor(body), output, recordedIo) {
                        started.complete(Unit)
                    }
                }
            try {
                withTimeout(5_000) { started.await() }
                assertEquals(1, recordedIo.dispatches.get())
                withTimeout(5_000) { task.cancelAndJoin() }
                assertEquals(1, canceledCalls.get())
                assertEquals(2, recordedIo.dispatches.get())
                assertEquals("previous model", output.readText())
                assertFalse(File(output.path + ".part").exists())
            } finally {
                task.cancelAndJoin()
                executor.shutdownNow()
            }
        }

    @Test fun `cancellation during part cleanup deletes part and preserves original failure`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("corrupt"))
            val executor = Executors.newCachedThreadPool()
            var cancelTask: (() -> Unit)? = null
            val recordedIo =
                RecordingIoDispatcher(executor.asCoroutineDispatcher()) { dispatchNumber ->
                    if (dispatchNumber == 2) cancelTask?.invoke()
                }
            var caught: Throwable? = null
            val task =
                async {
                    try {
                        downloadVerifiedModel(client, descriptor("correct"), output, recordedIo) {}
                    } catch (failure: Throwable) {
                        caught = failure
                    }
                }
            cancelTask = { task.cancel() }
            try {
                withTimeout(5_000) { task.join() }
                assertTrue(caught is IllegalStateException)
                assertEquals("Model integrity check failed", caught?.message)
                assertEquals(2, recordedIo.dispatches.get())
                assertFalse(File(output.path + ".part").exists())
            } finally {
                task.cancelAndJoin()
                executor.shutdownNow()
            }
        }

    private fun descriptor(body: String) =
        GemmaModelDescriptor(
            "a".repeat(40),
            MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) },
            body.toByteArray().size.toLong(),
            server.url("/model").toString(),
        )

    private class RecordingIoDispatcher(
        private val delegate: CoroutineDispatcher,
        private val onDispatch: (Int) -> Unit = {},
    ) : CoroutineDispatcher() {
        val dispatches = AtomicInteger()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            onDispatch(dispatches.incrementAndGet())
            delegate.dispatch(context, block)
        }
    }
}
