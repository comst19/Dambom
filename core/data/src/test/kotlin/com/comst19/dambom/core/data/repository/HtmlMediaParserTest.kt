package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlMediaParserTest {
    @Test
    fun `unclosed video retains its own poster without consuming the next video`() {
        val result =
            parseHtmlMedia(
                "https://example.com/page",
                "<video poster='/first.jpg'><source src='/first.mp4'>" +
                    "<video poster='/second.jpg'><source src='/second.mp4'>",
            ) as MediaDetectionResult.Success
        assertEquals(
            listOf("https://example.com/first.jpg", "https://example.com/second.jpg"),
            result.candidates.map { it.thumbnailUrl },
        )
    }

    @Test(timeout = 5_000)
    fun `many unclosed videos finish without a media candidate`() {
        val result = parseHtmlMedia("https://example.com/page", "<video>".repeat(40_000))
        assertEquals(
            MediaDetectionResult.Unsupported(com.comst19.dambom.core.domain.model.UnsupportedReason.NO_MEDIA),
            result,
        )
    }

    @Test
    fun `nested title markup and entities preserve existing title text`() {
        val result =
            parseHtmlMedia(
                "https://example.com/page",
                "<TITLE>Road <b>&amp;</b>\ntrip</TITLE><video src='/trip.mp4'></video>",
            ) as MediaDetectionResult.Success

        assertEquals("Road &amp;\ntrip", result.pageTitle)
    }

    @Test(timeout = 5_000)
    fun `many malformed source base and title starts finish without media`() {
        listOf("<source ", "<base a ", "<meta a ", "<a href ", "<title>x").forEach { token ->
            val result = parseHtmlMedia("https://example.com/page", token.repeat(35_000))
            assertEquals(
                MediaDetectionResult.Unsupported(com.comst19.dambom.core.domain.model.UnsupportedReason.NO_MEDIA),
                result,
            )
        }
    }

    @Test(timeout = 5_000)
    fun `malformed title markup retains text and source inside video is bounded`() {
        val title = "<a href ".repeat(35_000)
        val result =
            parseHtmlMedia(
                "https://example.com/page",
                "<title>$title</title><video>" + "<source ".repeat(35_000) + "</video>" +
                    "<video src='/clip.mp4'></video>",
            ) as MediaDetectionResult.Success

        assertEquals(title.trim(), result.pageTitle)
        assertEquals("https://example.com/clip.mp4", result.candidates.single().url)
    }

    @Test
    fun `missing page title and opaque filename leave localized fallback to presentation`() {
        val result =
            parseHtmlMedia(
                "https://example.com/page",
                "<video src='/8b3917b0-21e5-41cb-b724-0bd24bc3b5d1.mp4'></video>",
            ) as MediaDetectionResult.Success

        assertEquals("", result.pageTitle)
        assertEquals("", result.candidates.single().title)
    }

    @Test
    fun `base and encoded source are interpreted without a network call`() {
        val result =
            parseHtmlMedia(
                "https://example.com/page",
                "<title>Trip</title><base href='/media/'><video src='trip.mp4?a=1&amp;b=2'></video>",
            ) as MediaDetectionResult.Success

        assertEquals("Trip", result.pageTitle)
        assertEquals("trip", result.candidates.single().title)
        assertEquals("https://example.com/media/trip.mp4?a=1&b=2", result.candidates.single().url)
    }
}
