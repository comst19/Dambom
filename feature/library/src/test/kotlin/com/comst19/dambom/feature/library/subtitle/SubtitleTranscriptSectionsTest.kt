package com.comst19.dambom.feature.library.subtitle

import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.subtitleTranscriptSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTranscriptSectionsTest {
    @Test fun `long transcript is ordered into minute sections without dropping or shortening text`() {
        val longText = "긴 자막 문단입니다. ".repeat(300)
        val cues =
            listOf(
                SubtitleCue(3_600_000, 3_608_000, longText),
                SubtitleCue(60_000, 68_000, "다음 분"),
                SubtitleCue(59_999, 64_000, "분 경계를 지나는 자막"),
                SubtitleCue(0, 8_000, "첫 문단"),
            )
        val sections = subtitleTranscriptSections(cues)
        assertEquals(listOf(0L, 60_000L, 3_600_000L), sections.map { it.startMillis })
        assertEquals(cues.sortedBy { it.startMillis }, sections.flatMap { it.cues })
        assertEquals(
            longText,
            sections
                .last()
                .cues
                .last()
                .text,
        )
    }

    @Test fun `equal timestamps are retained and an empty transcript has no sections`() {
        val cues = listOf(SubtitleCue(0, 1_000, "첫 문장"), SubtitleCue(0, 1_000, "다음 문장"))
        assertEquals(cues, subtitleTranscriptSections(cues).single().cues)
        assertTrue(subtitleTranscriptSections(emptyList()).isEmpty())
    }
}
