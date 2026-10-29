@file:Suppress("MagicNumber")

package com.comst19.dambom.feature.library.subtitle.inference

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.comst19.dambom.core.coroutine.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject

internal class VideoAudioDecoder
    @Inject
    constructor(
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        suspend fun decode(
            path: String,
            onChunk: suspend (PcmChunk) -> Unit,
        ) = withContext(io) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(path)
                val track =
                    (0 until extractor.trackCount).firstOrNull {
                        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                    } ?: error("Video has no audio track")
                extractor.selectTrack(track)
                val inputFormat = extractor.getTrackFormat(track)
                val decoder = MediaCodec.createDecoderByType(checkNotNull(inputFormat.getString(MediaFormat.KEY_MIME)))
                codec = decoder
                decoder.configure(inputFormat, null, null, 0)
                decoder.start()
                var outputFormat = inputFormat
                var inputEnded = false
                var outputEnded = false
                val info = MediaCodec.BufferInfo()
                val chunker = PcmChunker()
                while (!outputEnded) {
                    currentCoroutineContext().ensureActive()
                    if (!inputEnded) {
                        val index = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (index >= 0) {
                            val size = extractor.readSampleData(checkNotNull(decoder.getInputBuffer(index)), 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outputFormat = decoder.outputFormat
                    if (index >= 0) {
                        try {
                            val buffer = checkNotNull(decoder.getOutputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val rate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            val encoding =
                                if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                    outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                } else {
                                    AudioFormat.ENCODING_PCM_16BIT
                                }
                            check(channels > 0 && rate >= PCM_SAMPLE_RATE)
                            check(
                                encoding == AudioFormat.ENCODING_PCM_16BIT ||
                                    encoding == AudioFormat.ENCODING_PCM_FLOAT,
                            )
                            val sampleBytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            var frame = 0L
                            while (buffer.remaining() >= channels * sampleBytes) {
                                var mono = 0f
                                repeat(channels) {
                                    mono +=
                                        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                            buffer.float
                                        } else {
                                            buffer.short / PCM_SCALE
                                        }
                                }
                                val time = info.presentationTimeUs + frame * 1_000_000 / rate
                                chunker.append(mono / channels, time)?.let { onChunk(it) }
                                frame++
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally {
                            decoder.releaseOutputBuffer(index, false)
                        }
                    }
                }
                chunker.drain()?.let { onChunk(it) }
            } finally {
                codec?.release()
                extractor.release()
            }
        }
    }

private const val CODEC_TIMEOUT_US = 10_000L
private const val PCM_SCALE = 32768f
