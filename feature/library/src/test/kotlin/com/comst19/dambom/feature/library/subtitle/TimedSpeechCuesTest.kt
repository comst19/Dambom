package com.comst19.dambom.feature.library.subtitle

import com.comst19.dambom.feature.library.subtitle.inference.TimedSpeechWord
import com.comst19.dambom.feature.library.subtitle.inference.timedSpeechCues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedSpeechCuesTest {
    @Test fun `measured starts and silence gaps survive translation grouping`() {
        val words = listOf(TimedSpeechWord("Hello.", 400), TimedSpeechWord("A", 2_300), TimedSpeechWord("test.", 2_500))
        val cues = timedSpeechCues(words, 8_000, 16_000)
        assertEquals(listOf(8_400L, 10_300L), cues.map { it.startMillis })
        assertEquals(listOf(9_100L, 11_200L), cues.map { it.endMillis })
        assertEquals(listOf("Hello.", "A test."), cues.map { it.text })
    }

    @Test fun `continuous speech is bounded using measured word boundaries`() {
        val words = List(20) { TimedSpeechWord("word$it", it * 400L) }
        val cues = timedSpeechCues(words, 0, 8_000)
        assertTrue(cues.size > 1)
        assertTrue(cues.all { cue -> words.any { it.timestampMillis == cue.startMillis } })
        assertEquals(words.joinToString(" ") { it.text }, cues.joinToString(" ") { it.text })
        assertEquals(8_000L, cues.last().endMillis)
    }

    @Test fun `absent or invalid timestamps never fabricate captions`() {
        assertTrue(timedSpeechCues(emptyList(), 100, 200).isEmpty())
        val invalid = listOf(TimedSpeechWord("bad", -1), TimedSpeechWord("late", 200))
        assertTrue(timedSpeechCues(invalid, 100, 200).isEmpty())
    }
}
