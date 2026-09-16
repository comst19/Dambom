package com.comst19.dambom.feature.library.file

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.coroutines.Dispatchers
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
    fun `input open failure does not open the output stream`() {
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
    fun `output open failure closes the input stream`() {
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
    fun `copy failure closes both streams`() {
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
