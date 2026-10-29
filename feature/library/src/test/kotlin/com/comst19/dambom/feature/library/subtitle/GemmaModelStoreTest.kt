package com.comst19.dambom.feature.library.subtitle

import com.comst19.dambom.feature.library.subtitle.data.GemmaModelDescriptor
import com.comst19.dambom.feature.library.subtitle.data.downloadVerifiedModel
import com.comst19.dambom.feature.library.subtitle.data.parseGemmaCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

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
            downloadVerifiedModel(client, descriptor(body), output, progress::add)
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

    private fun descriptor(body: String) =
        GemmaModelDescriptor(
            "a".repeat(40),
            MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) },
            body.toByteArray().size.toLong(),
            server.url("/model").toString(),
        )
}
