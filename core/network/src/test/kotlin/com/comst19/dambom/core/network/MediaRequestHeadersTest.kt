package com.comst19.dambom.core.network

import com.comst19.dambom.core.network.header.decodeMediaHeaders
import com.comst19.dambom.core.network.header.encodeMediaHeaders
import com.comst19.dambom.core.network.header.inheritPlaylistQuery
import com.comst19.dambom.core.network.header.mediaHeadersForUrl
import com.comst19.dambom.core.network.header.mediaRequestClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class MediaRequestHeadersTest {
    @Test
    fun `public media download identifies the app instead of the HTTP library`() {
        MockWebServer().use { server ->
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val agent = request.getHeader("User-Agent").orEmpty()
                        return if (agent.startsWith("okhttp/")) {
                            MockResponse().setResponseCode(403)
                        } else {
                            MockResponse().setHeader("Content-Type", "video/mp4").setBody("video")
                        }
                    }
                }
            server.start()
            val url = server.url("/public-download").toString()
            val client = mediaRequestClient(OkHttpClient(), url, emptyMap())
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                assertEquals(200, response.code)
            }
            assertEquals("Dambom", server.takeRequest().getHeader("User-Agent"))
        }
    }

    @Test
    fun `Naver and Daum child playlists inherit the exact encoded query`() {
        listOf("vod.pstatic.net", "vod.daum.net", "sample-naver-vod.akamaized.net").forEach { host ->
            val source = "https://$host/master.m3u8?token=a%2Fb&key=1&key=2".toHttpUrl()
            val child = "https://$host/720/playlist.m3u8".toHttpUrl()
            assertEquals("token=a%2Fb&key=1&key=2", inheritPlaylistQuery(source, child).encodedQuery)
        }
    }

    @Test
    fun `playlist query never replaces child query or leaks to another origin or segment`() {
        val source = "https://vod.pstatic.net/master.m3u8?token=secret".toHttpUrl()
        listOf(
            "https://vod.pstatic.net/720.m3u8?token=child",
            "https://other.pstatic.net/720.m3u8",
            "http://vod.pstatic.net/720.m3u8",
            "https://vod.pstatic.net:8443/720.m3u8",
            "https://vod.pstatic.net/segment.ts",
            "https://vod.pstatic.net/key.bin",
        ).forEach { value ->
            val target = value.toHttpUrl()
            assertEquals(target, inheritPlaylistQuery(source, target))
        }
        val ordinary = "https://example.org/master.m3u8?token=secret".toHttpUrl()
        val child = "https://example.org/720.m3u8".toHttpUrl()
        assertEquals(child, inheritPlaylistQuery(ordinary, child))
    }

    @Test
    fun `network client requests child playlist with inherited query`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("#EXTM3U"))
            val client = OkHttpClient.Builder().dns { listOf(InetAddress.getByName("127.0.0.1")) }.build()
            val origin = "http://vod.pstatic.net:${server.port}/master.m3u8?token=a%2Fb"
            val target = "http://vod.pstatic.net:${server.port}/720.m3u8"
            mediaRequestClient(client, origin, emptyMap())
                .newCall(Request.Builder().url(target).build())
                .execute()
                .close()
            assertEquals("/720.m3u8?token=a%2Fb", server.takeRequest().path)
        }
    }

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
