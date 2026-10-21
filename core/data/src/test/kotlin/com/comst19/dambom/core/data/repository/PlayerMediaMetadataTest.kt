package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.ORIGINAL_QUALITY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerMediaMetadataTest {
    @Test
    fun `common mapping resolves and deduplicates sources while preserving signed queries`() {
        val metadata =
            PlayerMediaMetadata(
                title = "Fixture",
                thumbnailUrl = "//images.example/poster.jpg",
                sources =
                    listOf(
                        PlayerMediaSource("/stream?token=a%2Fb", "video/mp4", "360p", 360),
                        PlayerMediaSource("https://player.example/stream?token=a%2Fb", "video/mp4", "360p", 360),
                    ),
            )
        val candidate = metadata.toDetectionResult("https://player.example/embed")!!.candidates.single()
        assertEquals("https://player.example/stream?token=a%2Fb", candidate.url)
        assertEquals("https://images.example/poster.jpg", candidate.thumbnailUrl)
        assertEquals(1, candidate.variants.size)
        assertEquals(360, candidate.variants.single().videoHeight)
    }

    @Test
    fun `common mapping drops invalid and credential-bearing sources`() {
        val metadata =
            PlayerMediaMetadata(
                title = "Fixture",
                thumbnailUrl = "javascript:alert(1)",
                sources =
                    listOf(
                        PlayerMediaSource("file:///video.mp4", "video/mp4"),
                        PlayerMediaSource("https://user:password@media.example/video.mp4", "video/mp4"),
                        PlayerMediaSource("/valid.mp4", "video/mp4"),
                    ),
            )
        val candidate = metadata.toDetectionResult("https://player.example/embed")!!.candidates.single()
        assertEquals("https://player.example/valid.mp4", candidate.url)
        assertNull(candidate.thumbnailUrl)
        assertEquals(1, candidate.variants.size)
        assertNull(metadata.copy(sources = emptyList()).toDetectionResult("https://player.example/embed"))
    }

    @Test
    fun `common mapping supplies title and quality defaults and removes invalid height`() {
        val metadata =
            PlayerMediaMetadata(
                title = " ",
                thumbnailUrl = null,
                sources = listOf(PlayerMediaSource("/fixture.mp4", "video/mp4", " ", -1)),
            )
        val result = metadata.toDetectionResult("https://player.example/embed")!!
        assertEquals("fixture", result.pageTitle)
        assertEquals(
            ORIGINAL_QUALITY,
            result.candidates
                .single()
                .variants
                .single()
                .quality,
        )
        assertNull(
            result.candidates
                .single()
                .variants
                .single()
                .videoHeight,
        )
    }
}
