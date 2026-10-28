package com.comst19.dambom.feature.library.compress

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.feature.library.export.VideoExportFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@UnstableApi
internal class Media3VideoCompressor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val files: VideoExportFiles,
    ) : VideoCompressor {
        override suspend fun inspect(path: String): CompressionSource =
            withContext(ioDispatcher) {
                val file = File(path)
                check(file.isFile && file.length() > 0L)
                val reader = MediaMetadataRetriever()
                try {
                    reader.setDataSource(path)
                    val width =
                        checkNotNull(
                            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                        )
                    val height =
                        checkNotNull(
                            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                        )
                    val duration =
                        checkNotNull(
                            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                        )
                    check(width >= 2 && height >= 2 && duration > 0L)
                    val rotated =
                        (
                            reader
                                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                                ?.toIntOrNull() ?: 0
                        ) % HALF_TURN_DEGREES != 0
                    val bitrate =
                        reader
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                            ?.toIntOrNull()
                            ?.takeIf { it > 0 }
                            ?: (file.length().toDouble() * BITS_PER_BYTE_MILLIS / duration)
                                .coerceIn(1.0, Int.MAX_VALUE.toDouble())
                                .toInt()
                    CompressionSource(
                        width = if (rotated) height else width,
                        height = if (rotated) width else height,
                        durationMillis = duration,
                        bytes = file.length(),
                        bitrate = bitrate,
                    )
                } finally {
                    reader.release()
                }
            }

        override suspend fun compress(
            path: String,
            profile: CompressionProfile,
            destination: String,
            onProgress: (Int?) -> Unit,
        ): Long =
            files.save(destination, "compress") { output ->
                val source = inspect(path)
                val settings = source.settings(profile)
                transform(path, settings, output, onProgress)
                withContext(ioDispatcher) {
                    if (output.length() >= source.bytes) {
                        throw CompressionNotSmallerException(
                            "Input=${source.bytes}, output=${output.length()}, settings=$settings",
                        )
                    }
                }
                onProgress(null)
            }

        override suspend fun discard(destination: String) = files.discard(destination)

        @Suppress("LongMethod")
        private suspend fun transform(
            path: String,
            settings: CompressionSettings,
            output: File,
            onProgress: (Int?) -> Unit,
        ) = withContext(Dispatchers.Main.immediate) {
            val completed = CompletableDeferred<Unit>()
            val encoder =
                DefaultEncoderFactory
                    .Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings
                            .Builder()
                            .setBitrate(settings.videoBitrate)
                            .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                            .build(),
                    ).setRequestedAudioEncoderSettings(
                        AudioEncoderSettings.Builder().setBitrate(COMPRESSION_AUDIO_BITRATE).build(),
                    ).build()
            val transformer =
                Transformer
                    .Builder(context)
                    .setEncoderFactory(encoder)
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
            val item =
                EditedMediaItem
                    .Builder(MediaItem.fromUri(Uri.fromFile(File(path))))
                    .setEffects(
                        Effects(
                            emptyList(),
                            listOf(
                                Presentation.createForWidthAndHeight(
                                    settings.width,
                                    settings.height,
                                    Presentation.LAYOUT_SCALE_TO_FIT,
                                ),
                            ),
                        ),
                    ).build()
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
                transformer.start(item, output.absolutePath)
                completed.await()
            } finally {
                progress.cancel()
                transformer.cancel()
            }
        }
    }

private const val HALF_TURN_DEGREES = 180
private const val PROGRESS_INTERVAL_MILLIS = 300L
