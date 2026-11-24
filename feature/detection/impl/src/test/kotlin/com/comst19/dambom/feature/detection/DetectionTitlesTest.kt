package com.comst19.dambom.feature.detection

import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.feature.detection.contract.DetectionUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionTitlesTest {
    @Test
    fun `all detection paths share page fallback and keep collection positions`() {
        val state =
            DetectionUiState.Content(
                pageTitle = "A useful page title",
                selectedIds = persistentSetOf(),
                candidates =
                    persistentListOf(
                        MediaCandidate("one", "https://cdn.example.com/index.m3u8", "index", null, null),
                        MediaCandidate("two", "https://cdn.example.com/master.mpd", "master", null, null),
                    ),
            )
        val content = state.withFallbackTitles("웹 영상")
        assertEquals("A useful page title · 1", content.candidates[0].title)
        assertEquals("A useful page title · 2", content.candidates[1].title)
        assertEquals(
            content.candidates[1].title,
            state.candidates[1].displayTitle("웹 영상", 1, 2, state.pageTitle),
        )
        val loading = state.copy(pageTitle = "확인 중").withFallbackTitles("웹 영상")
        assertEquals("웹 영상", loading.pageTitle)
        assertEquals("웹 영상 · 1", loading.candidates[0].title)
    }

    @Test
    fun `encoded human titles keep readable words and literal plus signs`() {
        val candidate =
            MediaCandidate("id", "https://example.com/video.mp4", "%EC%97%AC%ED%96%89 + friends.mp4", null, null)
        assertEquals("여행 + friends", candidate.displayTitle("웹 영상", 0, 1))
    }

    @Test
    fun `technical source labels are never used as download titles`() {
        val technicalTitles =
            listOf(
                "6B1BD4F27A3A40B5A77DE952CBCEBD44",
                "550e8400-e29b-41d4-a716-446655440000",
                "items1",
                "master",
                "playlist.m3u8",
                "index.mpd",
                "https://cdn.example.com/video.mp4?token=value",
                "blob:https://example.com/id",
                "확인 중",
                "Just a moment...",
            )
        technicalTitles.forEach { title ->
            val candidate = MediaCandidate("id", "https://cdn.example.com/video", title, null, null)
            assertEquals(title, "웹 영상", candidate.displayTitle("웹 영상", 0, 1))
        }
    }

    @Test
    fun `missing titles use selected language and retain collection positions`() {
        val candidate = MediaCandidate("id", "https://example.com/video.mp4", "", null, null)

        assertEquals("웹 영상", candidate.displayTitle("웹 영상", 0, 1))
        assertEquals("Web video", candidate.displayTitle("Web video", 0, 1))
        assertEquals("Web video · 2", candidate.displayTitle("Web video", 1, 3))
        assertEquals("Trip", candidate.copy(title = "Trip").displayTitle("Web video", 1, 3))
    }
}
