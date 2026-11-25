package com.comst19.dambom.feature.library.export

import androidx.core.net.toUri
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
        ): Long = documents.save(destination.toUri(), transform) { it }

        suspend fun discard(destination: String) = documents.discard(destination.toUri())
    }
