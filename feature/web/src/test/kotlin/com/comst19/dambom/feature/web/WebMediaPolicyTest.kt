package com.comst19.dambom.feature.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMediaPolicyTest {
    @Test
    fun `document start guard allows video frames and unsupported WebViews keep blocking`() {
        val videoUrl = "https://videos.pexels.com/video-files/7253690/sample.mp4"

        assertFalse(shouldBlockWebVideo(videoUrl, mediaGuardInstalled = true))
        assertTrue(shouldBlockWebVideo(videoUrl, mediaGuardInstalled = false))
        assertFalse(
            shouldBlockWebVideo(
                "https://images.pexels.com/videos/7253690/poster.jpg",
                mediaGuardInstalled = false,
            ),
        )
        assertTrue(WEB_MEDIA_GUARD_SCRIPT.contains("IntersectionObserver"))
        assertTrue(WEB_MEDIA_GUARD_SCRIPT.contains("HTMLMediaElement.prototype.play"))
    }
}
