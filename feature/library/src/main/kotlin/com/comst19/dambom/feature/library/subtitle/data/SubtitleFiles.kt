package com.comst19.dambom.feature.library.subtitle.data

import android.net.Uri
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.core.common.io.videoSubtitleTemporaryFile
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.feature.library.export.ExportDocumentStore
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.parseGeneratedSrt
import com.comst19.dambom.feature.library.subtitle.model.toSrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

internal class SubtitleFiles
    @Inject
    constructor(
        private val documents: ExportDocumentStore,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        suspend fun save(
            path: String,
            cues: List<SubtitleCue>,
        ) = withContext(io) {
            val source = File(path)
            val temporary = source.videoSubtitleTemporaryFile()
            try {
                check(source.isFile)
                temporary.writeText(cues.toSrt())
                currentCoroutineContext().ensureActive()
                check(source.isFile)
                check(temporary.renameTo(source.videoSubtitleFile()))
            } finally {
                temporary.delete()
            }
        }

        suspend fun read(path: String): List<SubtitleCue> =
            withContext(io) {
                File(path)
                    .videoSubtitleFile()
                    .takeIf(File::isFile)
                    ?.let { parseGeneratedSrt(it.readText()) }
                    .orEmpty()
            }

        suspend fun export(
            path: String,
            destination: Uri,
        ) {
            documents.save(destination) { File(path).videoSubtitleFile() }
        }

        suspend fun discard(destination: Uri) = documents.discard(destination)
    }
