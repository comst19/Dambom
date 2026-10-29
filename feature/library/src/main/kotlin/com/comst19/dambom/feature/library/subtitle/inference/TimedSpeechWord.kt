package com.comst19.dambom.feature.library.subtitle.inference

import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue

internal data class TimedSpeechWord(
    val text: String,
    val timestampMillis: Long,
)

internal fun timedSpeechCues(
    words: List<TimedSpeechWord>,
    startMillis: Long,
    endMillis: Long,
): List<SubtitleCue> {
    val ordered =
        words
            .filter { it.text.isNotBlank() && it.timestampMillis in 0 until endMillis - startMillis }
            .distinct()
            .sortedBy { it.timestampMillis }
    val cues = mutableListOf<SubtitleCue>()
    var first = 0
    ordered.forEachIndexed { index, word ->
        val next = ordered.getOrNull(index + 1)
        val sentenceEnd = word.text.last() in ".!?。！？"
        val pause = next != null && next.timestampMillis - word.timestampMillis > SPEECH_PAUSE_MILLIS
        val bounded = next != null && next.timestampMillis - ordered[first].timestampMillis >= MAX_SPEECH_CUE_MILLIS
        val boundary = sentenceEnd || pause || bounded
        if (next == null || boundary) {
            val end =
                minOf(
                    endMillis - startMillis,
                    next?.timestampMillis ?: endMillis - startMillis,
                    word.timestampMillis + LAST_WORD_HOLD_MILLIS,
                )
            val text = ordered.subList(first, index + 1).joinToString(" ") { it.text }
            val start = ordered[first].timestampMillis
            if (end > start) cues.add(SubtitleCue(startMillis + start, startMillis + end, text))
            first = index + 1
        }
    }
    return cues
}

private const val SPEECH_PAUSE_MILLIS = 1_000L
private const val MAX_SPEECH_CUE_MILLIS = 3_000L
private const val LAST_WORD_HOLD_MILLIS = 700L
