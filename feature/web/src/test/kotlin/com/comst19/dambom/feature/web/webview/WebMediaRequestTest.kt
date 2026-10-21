package com.comst19.dambom.feature.web.webview

import com.comst19.dambom.core.domain.model.MediaResourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebMediaRequestTest {
    @Test
    fun `dynamic media receives target cookies and browser user agent without inventing a referrer`() {
        val request =
            MediaResourceRequest("https://media.example/stream")
                .withBrowserContext("Fixture browser", "fixture=session")
        assertEquals("Fixture browser", request.headers["User-Agent"])
        assertEquals("fixture=session", request.headers["Cookie"])
        assertFalse(request.headers.keys.any { it.equals("Referer", true) })
    }

    @Test
    fun `captured user agent and referrer survive and cookie casing is deduplicated`() {
        val original =
            MediaResourceRequest(
                "https://media.example/stream",
                mapOf("user-agent" to "Captured", "cookie" to "old", "Referer" to "https://page.example/"),
            )
        val request = original.withBrowserContext("Fallback", "current")
        assertEquals("Captured", request.headers["user-agent"])
        assertEquals("https://page.example/", request.headers["Referer"])
        assertEquals(listOf("Cookie"), request.headers.keys.filter { it.equals("cookie", true) })
        assertEquals("current", request.headers["Cookie"])
        assertEquals("old", original.headers["cookie"])
    }

    @Test
    fun `missing browser cookie preserves captured request cookie`() {
        val request =
            MediaResourceRequest("https://media.example/stream", mapOf("Cookie" to "captured"))
                .withBrowserContext("Fixture browser", null)
        assertEquals("captured", request.headers["Cookie"])
    }
}
