package com.comst19.dambom.feature.library.media

import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalVideoMetadataTest {
    @Test
    fun `cache key changes when a video at the same path is replaced`() {
        assertNotEquals(
            LocalVideoCacheKey(path = "/videos/same.mp4", revision = 1L),
            LocalVideoCacheKey(path = "/videos/same.mp4", revision = 2L),
        )
    }
}
