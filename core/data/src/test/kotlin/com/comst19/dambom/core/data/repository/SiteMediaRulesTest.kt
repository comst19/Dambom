package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class SiteMediaRulesTest {
    @Test
    fun `Facebook rewrite preserves encoded path query and fragment and excludes lookalike hosts`() {
        assertEquals(
            "https://m.facebook.com/watch/?v=12&token=a%2Fb#video",
            siteDetectionUrl("https://www.facebook.com/watch/?v=12&token=a%2Fb#video"),
        )
        listOf("m.facebook.com", "facebook.com.example.org", "example.org").forEach { host ->
            val url = "https://$host/watch/?v=12"
            assertEquals(url, siteDetectionUrl(url))
        }
    }

    @Test
    fun `Vimeo config request retains privacy hash and never repeats config path`() {
        assertEquals(
            listOf("https://player.vimeo.com/video/123/config?h=a%2Fb&autoplay=0"),
            playerFollowUpUrls("https://player.vimeo.com/video/123/?h=a%2Fb&autoplay=0", ""),
        )
        assertTrue(playerFollowUpUrls("https://player.vimeo.com/video/123/config", "{}").isEmpty())
    }

    @Test
    fun `embed discovery honors base and entities but rejects data src credentials and lookalikes`() {
        val html =
            """
            <base href='https://player.vimeo.com/'>
            <iframe src='/video/123?h=a&amp;b=c'></iframe>
            <iframe src='/video/123?h=a&amp;b=c'></iframe>
            <iframe data-src='/video/456'></iframe>
            <iframe src='https://player.vimeo.com.evil.org/video/789'></iframe>
            <iframe src='https://user:password@player.vimeo.com/video/789'></iframe>
            <iframe src='javascript:alert(1)'></iframe>
            <iframe src='https://videos.sproutvideo.com/embed/abc123/def456'></iframe>
            """.trimIndent()
        assertEquals(
            listOf("https://player.vimeo.com/video/123?h=a&b=c", "https://videos.sproutvideo.com/embed/abc123/def456"),
            playerFollowUpUrls("https://example.org/watch", html),
        )
    }

    @Test
    fun `embed discovery has a bounded request list`() {
        val html = (1..20).joinToString("") { "<iframe src='https://player.vimeo.com/video/$it'></iframe>" }
        assertEquals(4, playerFollowUpUrls("https://example.org/watch", html).size)
    }

    @Test
    fun `Sprout reads offered downloads without inventing signatures or unrelated video links`() {
        val html =
            """
            <title>Public &amp; Demo</title>
            <a href='https://sproutvideo.com/videos/abc123/player_download?type=sd&amp;signature=a%2Fb'>SD</a>
            <a href='https://sproutvideo.com/videos/abc123/player_download?type=hd&amp;signature=c%2Bd'>HD</a>
            <a href='https://sproutvideo.com/videos/other/player_download?type=hd'>Other</a>
            <a href='https://sproutvideo.com.evil.org/videos/abc123/player_download?type=hd'>Bad</a>
            """.trimIndent()
        val candidate = parseSproutDownloads(SPROUT_URL, html)!!.candidates.single()
        assertEquals("Public & Demo", candidate.title)
        assertEquals(listOf("SD", "HD"), candidate.variants.map { it.quality })
        assertEquals("https://sproutvideo.com/videos/abc123/player_download?type=sd&signature=a%2Fb", candidate.url)
        assertNull(parseSproutDownloads("https://example.org/embed/abc123/def456", html))
        assertNull(parseSproutDownloads(SPROUT_URL, "<video></video>"))
    }

    @Test
    fun `repository follows embed then config preserving same origin headers only`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                val embeds =
                    (123..126).joinToString("") {
                        "<iframe src='http://player.vimeo.com:${server.port}/video/$it?h=hash'></iframe>"
                    }
                server.enqueue(html(embeds))
                server.enqueue(html("<html>No inline config</html>"))
                server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(CONFIG))
                val result =
                    repository().detectRequest(
                        "http://example.org:${server.port}/watch",
                        mapOf(
                            "Cookie" to "private=session",
                            "Authorization" to "Bearer private",
                            "Referer" to "https://example.org/",
                        ),
                    ) as MediaDetectionResult.Success
                assertEquals("http://player.vimeo.com:${server.port}/stream.mp4", result.candidates.single().url)
                assertEquals("private=session", server.takeRequest().getHeader("Cookie"))
                val embed = server.takeRequest()
                val config = server.takeRequest()
                assertNull(embed.getHeader("Cookie"))
                assertNull(config.getHeader("Authorization"))
                assertEquals("https://example.org/", config.getHeader("Referer"))
                assertEquals("/video/123/config?h=hash", config.path)
            }
        }

    @Test
    fun `repository does not request embeds when direct media is already present`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    html(
                        "<video src='/direct.mp4'></video>" +
                            "<iframe src='https://player.vimeo.com/video/123'></iframe>",
                    ),
                )
                assertTrue(repository().detect(server.url("/watch").toString()) is MediaDetectionResult.Success)
                assertEquals(1, server.requestCount)
            }
        }

    @Test
    fun `repository stops after four extra requests and skips restricted embeds`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                val embeds =
                    (1..20).joinToString("") {
                        "<iframe src='http://player.vimeo.com:${server.port}/video/$it'></iframe>"
                    }
                server.enqueue(html(embeds))
                repeat(4) { server.enqueue(MockResponse().setResponseCode(403)) }
                assertEquals(
                    MediaDetectionResult.Unsupported(UnsupportedReason.NO_MEDIA),
                    repository().detect(server.url("/watch").toString()),
                )
                assertEquals(5, server.requestCount)
            }
        }

    @Test
    fun `repository extracts Sprout offered downloads from embed HTML`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    html(
                        "<a href='https://sproutvideo.com/videos/abc123/player_download" +
                            "?type=hd&amp;signature=fixture'>HD</a>",
                    ),
                )
                val result =
                    repository().detect("http://videos.sproutvideo.com:${server.port}/embed/abc123/def456")
                        as MediaDetectionResult.Success
                assertEquals(
                    "HD",
                    result.candidates
                        .single()
                        .variants
                        .single()
                        .quality,
                )
            }
        }

    @Test
    fun `Facebook shared URL is rewritten before HTTP detection`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(html("<video src='/clip.mp4'></video>"))
                val result =
                    repository().detect("http://www.facebook.com:${server.port}/watch?v=123")
                        as MediaDetectionResult.Success
                assertEquals("http://m.facebook.com:${server.port}/clip.mp4", result.candidates.single().url)
                assertEquals("m.facebook.com:${server.port}", server.takeRequest().getHeader("Host"))
            }
        }

    @Test
    fun `cancelling during player follow up cancels detection`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(html("<iframe src='http://player.vimeo.com:${server.port}/video/123'></iframe>"))
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                val detection = launch(Dispatchers.IO) { repository().detect(server.url("/watch").toString()) }
                server.takeRequest()
                server.takeRequest()
                detection.cancelAndJoin()
                assertTrue(detection.isCancelled)
            }
        }

    private fun repository(): DefaultMediaDetectionRepository {
        val client = OkHttpClient.Builder().dns { listOf(InetAddress.getByName("127.0.0.1")) }.build()
        return DefaultMediaDetectionRepository(client, FxTwitterNetworkDataSource(client, Json), Dispatchers.IO)
    }

    private fun html(body: String): MockResponse = MockResponse().setHeader("Content-Type", "text/html").setBody(body)
}

private const val SPROUT_URL = "https://videos.sproutvideo.com/embed/abc123/def456"
private const val CONFIG =
    """{"request":{"files":{"progressive":[{"url":"/stream.mp4","quality":"720p"}]}}}"""
