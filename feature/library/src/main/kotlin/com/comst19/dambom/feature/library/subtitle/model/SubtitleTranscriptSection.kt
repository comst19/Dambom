package com.comst19.dambom.feature.library.subtitle.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

internal data class SubtitleTranscriptSection(
    val startMillis: Long,
    val cues: ImmutableList<SubtitleCue>,
)

internal fun subtitleTranscriptSections(cues: List<SubtitleCue>): ImmutableList<SubtitleTranscriptSection> =
    cues
        .sortedBy(SubtitleCue::startMillis)
        .groupBy { it.startMillis / TRANSCRIPT_SECTION_MILLIS }
        .map { (minute, entries) ->
            SubtitleTranscriptSection(minute * TRANSCRIPT_SECTION_MILLIS, entries.toPersistentList())
        }.toPersistentList()

private const val TRANSCRIPT_SECTION_MILLIS = 60_000L
