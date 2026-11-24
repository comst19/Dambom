@file:Suppress("MagicNumber")

package com.comst19.dambom.feature.library.subtitle.model

import java.util.Locale

internal fun List<SubtitleCue>.toSrt(): String =
    filter { it.text.isNotBlank() && it.endMillis > it.startMillis }
        .mapIndexed { index, cue ->
            "${index + 1}\n${cue.startMillis.srtTime()} --> ${cue.endMillis.srtTime()}\n" +
                cue.text.trim().replace(Regex("\\n\\s*\\n"), "\n") + "\n"
        }.joinToString("\n")

internal fun parseGeneratedSrt(content: String): List<SubtitleCue> =
    content.trim().split(Regex("\\r?\\n\\s*\\r?\\n")).mapNotNull { block ->
        val lines = block.lines()
        if (lines.size < 3) return@mapNotNull null
        val times = lines[1].split(" --> ")
        if (times.size != 2) return@mapNotNull null
        val start = times[0].parseSrtTime() ?: return@mapNotNull null
        val end = times[1].parseSrtTime() ?: return@mapNotNull null
        if (end <= start) return@mapNotNull null
        SubtitleCue(start, end, lines.drop(2).joinToString("\n"))
    }

private fun Long.srtTime(): String =
    String.format(
        Locale.ROOT,
        "%02d:%02d:%02d,%03d",
        this / 3_600_000,
        this / 60_000 % 60,
        this / 1_000 % 60,
        this % 1_000,
    )

@Suppress("ReturnCount", "ComplexCondition")
private fun String.parseSrtTime(): Long? {
    val parts = split(':', ',')
    if (parts.size != 4) return null
    val values = parts.map { it.toLongOrNull() ?: return null }
    if (values[0] < 0 || values[1] !in 0..59 || values[2] !in 0..59 || values[3] !in 0..999) return null
    return values[0] * 3_600_000 + values[1] * 60_000 + values[2] * 1_000 + values[3]
}
