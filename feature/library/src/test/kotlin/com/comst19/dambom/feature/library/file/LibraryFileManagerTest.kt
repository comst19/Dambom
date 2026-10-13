package com.comst19.dambom.feature.library.file

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryFileManagerTest {
    @Test
    fun `successful copy preserves bytes across buffer boundaries`() =
        runBlocking {
            val data = ByteArray(32_769) { it.toByte() }
            val output = ByteArrayOutputStream()
            copyStreams({ ByteArrayInputStream(data) }, { output })
            org.junit.Assert.assertArrayEquals(data, output.toByteArray())
        }

    @Test
    fun `cancelled copy stops reading and closes both streams`() =
        runBlocking {
            var reads = 0
            var inputClosed = false
            var outputClosed = false
            val copy =
                launch {
                    val owner = checkNotNull(currentCoroutineContext()[Job])
                    copyStreams(
                        openInput = {
                            object : ByteArrayInputStream(ByteArray(32_768)) {
                                override fun read(
                                    bytes: ByteArray,
                                    offset: Int,
                                    length: Int,
                                ): Int {
                                    reads++
                                    return super.read(bytes, offset, length)
                                }

                                override fun close() {
                                    inputClosed = true
                                }
                            }
                        },
                        openOutput = {
                            object : ByteArrayOutputStream() {
                                override fun write(
                                    bytes: ByteArray,
                                    offset: Int,
                                    length: Int,
                                ) {
                                    super.write(bytes, offset, length)
                                    owner.cancel()
                                }

                                override fun close() {
                                    outputClosed = true
                                }
                            }
                        },
                    )
                }
            copy.join()
            assertEquals(1, reads)
            assertEquals(true, inputClosed)
            assertEquals(true, outputClosed)
        }

    @Test
    fun `cancelled file copy removes partial destination and keeps source`() =
        runBlocking {
            val directory =
                java.nio.file.Files
                    .createTempDirectory("cancelled-export")
                    .toFile()
            val source = directory.resolve("source.mp4").apply { writeBytes(ByteArray(32_768) { 7 }) }
            val destination = directory.resolve("new.mp4")
            try {
                val copy =
                    launch {
                        val owner = checkNotNull(currentCoroutineContext()[Job])
                        copyToNewFile(destination) {
                            object : ByteArrayInputStream(source.readBytes()) {
                                override fun read(
                                    bytes: ByteArray,
                                    offset: Int,
                                    length: Int,
                                ): Int {
                                    val count = super.read(bytes, offset, length)
                                    owner.cancel()
                                    return count
                                }
                            }
                        }
                    }
                copy.join()
                assertEquals(false, destination.exists())
                assertEquals(32_768L, source.length())
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun `partial copy failure removes only the new destination and preserves original error`() {
        val directory =
            java.nio.file.Files
                .createTempDirectory("failed-export")
                .toFile()
        val source = directory.resolve("source.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val existing = directory.resolve("existing.mp4").apply { writeText("existing") }
        val destination = directory.resolve("new.mp4")
        val failure = IOException("read failed after partial write")
        var read = false
        try {
            val thrown =
                org.junit.Assert.assertThrows(IOException::class.java) {
                    runBlocking {
                        copyToNewFile(destination) {
                            object : java.io.InputStream() {
                                override fun read(): Int = throw failure

                                override fun read(
                                    bytes: ByteArray,
                                    offset: Int,
                                    length: Int,
                                ): Int {
                                    if (read) throw failure
                                    read = true
                                    bytes[offset] = 1
                                    return 1
                                }
                            }
                        }
                    }
                }
            org.junit.Assert.assertSame(failure, thrown)
            org.junit.Assert.assertFalse(destination.exists())
            org.junit.Assert.assertArrayEquals(byteArrayOf(1, 2, 3), source.readBytes())
            assertEquals("existing", existing.readText())
            org.junit.Assert.assertThrows(IllegalStateException::class.java) {
                runBlocking { copyToNewFile(existing, source::inputStream) }
            }
            assertEquals("existing", existing.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `default export uses the downloads collection`() {
        assertEquals(MediaStore.Downloads.EXTERNAL_CONTENT_URI, defaultDownloadCollectionUri())
    }

    @Test
    fun `restored configured directory requires its persisted read and write grant`() {
        val treeUri = android.net.Uri.parse("content://downloads/tree/dambom")

        assertEquals(
            true,
            hasPersistedTreePermission(
                treeUri,
                listOf(PersistedTreePermission(treeUri, canRead = true, canWrite = true)),
            ),
        )
    }

    @Test
    fun `revoked configured directory is rejected before export`() {
        val treeUri = android.net.Uri.parse("content://downloads/tree/dambom")

        assertEquals(false, hasPersistedTreePermission(treeUri, emptyList()))
    }

    @Test
    fun `missing local file returns no share intent instead of throwing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = LibraryFileManager(context, Dispatchers.IO)

        assertNull(manager.createShareIntent(missingFileTask()))
    }

    @Test
    fun `file outside configured provider path returns no share intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = LibraryFileManager(context, Dispatchers.IO)
        val unsupportedFile = context.cacheDir.resolve("outside-provider.mp4").apply { writeBytes(byteArrayOf(1)) }

        try {
            assertNull(manager.createShareIntent(taskWithPath(unsupportedFile.path)))
        } finally {
            unsupportedFile.delete()
        }
    }

    @Test
    fun `input open failure does not open the output stream`() =
        runBlocking {
            var outputOpenCount = 0

            try {
                copyStreams(
                    openInput = { throw IOException("source disappeared") },
                    openOutput = {
                        outputOpenCount++
                        ByteArrayOutputStream()
                    },
                )
            } catch (_: IOException) {
            }

            assertEquals(0, outputOpenCount)
        }

    @Test
    fun `output open failure closes the input stream`() =
        runBlocking {
            val input = CloseTrackingInputStream()

            try {
                copyStreams(
                    openInput = { input },
                    openOutput = { throw IOException("destination unavailable") },
                )
            } catch (_: IOException) {
            }

            assertEquals(1, input.closeCount)
        }

    @Test
    fun `copy failure closes both streams`() =
        runBlocking {
            val input = CloseTrackingInputStream()
            val output = FailingOutputStream()

            try {
                copyStreams(
                    openInput = { input },
                    openOutput = { output },
                )
            } catch (_: IOException) {
            }

            assertEquals(1, input.closeCount)
            assertEquals(1, output.closeCount)
        }
}

private class CloseTrackingInputStream : ByteArrayInputStream(byteArrayOf(1)) {
    var closeCount = 0

    override fun close() {
        closeCount++
        super.close()
    }
}

private class FailingOutputStream : ByteArrayOutputStream() {
    var closeCount = 0

    override fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Unit = throw IOException("copy failed")

    override fun close() {
        closeCount++
        super.close()
    }
}

private fun missingFileTask() = taskWithPath("/does/not/exist/missing.mp4")

private fun taskWithPath(localFilePath: String) =
    DownloadTask(
        id = "missing",
        url = "https://example.com/video.mp4",
        sourcePageUrl = "https://example.com",
        title = "Missing video",
        mimeType = "video/mp4",
        expectedBytes = 1L,
        downloadedBytes = 1L,
        quality = "original",
        status = DownloadStatus.COMPLETED,
        failureReason = null,
        localFileName = "missing.mp4",
        localFilePath = localFilePath,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
