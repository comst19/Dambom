package com.comst19.dambom.feature.detection

import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.core.domain.model.SharedLinkQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedDownloadQualityTest {
    private val candidate =
        MediaCandidate(
            "id",
            "primary",
            "Video",
            "video/mp4",
            null,
            variants =
                listOf(
                    variant("1920×1080 · 4000 kbps"),
                    variant("640×360 · 500 kbps"),
                    variant("1280×720 · 2000 kbps"),
                ),
        )

    @Test
    fun `highest and lowest use dimensions rather than variant ordering`() {
        assertEquals("1920×1080 · 4000 kbps", candidate.preferredSharedVariant(AppSettings()).quality)
        assertEquals(
            "640×360 · 500 kbps",
            candidate.preferredSharedVariant(AppSettings(sharedLinkQuality = SharedLinkQuality.LOWEST)).quality,
        )
    }

    @Test
    fun `missing preferred resolution uses highest below target`() {
        assertEquals("1280×720 · 2000 kbps", candidate.preferredSharedVariant(preferred(900)).quality)
    }

    @Test
    fun `preferred target below all variants uses lowest`() {
        assertEquals("640×360 · 500 kbps", candidate.preferredSharedVariant(preferred(240)).quality)
    }

    @Test
    fun `portrait video uses the shorter dimension for resolution`() {
        val portrait = candidate.copy(variants = listOf(variant("720×1280"), variant("1080×1920")))
        assertEquals("720×1280", portrait.preferredSharedVariant(preferred(720)).quality)
    }

    @Test
    fun `unknown resolution uses bitrate then size deterministically`() {
        val unknown = candidate.copy(variants = listOf(variant("MP4 · 300 kbps"), variant("MP4 · 1000 kbps")))
        assertEquals("MP4 · 1000 kbps", unknown.preferredSharedVariant(AppSettings()).quality)
        assertEquals("MP4 · 300 kbps", unknown.preferredSharedVariant(preferred(720)).quality)
    }

    @Test
    fun `single original video remains downloadable`() {
        val single = candidate.copy(variants = emptyList())
        assertEquals(single.url, single.preferredSharedVariant(preferred(1080)).url)
    }

    @Test
    fun `oversized dimensions are ignored instead of crashing automatic selection`() {
        val malformed = candidate.copy(variants = listOf(variant("999999999999x720"), variant("360p")))
        assertEquals("360p", malformed.preferredSharedVariant(AppSettings()).quality)
    }

    private fun preferred(resolution: Int) =
        AppSettings(sharedLinkQuality = SharedLinkQuality.PREFERRED_RESOLUTION, sharedLinkResolution = resolution)

    private fun variant(quality: String) = MediaVariant(quality, "video/mp4", null, quality)
}
