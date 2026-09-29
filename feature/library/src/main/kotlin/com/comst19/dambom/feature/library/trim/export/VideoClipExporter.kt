package com.comst19.dambom.feature.library.trim.export

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import com.comst19.dambom.feature.library.trim.media.clippedMediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@UnstableApi
internal class VideoClipExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ClipExporter {
        override suspend fun durationMillis(path: String): Long =
            withContext(ioDispatcher) {
                check(File(path).isFile)
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    checkNotNull(duration?.toLongOrNull()).also { check(it > 0L) }
                } finally {
                    retriever.release()
                }
            }

        override suspend fun export(
            path: String,
            selection: TrimSelection,
            destination: String,
            onProgress: (Int?) -> Unit,
        ) {
            val temporary = File(context.cacheDir, "trim-${UUID.randomUUID()}.mp4")
            val targetUri = Uri.parse(destination)
            var saved = false
            try {
                require(selection.isValidFor(durationMillis(path)))
                transform(path, selection, temporary, onProgress)
                onProgress(null)
                withContext(ioDispatcher) {
                    temporary.inputStream().use { input ->
                        val stream = checkNotNull(context.contentResolver.openOutputStream(targetUri, "wt"))
                        stream.use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var count = input.read(buffer)
                            while (count >= 0) {
                                currentCoroutineContext().ensureActive()
                                output.write(buffer, 0, count)
                                count = input.read(buffer)
                            }
                        }
                    }
                }
                saved = true
            } finally {
                withContext(NonCancellable) {
                    withContext(ioDispatcher) { temporary.delete() }
                    if (!saved) {
                        discard(destination)
                    }
                }
            }
        }

        override suspend fun discard(destination: String) {
            withContext(ioDispatcher) {
                suspendRunCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(destination)) }
            }
        }

        private suspend fun transform(
            path: String,
            selection: TrimSelection,
            output: File,
            onProgress: (Int?) -> Unit,
        ) = withContext(Dispatchers.Main.immediate) {
            val completed = CompletableDeferred<Unit>()
            val transformer =
                Transformer
                    .Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult,
                            ) {
                                completed.complete(Unit)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                completed.completeExceptionally(exportException)
                            }
                        },
                    ).build()
            val item = clippedMediaItem(path, selection)
            val progress =
                launch {
                    val holder = ProgressHolder()
                    while (true) {
                        delay(PROGRESS_INTERVAL_MILLIS)
                        onProgress(
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                holder.progress
                            } else {
                                null
                            },
                        )
                    }
                }
            try {
                transformer.start(EditedMediaItem.Builder(item).build(), output.absolutePath)
                completed.await()
            } finally {
                progress.cancel()
                transformer.cancel()
            }
        }
    }

private const val PROGRESS_INTERVAL_MILLIS = 300L
