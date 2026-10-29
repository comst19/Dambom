package com.comst19.dambom.feature.library.compress

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File

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
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(document(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun deleteDocument(documentId: String) {
        document(documentId).delete()
    }

    private fun document(id: String): File {
        require(id.matches(Regex("[a-z0-9.-]+")))
        return File(checkNotNull(context).cacheDir, id)
    }
}
