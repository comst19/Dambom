package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlMediaParserTest {
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
