package com.comst19.dambom.feature.library.subtitle

import com.comst19.dambom.feature.library.subtitle.inference.PcmChunk
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleSpeechEvidence
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSpeechEvidenceTest {
    @Test fun `isolated speech evidence preserves all song cues`() {
        val scores = listOf(0.92f, 0.2f, 0.75f).iterator()
        val evidence = SubtitleSpeechEvidence { scores.next() }
        evidence.append(chunk(0, 2_925))
        val cues = listOf(SubtitleCue(270, 970, "반짝임"), SubtitleCue(1_410, 2_830, "반짝반짝"))

        assertEquals(cues, evidence.retainCues(cues))
    }

    @Test fun `consecutive evidence across chunk boundaries keeps intersecting cues`() {
        val scores = listOf(0.1f, 0.92f, 0.7f, 0.1f, 0.1f, 0.1f, 0.8f).iterator()
        val evidence = SubtitleSpeechEvidence { scores.next() }
        evidence.append(chunk(0, 2_000))
        evidence.append(chunk(2_000, 6_825))
        val dialogue = SubtitleCue(2_500, 3_750, "대사")
        val background = SubtitleCue(3_425, 3_900, "배경 노래")

        assertEquals(listOf(dialogue), evidence.retainCues(listOf(dialogue, background)))
        assertEquals(listOf(background), evidence.retainCues(listOf(background)))
    }

    @Test fun `speech evidence does not cross a timestamp discontinuity`() {
        val evidence = SubtitleSpeechEvidence { 0.95f }
        evidence.append(chunk(0, 975))
        evidence.append(chunk(3_000, 3_975))
        val cues = listOf(SubtitleCue(300, 900, "첫 구간"), SubtitleCue(4_500, 5_000, "두 번째 구간"))

        assertEquals(cues, evidence.retainCues(cues))
    }

    @Test fun `isolated speech after a confirmed pair protects its own cue`() {
        val scores = listOf(0.92f, 0.8f, 0.1f, 0.1f, 0.1f, 0.8f, 0.1f).iterator()
        val evidence = SubtitleSpeechEvidence { scores.next() }
        evidence.append(chunk(0, 6_825))
        val dialogue = SubtitleCue(0, 1_925, "첫 대사")
        val background = SubtitleCue(2_500, 4_200, "배경 노래")
        val briefDialogue = SubtitleCue(5_100, 6_000, "짧은 대사")

        assertEquals(listOf(dialogue, briefDialogue), evidence.retainCues(listOf(dialogue, background, briefDialogue)))
    }

    @Test fun `one spoken introduction does not remove the song`() {
        val scores = listOf(0.92f, 0.8f, 0.1f, 0.1f, 0.1f).iterator()
        val evidence = SubtitleSpeechEvidence { scores.next() }
        evidence.append(chunk(0, 4_875))
        val cues = listOf(SubtitleCue(0, 1_925, "소개"), SubtitleCue(3_000, 4_200, "노래"))

        assertEquals(cues, evidence.retainCues(cues))
    }

    private fun chunk(
        start: Long,
        end: Long,
    ) = PcmChunk(start, end, ByteArray(((end - start) * 32).toInt()))
}
