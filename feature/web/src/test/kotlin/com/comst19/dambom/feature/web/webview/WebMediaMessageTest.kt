package com.comst19.dambom.feature.web.webview

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebMediaMessageTest {
    @Test
    fun `protection messages require a boolean and still reject a stale page`() {
        val protected = JSONObject(message()).put("protected", true).toString()
        assertEquals(true, parseWebMediaMessage(protected, PAGE_URL, true)?.isProtected)
        assertNull(parseWebMediaMessage(protected, "https://example.com/other", true))
        val nonBoolean = JSONObject(message()).put("protected", "true").toString()
        assertEquals(false, parseWebMediaMessage(nonBoolean, PAGE_URL, true)?.isProtected)
    }

    @Test
    fun `valid metadata preserves the media URL and bounds the title`() {
        val request = parseWebMediaMessage(message(title = "x".repeat(600)), PAGE_URL, true)
        assertEquals(MEDIA_URL, request?.url)
        assertEquals(POSTER_URL, request?.thumbnailUrl)
        assertEquals("x".repeat(512), request?.title)
    }

    @Test
    fun `stale main frame metadata is ignored while fragments remain on the same page`() {
        assertNull(parseWebMediaMessage(message(), "https://example.com/other", true))
        assertEquals(MEDIA_URL, parseWebMediaMessage(message(), "$PAGE_URL#player", true)?.url)
    }

    @Test
    fun `invalid posters do not discard a valid media URL`() {
        val invalidPosters =
            listOf("javascript:alert(1)", "file:///image.jpg", "https://[", "https://user:pass@example.com/image.jpg")
        for (poster in invalidPosters) {
            val request = parseWebMediaMessage(message(poster = poster), PAGE_URL, true)
            assertEquals(MEDIA_URL, request?.url)
            assertNull(request?.thumbnailUrl)
        }
    }

    @Test
    fun `malformed oversized and non string inputs cannot create a request`() {
        assertNull(parseWebMediaMessage(null, PAGE_URL, true))
        assertNull(parseWebMediaMessage("not json", PAGE_URL, true))
        assertNull(parseWebMediaMessage(message(title = "x".repeat(16_384)), PAGE_URL, true))
        for (url in listOf(
            "file:///video.mp4",
            "blob:https://example.com/video",
            "https://user:pass@example.com/video.mp4",
            "https://example.com/" + "x".repeat(8_192),
        )) {
            assertNull(parseWebMediaMessage(message(url = url), PAGE_URL, true))
        }
        val nonStringUrl = JSONObject(message()).put("url", 42).toString()
        assertNull(parseWebMediaMessage(nonStringUrl, PAGE_URL, true))
        val nonStringMetadata = JSONObject(message()).put("poster", 42).put("title", JSONObject.NULL).toString()
        val request = parseWebMediaMessage(nonStringMetadata, PAGE_URL, true)
        assertEquals(MEDIA_URL, request?.url)
        assertNull(request?.title)
        assertNull(request?.thumbnailUrl)
    }
}

private fun message(
    url: String = MEDIA_URL,
    poster: String = POSTER_URL,
    title: String = "Video title",
): String =
    JSONObject()
        .put("url", url)
        .put("poster", poster)
        .put("title", title)
        .put("pageUrl", PAGE_URL)
        .toString()

private const val PAGE_URL = "https://example.com/page"
private const val MEDIA_URL = "https://example.com/video.mp4"
private const val POSTER_URL = "https://example.com/poster.jpg"
