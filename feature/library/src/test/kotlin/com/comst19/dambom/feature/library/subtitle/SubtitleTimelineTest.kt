package com.comst19.dambom.feature.library.subtitle

import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.playback.SubtitleTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleTimelineTest {
    @Test fun `seeking in either direction respects cue boundaries and gaps`() {
        val first = SubtitleCue(100, 200, "first")
        val second = SubtitleCue(300, 400, "second")
        val timeline = SubtitleTimeline(listOf(second, first))
        assertNull(timeline.at(0))
        assertEquals(second, timeline.at(300))
        assertNull(timeline.at(400))
        assertEquals(first, timeline.at(199))
        assertNull(timeline.at(200))
        assertEquals(first, timeline.at(100))
        assertNull(SubtitleTimeline(emptyList()).at(0))
    }

    @Test fun `long subtitles resolve the beginning middle and end`() {
        val cues = List(100_000) { index -> SubtitleCue(index * 1000L, index * 1000L + 800, "$index") }
        val timeline = SubtitleTimeline(cues)
        for (index in listOf(0, 50_000, 99_999)) {
            assertEquals(cues[index], timeline.at(index * 1000L + 500))
            assertNull(timeline.at(index * 1000L + 900))
        }
    }

    @Test fun `playback never invents timing by splitting translated text`() {
        val cue = SubtitleCue(200, 8_000, "첫 문장입니다. 다음 문장입니다.")
        val timeline = SubtitleTimeline(listOf(cue))
        assertEquals(cue, timeline.at(200))
        assertEquals(cue, timeline.at(7_999))
        assertNull(timeline.at(8_000))
    }
}
