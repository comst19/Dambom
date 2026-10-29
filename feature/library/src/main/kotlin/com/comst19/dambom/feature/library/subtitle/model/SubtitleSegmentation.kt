package com.comst19.dambom.feature.library.subtitle.model

internal fun splitSubtitleCue(cue: SubtitleCue): List<SubtitleCue> {
    val normalized = cue.text.replace(WHITESPACE, " ").trim()
    val parts = normalized.split(SENTENCE_BREAK).filter(String::isNotBlank).flatMap(::captionSentenceParts)
    val duration = cue.endMillis - cue.startMillis
    if (parts.size <= 1 || duration < parts.size) {
        return listOf(if (normalized == cue.text) cue else cue.copy(text = normalized))
    }
    val totalUnits = parts.sumOf(::captionWidthUnits).toLong()
    var consumedUnits = 0L
    var start = cue.startMillis
    return parts.mapIndexed { index, text ->
        consumedUnits += captionWidthUnits(text)
        val end = cue.startMillis + index + 1 + (duration - parts.size) * consumedUnits / totalUnits
        SubtitleCue(start, end, text).also { start = end }
    }
}

private fun captionSentenceParts(sentence: String): List<String> {
    val parts = mutableListOf<String>()
    var current = ""
    for (word in WORD.findAll(sentence).flatMap { captionWordParts(it.value) }) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (captionWidthUnits(candidate) > MAX_CAPTION_UNITS) {
            parts.add(current)
            current = word
        } else {
            current = candidate
        }
    }
    if (current.isNotEmpty()) parts.add(current)
    return parts
}

private fun captionWordParts(word: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var width = 0
    for (point in word.codePoints().toArray()) {
        val units = if (point < WIDE_CHARACTER_START) 1 else 2
        if (width + units > MAX_CAPTION_UNITS) {
            parts.add(current.toString())
            current.clear()
            width = 0
        }
        current.appendCodePoint(point)
        width += units
    }
    if (current.isNotEmpty()) parts.add(current.toString())
    return parts
}

private fun captionWidthUnits(text: String): Int {
    val points = text.codePoints().toArray()
    return points.sumOf { if (it < WIDE_CHARACTER_START) 1 else 2 }
}

private val WHITESPACE = Regex("\\s+")
private val WORD = Regex("\\S+")
private val SENTENCE_BREAK = Regex("(?<=[.!?])\\s+|(?<=[。！？])")
private const val MAX_CAPTION_UNITS = 64
private const val WIDE_CHARACTER_START = 0x100
