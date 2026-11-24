package com.comst19.dambom.feature.library.export

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.feature.library.compress.CompressionDocumentsProvider
import com.comst19.dambom.feature.library.subtitle.data.SubtitleFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExportDocumentRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ExportDocumentStore(context, Dispatchers.IO)
    private val journal get() = context.noBackupFilesDir.resolve("pending-exports")

    @Test
    fun mp4CancellationRemovesStartedDocumentAndCache() =
        runBlocking {
            val id = "journal-cancel-wait-video.mp4"
            CompressionDocumentsProvider.firstBytes = CountDownLatch(1)
            CompressionDocumentsProvider.releaseWriter = CountDownLatch(1)
            val job = launch(Dispatchers.IO) { saveMp4(id) }
            try {
                assertTrue(CompressionDocumentsProvider.firstBytes.await(10, TimeUnit.SECONDS))
                assertTrue(document(id).length() > 0)
                store.recoverInterruptedExports()
                assertTrue(document(id).length() > 0)
                job.cancel()
            } finally {
                job.cancel()
                CompressionDocumentsProvider.releaseWriter.countDown()
                job.cancelAndJoin()
            }
            assertReconciled(id, deleted = true)
        }

    @Test
    fun srtCancellationRemovesStartedDocumentAndKeepsSource() =
        runBlocking {
            val id = "journal-cancel-wait-caption.srt"
            val source = captionSource(id)
            CompressionDocumentsProvider.firstBytes = CountDownLatch(1)
            CompressionDocumentsProvider.releaseWriter = CountDownLatch(1)
            val job = launch(Dispatchers.IO) { saveSrt(id, source) }
            try {
                assertTrue(CompressionDocumentsProvider.firstBytes.await(10, TimeUnit.SECONDS))
                assertTrue(document(id).length() > 0)
                job.cancel()
            } finally {
                job.cancel()
                CompressionDocumentsProvider.releaseWriter.countDown()
                job.cancelAndJoin()
            }
            assertReconciled(id, deleted = true)
            assertEquals(PAYLOAD_SIZE, source.videoSubtitleFile().length())
            source.videoSubtitleFile().delete()
            Unit
        }

    @Test
    fun failedWriteRemovesDocumentWhenProviderAllowsDelete() =
        runBlocking {
            val id = "journal-allow-fail-video.mp4"
            assertTrue(runCatching { saveMp4(id) }.isFailure)
            assertReconciled(id, deleted = true)
        }

    @Test
    fun refusalToDeleteFallsBackToTruncation() =
        runBlocking {
            val id = "journal-deny-fail-caption.srt"
            val source = captionSource(id)
            try {
                assertTrue(runCatching { saveSrt(id, source) }.isFailure)
                assertReconciled(id, deleted = false)
                assertEquals(0L, document(id).length())
                assertEquals(PAYLOAD_SIZE, source.videoSubtitleFile().length())
            } finally {
                document(id).delete()
                source.videoSubtitleFile().delete()
            }
        }

    @Test
    fun refusalToDeleteAndTruncateLeavesDocumentForManualCleanup() =
        runBlocking {
            val id = "journal-deny-all-fail-video.mp4"
            try {
                assertTrue(runCatching { saveMp4(id) }.isFailure)
                assertReconciled(id, deleted = false)
                assertTrue(document(id).length() > 0)
            } finally {
                document(id).delete()
            }
        }

    @Test
    fun completedDocumentSurvivesRecovery() =
        runBlocking {
            val id = "completed-export-recovery.mp4"
            try {
                assertEquals(PAYLOAD_SIZE, saveMp4(id))
                store.recoverInterruptedExports()
                assertEquals(PAYLOAD_SIZE, document(id).length())
                assertReconciled(id, deleted = false)
            } finally {
                document(id).delete()
            }
        }

    @Test
    fun startWriteForExternalForceStop(): Unit =
        runBlocking {
            val id = crashDocument()
            CompressionDocumentsProvider.firstBytes = CountDownLatch(1)
            CompressionDocumentsProvider.releaseWriter = CountDownLatch(1)
            if (id.endsWith(".srt")) saveSrt(id, captionSource(id)) else saveMp4(id)
            error("The test process must be force-stopped while its document pipe is blocked")
        }

    @Test
    fun recoverWriteAfterExternalForceStop() =
        runBlocking {
            val id = crashDocument()
            assertTrue(document(id).length() > 0)
            assertEquals(1, journal.listFiles().orEmpty().count { it.extension == "pending" })
            if (id.endsWith(".mp4")) assertTrue(exportCacheFiles().isNotEmpty())
            store.recoverInterruptedExports()
            val refused = id.startsWith("journal-deny-")
            assertReconciled(id, deleted = !refused)
            if (refused) {
                assertEquals(0L, document(id).length())
                document(id).delete()
            }
            if (id.endsWith(".srt")) {
                assertEquals(PAYLOAD_SIZE, captionSourceFile(id).videoSubtitleFile().length())
                captionSourceFile(id).videoSubtitleFile().delete()
            }
            Unit
        }

    private suspend fun saveMp4(id: String): Long = VideoExportFiles(store).save(uri(id).toString(), ::writePayload)

    private suspend fun saveSrt(
        id: String,
        source: File,
    ) = SubtitleFiles(store, Dispatchers.IO).export(source.path, uri(id))

    private fun captionSource(id: String) = captionSourceFile(id).apply { writePayload(videoSubtitleFile()) }

    private fun captionSourceFile(id: String) = context.cacheDir.resolve("source-$id.mp4")

    private fun crashDocument(): String {
        val id = InstrumentationRegistry.getArguments().getString("forceStopDocument")
        assumeTrue(id in CRASH_DOCUMENTS)
        return checkNotNull(id)
    }

    private fun assertReconciled(
        id: String,
        deleted: Boolean,
    ) {
        assertEquals(!deleted, document(id).exists())
        assertFalse(journal.listFiles().orEmpty().any { it.extension == "pending" })
        assertTrue(exportCacheFiles().isEmpty())
    }

    private fun exportCacheFiles() =
        context.cacheDir
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith("export-") }

    private fun document(id: String) = context.cacheDir.resolve(id)

    private fun uri(id: String): Uri = DocumentsContract.buildDocumentUri(AUTHORITY, id)

    private fun writePayload(file: File) {
        RandomAccessFile(file, "rw").use { it.setLength(PAYLOAD_SIZE) }
    }

    private companion object {
        const val AUTHORITY = "com.comst19.dambom.compression.test.documents"
        const val PAYLOAD_SIZE = 1_048_576L
        val CRASH_DOCUMENTS = setOf("journal-crash-wait-video.mp4", "journal-deny-crash-wait-caption.srt")
    }
}
