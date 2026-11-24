package com.comst19.dambom.feature.library.pip

import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToLong

data class PipEligibility(
    val isSystemPipSupported: Boolean,
    val isFullscreen: Boolean,
    val isPlaying: Boolean,
    val hasCompletedLocalVideo: Boolean,
) {
    val isEligible: Boolean
        get() = isSystemPipSupported && isFullscreen && isPlaying && hasCompletedLocalVideo
}

data class PipAspectRatio(
    val numerator: Int,
    val denominator: Int,
) {
    init {
        require(numerator > 0 && denominator > 0)
    }
}

fun pipAspectRatio(
    videoWidth: Int,
    videoHeight: Int,
): PipAspectRatio? {
    if (videoWidth <= 0 || videoHeight <= 0) return null

    val ratio = videoWidth.toDouble() / videoHeight
    if (ratio < MIN_PIP_ASPECT_RATIO) {
        return PipAspectRatio(
            numerator = MIN_PIP_ASPECT_NUMERATOR,
            denominator = MIN_PIP_ASPECT_DENOMINATOR,
        )
    }
    if (ratio > MAX_PIP_ASPECT_RATIO) {
        return PipAspectRatio(
            numerator = MAX_PIP_ASPECT_NUMERATOR,
            denominator = MAX_PIP_ASPECT_DENOMINATOR,
        )
    }
    return PipAspectRatio(numerator = videoWidth, denominator = videoHeight)
}

fun fittedVideoBounds(
    container: IntRect,
    videoWidth: Int,
    videoHeight: Int,
): IntRect? {
    if (videoWidth <= 0 || videoHeight <= 0) return null

    val containerWidth = container.right.toLong() - container.left.toLong()
    val containerHeight = container.bottom.toLong() - container.top.toLong()
    if (containerWidth !in 1..Int.MAX_VALUE || containerHeight !in 1..Int.MAX_VALUE) return null

    val videoAspectRatio = videoWidth.toDouble() / videoHeight
    val containerAspectRatio = containerWidth.toDouble() / containerHeight
    val visibleWidth: Long
    val visibleHeight: Long
    if (videoAspectRatio > containerAspectRatio) {
        visibleWidth = containerWidth
        visibleHeight = (containerWidth / videoAspectRatio).roundToLong()
    } else {
        visibleWidth = (containerHeight * videoAspectRatio).roundToLong()
        visibleHeight = containerHeight
    }
    if (visibleWidth !in 1..containerWidth || visibleHeight !in 1..containerHeight) return null

    val left = container.left.toLong() + (containerWidth - visibleWidth) / 2
    val top = container.top.toLong() + (containerHeight - visibleHeight) / 2
    val right = left + visibleWidth
    val bottom = top + visibleHeight
    if (left !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
        top !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
        right !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
        bottom !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    ) {
        return null
    }

    return IntRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}

private const val MIN_PIP_ASPECT_NUMERATOR = 100
private const val MIN_PIP_ASPECT_DENOMINATOR = 239
private const val MAX_PIP_ASPECT_NUMERATOR = 239
private const val MAX_PIP_ASPECT_DENOMINATOR = 100
private const val MIN_PIP_ASPECT_RATIO = MIN_PIP_ASPECT_NUMERATOR.toDouble() / MIN_PIP_ASPECT_DENOMINATOR
private const val MAX_PIP_ASPECT_RATIO = MAX_PIP_ASPECT_NUMERATOR.toDouble() / MAX_PIP_ASPECT_DENOMINATOR
