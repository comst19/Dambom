package com.comst19.dambom.feature.library.subtitle.export

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.FrameworkMuxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.comst19.dambom.core.common.util.VideoExportGate
import com.comst19.dambom.feature.library.export.VideoExportFiles
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal interface SubtitleVideoExporter {
    suspend fun export(
        path: String,
        cues: List<SubtitleCue>,
        destination: String,
        onProgress: (Int?) -> Unit,
    )

    suspend fun discard(destination: String)
}

@UnstableApi
internal class Media3SubtitleVideoExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val files: VideoExportFiles,
    ) : SubtitleVideoExporter {
        override suspend fun export(
            path: String,
            cues: List<SubtitleCue>,
            destination: String,
            onProgress: (Int?) -> Unit,
        ) {
            files.save(destination, "subtitled") { output ->
                require(cues.isNotEmpty())
                VideoExportGate.shared.run {
                    withContext(Dispatchers.Main.immediate) {
                        val done = CompletableDeferred<Unit>()
                        val transformer =
                            Transformer
                                .Builder(context)
                                .setMuxerFactory(FrameworkMuxer.Factory())
                                .setVideoMimeType(MimeTypes.VIDEO_H264)
                                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                                .addListener(
                                    object : Transformer.Listener {
                                        override fun onCompleted(
                                            composition: Composition,
                                            exportResult: ExportResult,
                                        ) {
                                            done.complete(Unit)
                                        }

                                        override fun onError(
                                            composition: Composition,
                                            exportResult: ExportResult,
                                            exportException: ExportException,
                                        ) {
                                            done.completeExceptionally(exportException)
                                        }
                                    },
                                ).build()
                        val item =
                            EditedMediaItem
                                .Builder(MediaItem.fromUri(path))
                                .setEffects(
                                    Effects(emptyList(), listOf(OverlayEffect(listOf(SubtitleBitmapOverlay(cues))))),
                                ).build()
                        val progress =
                            launch {
                                val holder = ProgressHolder()
                                while (true) {
                                    delay(EXPORT_PROGRESS_INTERVAL_MILLIS)
                                    onProgress(
                                        if (transformer.getProgress(holder) ==
                                            Transformer.PROGRESS_STATE_AVAILABLE
                                        ) {
                                            holder.progress
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        try {
                            transformer.start(item, output.absolutePath)
                            done.await()
                        } finally {
                            progress.cancel()
                            transformer.cancel()
                        }
                    }
                }
            }
        }

        override suspend fun discard(destination: String) = files.discard(destination)
    }

private const val EXPORT_PROGRESS_INTERVAL_MILLIS = 300L
