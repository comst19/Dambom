package com.comst19.dambom.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerMediaItemTest {
    @Test
    fun `same id and URI keeps the current media item`() {
        val uri = "file:///files/old.mp4"

        assertFalse(shouldReplaceMediaItem("video", uri, "video", uri))
    }

    @Test
    fun `same id with a new URI replaces the current media item`() {
        assertTrue(
            shouldReplaceMediaItem(
                "video",
                "file:///files/old.mp4",
                "video",
                "file:///files/new.mp4",
            ),
        )
    }

    @Test
    fun `cleared media is replaced when the same path becomes available again`() {
        val uri = "file:///files/video.mp4"

        assertTrue(shouldReplaceMediaItem(null, null, "video", uri))
    }
}
