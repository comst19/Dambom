package com.comst19.dambom.feature.library.compress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionProfileTest {
    @Test
    fun `landscape and portrait preserve aspect and never upscale`() {
        val landscape = source(1920, 1080).settings(CompressionProfile.BALANCED)
        val portrait = source(1080, 1920).settings(CompressionProfile.BALANCED)
        assertEquals(1280, landscape.width)
        assertEquals(720, landscape.height)
        assertEquals(720, portrait.width)
        assertEquals(1280, portrait.height)
        val small = source(640, 360).settings(CompressionProfile.QUALITY)
        assertEquals(640, small.width)
        assertEquals(360, small.height)
    }

    @Test
    fun `odd dimensions are encoder aligned without increasing either edge`() {
        val settings = source(853, 479).settings(CompressionProfile.QUALITY)
        assertEquals(852, settings.width)
        assertEquals(478, settings.height)
    }

    @Test
    fun `low bitrate input is reduced rather than raised to a fixed preset`() {
        val video = source(640, 360).copy(bitrate = 400_000)
        val small = video.settings(CompressionProfile.SMALL)
        val balanced = video.settings(CompressionProfile.BALANCED)
        val quality = video.settings(CompressionProfile.QUALITY)
        assertTrue(small.videoBitrate < balanced.videoBitrate)
        assertTrue(balanced.videoBitrate < quality.videoBitrate)
        assertTrue(quality.videoBitrate < video.bitrate)
        assertTrue(small.estimatedBytes < balanced.estimatedBytes)
    }

    @Test
    fun `long high bitrate videos do not overflow the estimate`() {
        val settings =
            source(3840, 2160)
                .copy(durationMillis = 7_200_000L, bitrate = Int.MAX_VALUE)
                .settings(CompressionProfile.QUALITY)
        assertTrue(settings.estimatedBytes > Int.MAX_VALUE)
        assertEquals(1920, settings.width)
        assertEquals(1080, settings.height)
    }

    private fun source(
        width: Int,
        height: Int,
    ) = CompressionSource(width, height, 10_000L, 10_000_000L, 8_000_000)
}
