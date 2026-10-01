package com.comst19.dambom.feature.detection

import com.comst19.dambom.core.domain.model.MediaCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionTitlesTest {
    @Test
    fun `missing titles use selected language and retain collection positions`() {
        val candidate = MediaCandidate("id", "https://example.com/video.mp4", "", null, null)

        assertEquals("웹 영상", candidate.displayTitle("웹 영상", 0, 1))
        assertEquals("Web video", candidate.displayTitle("Web video", 0, 1))
        assertEquals("Web video · 2", candidate.displayTitle("Web video", 1, 3))
        assertEquals("Trip", candidate.copy(title = "Trip").displayTitle("Web video", 1, 3))
    }
}
