package com.comst19.dambom.feature.library.subtitle

import com.comst19.dambom.feature.library.subtitle.inference.PcmChunk
import com.comst19.dambom.feature.library.subtitle.inference.PcmChunker
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import com.comst19.dambom.feature.library.subtitle.model.parseGeneratedSrt
import com.comst19.dambom.feature.library.subtitle.model.toSrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SubtitleCueTest {
    @Test fun `SRT keeps unicode multiline text and hour boundaries`() {
        val cues = listOf(SubtitleCue(3_599_980, 3_601_234, "안녕하세요\nこんにちは"))
        val srt = cues.toSrt()
        assertEquals("1\n00:59:59,980 --> 01:00:01,234\n안녕하세요\nこんにちは\n", srt)
        assertEquals(cues, parseGeneratedSrt(srt))
    }

    @Test fun `blank output and invalid timing are excluded and entries renumbered`() {
        val cues = listOf(SubtitleCue(0, 100, " "), SubtitleCue(300, 200, "wrong"), SubtitleCue(500, 900, "Hello"))
        assertEquals("1\n00:00:00,500 --> 00:00:00,900\nHello\n", cues.toSrt())
        assertEquals(emptyList<SubtitleCue>(), parseGeneratedSrt("1\n00:99:00,000 --> 00:00:01,000\nwrong"))
    }

    @Test fun `text cannot introduce an empty block that breaks the generated file`() {
        val srt = listOf(SubtitleCue(0, 800, "First\n\nSecond")).toSrt()
        assertEquals("First\nSecond", parseGeneratedSrt(srt).single().text)
    }

    @Test fun `only the three requested directed language pairs exist`() {
        assertEquals(
            listOf("en-US" to "ko-KR", "ja-JP" to "ko-KR", "ja-JP" to "en-US"),
            SubtitleLanguagePair.entries.map { it.source to it.target },
        )
    }

    @Test fun `48 kHz sampling produces exactly 16 kHz without integer interval drift`() {
        val chunker = PcmChunker(16_000)
        val chunks = mutableListOf<PcmChunk>()
        repeat(48_000) { frame -> chunker.append(0.5f, frame * 1_000_000L / 48_000)?.let(chunks::add) }
        chunker.drain()?.let(chunks::add)
        assertEquals(32_000, chunks.sumOf { it.bytes.size })
        assertEquals(0, chunks.first().startMillis)
        assertEquals(1000, chunks.last().endMillis)
    }

    @Test fun `16 kHz input keeps samples with truncated microsecond timestamps`() {
        val chunker = PcmChunker(16_000)
        val chunks = mutableListOf<PcmChunk>()
        repeat(16_000) { frame -> chunker.append(0.5f, frame * 1_000_000L / 16_000)?.let(chunks::add) }
        chunker.drain()?.let(chunks::add)
        assertEquals(32_000, chunks.sumOf { it.bytes.size })
        assertEquals(1000, chunks.last().endMillis)
    }

    @Test fun `chunk timestamps preserve a delayed audio start and the last partial chunk`() {
        val chunker = PcmChunker(2)
        val chunks = mutableListOf<PcmChunk>()
        listOf(2_000_000L, 2_000_063L, 2_000_125L).forEach { chunker.append(-0.5f, it)?.let(chunks::add) }
        chunker.drain()?.let(chunks::add)
        assertEquals(listOf(4, 2), chunks.map { it.bytes.size })
        assertEquals(2_000, chunks.first().startMillis)
        assertFalse(chunks.first().bytes.all { it == 0.toByte() })
    }
}
