package com.comst19.dambom.feature.library.subtitle.inference

import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

internal class SubtitleSpeechEvidence(
    private val speechScore: (ShortArray) -> Float,
) {
    private val samples = ShortArray(WINDOW_SAMPLES)
    private val regions = mutableListOf<LongRange>()
    private var pending = 0
    private var windowStart = 0L
    private var previousChunkEnd = 0L
    private var previousSpeech = false
    private var hasConsecutiveSpeech = false

    fun append(chunk: PcmChunk) {
        if (abs(chunk.startMillis - previousChunkEnd) > CLOCK_TOLERANCE_MILLIS) {
            pending = 0
            windowStart = chunk.startMillis
            previousSpeech = false
        }
        previousChunkEnd = chunk.endMillis
        val pcm = ByteBuffer.wrap(chunk.bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        while (pcm.hasRemaining()) {
            val count = minOf(samples.size - pending, pcm.remaining())
            pcm.get(samples, pending, count)
            pending += count
            if (pending < samples.size) continue
            val speech = speechScore(samples) >= SPEECH_THRESHOLD
            if (speech) {
                if (previousSpeech) hasConsecutiveSpeech = true
                addRegion()
            }
            previousSpeech = speech
            windowStart += WINDOW_MILLIS
            pending = 0
        }
    }

    fun retainCues(cues: List<SubtitleCue>): List<SubtitleCue> {
        if (!hasConsecutiveSpeech || regions.size < 2) return cues
        val retained =
            cues.filter { cue ->
                val match = regions.binarySearch { region -> region.last.compareTo(cue.startMillis) }
                val candidate = if (match >= 0) match + 1 else -match - 1
                regions.getOrNull(candidate)?.let { cue.endMillis > it.first } == true
            }
        return retained.ifEmpty { cues }
    }

    private fun addRegion() {
        val start = windowStart - CUE_MARGIN_MILLIS
        val end = windowStart + WINDOW_MILLIS + CUE_MARGIN_MILLIS
        val previous = regions.lastOrNull()
        if (previous != null && start <= previous.last) {
            regions[regions.lastIndex] = previous.first..end
        } else {
            regions.add(start..end)
        }
    }
}

private const val WINDOW_MILLIS = 975
private const val WINDOW_SAMPLES = PCM_SAMPLE_RATE * WINDOW_MILLIS / 1_000
private const val CUE_MARGIN_MILLIS = 500L
private const val CLOCK_TOLERANCE_MILLIS = 2L
private const val SPEECH_THRESHOLD = 0.70f
