package com.comst19.dambom.feature.web

import android.webkit.WebViewClient
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.feature.web.component.shouldShowPlaybackHint
import com.comst19.dambom.feature.web.contract.WebDetectionState
import com.comst19.dambom.feature.web.webview.WEB_MEDIA_GUARD_SCRIPT
import com.comst19.dambom.feature.web.webview.WebNavigationFailure
import com.comst19.dambom.feature.web.webview.classifyHttpNavigationFailure
import com.comst19.dambom.feature.web.webview.classifyWebNavigationFailure
import com.comst19.dambom.feature.web.webview.shouldBlockWebVideo
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
        assertTrue(WEB_MEDIA_GUARD_SCRIPT.contains("const originalPlay"))
        assertTrue(WEB_MEDIA_GUARD_SCRIPT.contains("originalPlay.call(this)"))
        assertTrue(WEB_MEDIA_GUARD_SCRIPT.contains("this.preload = 'none'"))
        assertFalse(WEB_MEDIA_GUARD_SCRIPT.contains("video.currentTime"))
        assertTrue(shouldBlockWebVideo("https://example.com/video.mp4#player", mediaGuardInstalled = false))
        assertFalse(shouldBlockWebVideo("https://example.com/poster.jpg#fake.mp4", mediaGuardInstalled = false))
    }

    @Test
    fun `main frame resource errors map to recovery policy`() {
        assertEquals(
            WebNavigationFailure.CONNECTION,
            classifyWebNavigationFailure(isForMainFrame = true, WebViewClient.ERROR_CONNECT),
        )
        assertEquals(
            WebNavigationFailure.CONNECTION,
            classifyWebNavigationFailure(isForMainFrame = true, WebViewClient.ERROR_HOST_LOOKUP),
        )
        assertEquals(
            WebNavigationFailure.TIMEOUT,
            classifyWebNavigationFailure(isForMainFrame = true, WebViewClient.ERROR_TIMEOUT),
        )
        assertEquals(
            WebNavigationFailure.UNSUPPORTED_URL,
            classifyWebNavigationFailure(isForMainFrame = true, WebViewClient.ERROR_UNSUPPORTED_SCHEME),
        )
        assertEquals(
            WebNavigationFailure.SECURITY,
            classifyWebNavigationFailure(isForMainFrame = true, WebViewClient.ERROR_FAILED_SSL_HANDSHAKE),
        )
        assertEquals(
            WebNavigationFailure.SECURITY,
            classifyWebNavigationFailure(isForMainFrame = true, WebViewClient.ERROR_UNSAFE_RESOURCE),
        )
        assertEquals(
            null,
            classifyWebNavigationFailure(isForMainFrame = false, WebViewClient.ERROR_CONNECT),
        )
        assertTrue(WebNavigationFailure.CONNECTION.retryable)
        assertTrue(WebNavigationFailure.TIMEOUT.retryable)
        assertFalse(WebNavigationFailure.UNSUPPORTED_URL.retryable)
        assertFalse(WebNavigationFailure.SECURITY.retryable)
        assertFalse(WebNavigationFailure.SECURITY.canOpenExternal)
    }

    @Test
    fun `main frame HTTP errors distinguish access timeout and server failures`() {
        assertEquals(
            WebNavigationFailure.HTTP_CLIENT,
            classifyHttpNavigationFailure(isForMainFrame = true, statusCode = 401),
        )
        assertEquals(
            WebNavigationFailure.HTTP_CLIENT,
            classifyHttpNavigationFailure(isForMainFrame = true, statusCode = 403),
        )
        assertEquals(
            WebNavigationFailure.HTTP_CLIENT,
            classifyHttpNavigationFailure(isForMainFrame = true, statusCode = 404),
        )
        assertEquals(
            WebNavigationFailure.HTTP_CLIENT,
            classifyHttpNavigationFailure(isForMainFrame = true, statusCode = 429),
        )
        assertEquals(
            WebNavigationFailure.TIMEOUT,
            classifyHttpNavigationFailure(isForMainFrame = true, statusCode = 408),
        )
        assertEquals(
            WebNavigationFailure.HTTP_SERVER,
            classifyHttpNavigationFailure(isForMainFrame = true, statusCode = 503),
        )
        assertEquals(null, classifyHttpNavigationFailure(isForMainFrame = false, statusCode = 404))
        assertEquals(null, classifyHttpNavigationFailure(isForMainFrame = true, statusCode = 200))
        assertFalse(WebNavigationFailure.HTTP_CLIENT.retryable)
        assertTrue(WebNavigationFailure.HTTP_SERVER.retryable)
    }

    @Test
    fun `playback hint is shown before detection and after media detection fails`() {
        assertTrue(WebDetectionState.NotFound(UnsupportedReason.NO_MEDIA).shouldShowPlaybackHint())
        assertTrue(WebDetectionState.NotFound(UnsupportedReason.UNSUPPORTED_FORMAT).shouldShowPlaybackHint())
        assertTrue(WebDetectionState.Idle.shouldShowPlaybackHint())
        assertFalse(WebDetectionState.Scanning.shouldShowPlaybackHint())
        assertFalse(WebDetectionState.Found(1).shouldShowPlaybackHint())
        assertFalse(WebDetectionState.NotFound(UnsupportedReason.NETWORK_ERROR).shouldShowPlaybackHint())
    }
}
