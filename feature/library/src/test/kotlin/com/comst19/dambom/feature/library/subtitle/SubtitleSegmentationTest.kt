package com.comst19.dambom.feature.library.subtitle

import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.splitSubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSegmentationTest {
    @Test fun `sentences share the original interval without gaps or missing text`() {
        val text = "안녕하세요, 이것은 자막 번역 테스트입니다. 오늘은 날씨가 맑습니다. 밖에 나갈 때 물 한 병을 가져가 주세요."
        val parts = splitSubtitleCue(SubtitleCue(100, 8_100, text))
        assertEquals(3, parts.size)
        assertEquals(text, parts.joinToString(" ") { it.text })
        assertEquals(100L, parts.first().startMillis)
        assertEquals(8_100L, parts.last().endMillis)
        assertTrue(parts.all { it.endMillis > it.startMillis })
        assertTrue(parts.zipWithNext().all { (left, right) -> left.endMillis == right.startMillis })
    }

    @Test fun `long words retain complete Unicode code points and every character`() {
        val text = "あ😀".repeat(80)
        val parts = splitSubtitleCue(SubtitleCue(0, 8_000, text))
        assertTrue(parts.size > 1)
        assertEquals(text, parts.joinToString("") { it.text })
        assertTrue(parts.all { it.text.codePoints().count() <= 32 })
        assertEquals(parts, parts.flatMap(::splitSubtitleCue))
    }

    @Test fun `English words are wrapped without dropping punctuation or decimal numbers`() {
        val text =
            "The model is 2.6 gigabytes and this long subtitle should be displayed " +
                "in smaller readable pieces on screen."
        val parts = splitSubtitleCue(SubtitleCue(0, 8_000, text))
        assertTrue(parts.size > 1)
        assertEquals(text, parts.joinToString(" ") { it.text })
        assertTrue(parts.all { it.text.length <= 64 })
    }

    @Test fun `short existing cues retain their timing and text`() {
        val cue = SubtitleCue(500, 900, "짧은 자막")
        assertEquals(listOf(cue), splitSubtitleCue(cue))
    }
}
