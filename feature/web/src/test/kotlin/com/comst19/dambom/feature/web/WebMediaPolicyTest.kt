package com.comst19.dambom.feature.web

import android.webkit.WebViewClient
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.feature.web.contract.WebDetectionState
import org.junit.Assert.assertEquals
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

    @Test
    fun `main frame connection error replaces the native WebView error page`() {
        assertEquals(
            WebNavigationFailure.CONNECTION,
            classifyWebNavigationFailure(isForMainFrame = true, WebViewClient.ERROR_CONNECT),
        )
        assertEquals(
            null,
            classifyWebNavigationFailure(isForMainFrame = false, WebViewClient.ERROR_CONNECT),
        )
    }

    @Test
    fun `playback hint is shown only after media detection fails`() {
        assertTrue(WebDetectionState.NotFound(UnsupportedReason.NO_MEDIA).shouldShowPlaybackHint())
        assertTrue(WebDetectionState.NotFound(UnsupportedReason.UNSUPPORTED_FORMAT).shouldShowPlaybackHint())
        assertFalse(WebDetectionState.Idle.shouldShowPlaybackHint())
        assertFalse(WebDetectionState.Scanning.shouldShowPlaybackHint())
        assertFalse(WebDetectionState.Found(1).shouldShowPlaybackHint())
        assertFalse(WebDetectionState.NotFound(UnsupportedReason.NETWORK_ERROR).shouldShowPlaybackHint())
    }
}
