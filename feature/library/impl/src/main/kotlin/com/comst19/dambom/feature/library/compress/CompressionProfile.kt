package com.comst19.dambom.feature.library.compress

import kotlin.math.min

@Suppress("MagicNumber")
internal enum class CompressionProfile(
    val shortEdge: Int,
    val maxBitrate: Int,
    val percent: Int,
) {
    SMALL(480, 800_000, 40),
    BALANCED(720, 1_600_000, 60),
    QUALITY(1080, 3_000_000, 75),
}

internal data class CompressionSource(
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val bytes: Long,
    val bitrate: Int,
) {
    fun settings(profile: CompressionProfile): CompressionSettings {
        val scale = min(1.0, profile.shortEdge.toDouble() / min(width, height))
        val videoBitrate =
            min(profile.maxBitrate.toLong(), bitrate.toLong() * profile.percent / PERCENT_SCALE)
                .coerceIn(MIN_VIDEO_BITRATE, Int.MAX_VALUE.toLong())
                .toInt()
        return CompressionSettings(
            width = ((width * scale).toInt() / 2 * 2).coerceAtLeast(2),
            height = ((height * scale).toInt() / 2 * 2).coerceAtLeast(2),
            videoBitrate = videoBitrate,
            estimatedBytes =
                (videoBitrate.toLong() + COMPRESSION_AUDIO_BITRATE) * durationMillis / BITS_PER_BYTE_MILLIS,
        )
    }
}

internal data class CompressionSettings(
    val width: Int,
    val height: Int,
    val videoBitrate: Int,
    val estimatedBytes: Long,
)

internal const val COMPRESSION_AUDIO_BITRATE = 96_000

private const val PERCENT_SCALE = 100
private const val MIN_VIDEO_BITRATE = 64_000L
internal const val BITS_PER_BYTE_MILLIS = 8_000
