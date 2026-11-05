package com.comst19.dambom.feature.library.subtitle.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.core.common.io.videoSubtitleTemporaryFile
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.parseGeneratedSrt
import com.comst19.dambom.feature.library.subtitle.model.toSrt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

internal class SubtitleFiles
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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
            var saved = false
            try {
                withContext(io) {
                    File(path).videoSubtitleFile().inputStream().use { input ->
                        checkNotNull(context.contentResolver.openOutputStream(destination, "wt")).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    saved = true
                }
            } finally {
                if (!saved) discard(destination)
            }
        }

        suspend fun discard(destination: Uri) =
            withContext(NonCancellable + io) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
                Unit
            }
    }
