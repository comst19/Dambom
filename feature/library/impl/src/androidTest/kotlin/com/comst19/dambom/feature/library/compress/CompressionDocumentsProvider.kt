package com.comst19.dambom.feature.library.compress

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CompressionDocumentsProvider : DocumentsProvider() {
    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: emptyArray())

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor {
        val columns =
            projection
                ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        val file = document(documentId)
        return MatrixCursor(columns).apply {
            addRow(
                columns.map { column ->
                    when (column) {
                        Document.COLUMN_DOCUMENT_ID -> documentId
                        Document.COLUMN_DISPLAY_NAME -> file.name
                        Document.COLUMN_MIME_TYPE -> "video/mp4"
                        Document.COLUMN_SIZE -> file.length()
                        else -> null
                    }
                },
            )
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: emptyArray())

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        if (documentId.startsWith("journal-deny-all-") && mode == "rwt") {
            throw SecurityException("Truncation rejected by test provider")
        }
        if (!documentId.startsWith("journal-") || mode != "wt") {
            return ParcelFileDescriptor.open(document(documentId), ParcelFileDescriptor.parseMode(mode))
        }
        val (reader, writer) = ParcelFileDescriptor.createPipe()
        thread(name = "export-test-provider", isDaemon = true) {
            ParcelFileDescriptor.AutoCloseInputStream(reader).use { input ->
                document(documentId).outputStream().use { output ->
                    val buffer = ByteArray(4096)
                    val size = input.read(buffer)
                    if (size <= 0) return@thread
                    output.write(buffer, 0, size)
                    if (documentId.contains("-wait-")) {
                        firstBytes.countDown()
                        releaseWriter.await(45, TimeUnit.SECONDS)
                    }
                    if (!documentId.contains("-fail-")) input.copyTo(output)
                }
            }
        }
        return writer
    }

    override fun deleteDocument(documentId: String) {
        if (documentId.startsWith("journal-deny-")) throw UnsupportedOperationException("Cannot delete test document")
        document(documentId).delete()
    }

    private fun document(id: String): File {
        require(id.matches(Regex("[a-z0-9.-]+")))
        return File(checkNotNull(context).cacheDir, id)
    }

    companion object {
        var firstBytes = CountDownLatch(1)
        var releaseWriter = CountDownLatch(1)
    }
}
