@file:Suppress("MagicNumber")

package com.comst19.dambom.feature.library.subtitle.inference

import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

internal data class PcmChunk(
    val startMillis: Long,
    val endMillis: Long,
    val bytes: ByteArray,
)

internal class PcmChunker(
    private val chunkSamples: Int = PCM_SAMPLE_RATE * 8,
) {
    private val pending = ByteArrayOutputStream(chunkSamples * 2)
    private var originUs = -1L
    private var emittedSamples = 0L
    private var firstSampleUs = 0L
    private var lastSampleUs = 0L

    fun append(
        sample: Float,
        timestampUs: Long,
    ): PcmChunk? {
        if (originUs < 0 && timestampUs >= 0) originUs = timestampUs
        // Whole-microsecond timestamps truncate the 62.5 us interval at 16 kHz.
        val sampleIndex = (timestampUs - originUs + 1) * PCM_SAMPLE_RATE / 1_000_000
        if (timestampUs < 0 || sampleIndex < emittedSamples) return null
        if (pending.size() == 0) firstSampleUs = timestampUs
        val value = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt()
        pending.write(value and 0xff)
        pending.write((value shr 8) and 0xff)
        lastSampleUs = timestampUs
        emittedSamples = sampleIndex + 1
        return if (pending.size() >= chunkSamples * 2) drain() else null
    }

    fun drain(): PcmChunk? {
        if (pending.size() == 0) return null
        return PcmChunk(firstSampleUs / 1_000, (lastSampleUs + PCM_SAMPLE_INTERVAL_US) / 1_000, pending.toByteArray())
            .also { pending.reset() }
    }
}

internal const val PCM_SAMPLE_RATE = 16_000
private const val PCM_SAMPLE_INTERVAL_US = (1_000_000L + PCM_SAMPLE_RATE - 1) / PCM_SAMPLE_RATE
