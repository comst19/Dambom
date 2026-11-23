package com.comst19.dambom.feature.library.export

import android.net.Uri
import java.io.File
import javax.inject.Inject

internal class VideoExportFiles
    @Inject
    constructor(
        private val documents: ExportDocumentStore,
    ) {
        suspend fun save(
            destination: String,
            transform: suspend (File) -> Unit,
        ): Long = documents.save(Uri.parse(destination), transform) { it }

        suspend fun discard(destination: String) = documents.discard(Uri.parse(destination))
    }
