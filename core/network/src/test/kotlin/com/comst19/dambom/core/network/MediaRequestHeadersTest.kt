package com.comst19.dambom.core.network

import com.comst19.dambom.core.network.header.decodeMediaHeaders
import com.comst19.dambom.core.network.header.encodeMediaHeaders
import com.comst19.dambom.core.network.header.mediaHeadersForUrl
import com.comst19.dambom.core.network.header.mediaRequestClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRequestHeadersTest {
    @Test
    fun `header persistence filters transport fields and line injection`() {
        val headers = mapOf("Cookie" to "session=test", "Range" to "bytes=0-", "Origin" to "bad\r\nvalue")
        val encoded = encodeMediaHeaders(headers)
        assertEquals(mapOf("cookie" to "session=test"), decodeMediaHeaders(encoded))
        val redirected = mediaHeadersForUrl(decodeMediaHeaders(encoded), "https://a.example", "https://b.example")
        assertEquals(emptyMap<String, String>(), redirected)
    }

    @Test
    fun `redirect keeps user agent but strips credentials across origins`() {
        MockWebServer().use { first ->
            MockWebServer().use { second ->
                first.start()
                second.start()
                first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", second.url("/video")))
                second.enqueue(MockResponse().setBody("video"))
                val url = first.url("/video").toString()
                val client =
                    mediaRequestClient(
                        OkHttpClient(),
                        url,
                        mapOf(
                            "Cookie" to "session=test",
                            "Authorization" to "Bearer test",
                            "User-Agent" to "TestAgent",
                        ),
                    )
                client.newCall(Request.Builder().url(url).build()).execute().close()
                assertEquals("session=test", first.takeRequest().getHeader("Cookie"))
                val redirected = second.takeRequest()
                assertNull(redirected.getHeader("Cookie"))
                assertNull(redirected.getHeader("Authorization"))
                assertEquals("TestAgent", redirected.getHeader("User-Agent"))
            }
        }
    }
}
