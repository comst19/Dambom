package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class PlayerConfigDetectionTest {
    @Test(timeout = 5_000)
    fun `many unclosed script tags are ignored without repeated suffix scans`() {
        val html = "<script>".repeat(20_000)
        assertNull(parsePlayerConfig("https://player.vimeo.com/video/123", html))
    }

    @Test
    fun `long embedded JSON string does not exhaust the parser stack`() {
        val title = "a".repeat(20_000)
        val html =
            """
            <script>window.playerConfig = {
              "request":{"files":{"progressive":[{"url":"/stream"}]}},
              "video":{"title":"$title"}
            };</script>
            """.trimIndent()
        val result = parsePlayerConfig("https://player.vimeo.com/video/123", html)!!
        assertEquals(title, result.pageTitle)
        assertEquals("https://player.vimeo.com/stream", result.candidates.single().url)
    }

    @Test
    fun `Vimeo config chooses the default CDN and preserves progressive alternatives`() {
        val config =
            """
            {"request":{"files":{
              "hls":{"default_cdn":"chosen","cdns":{
                "other":{"url":"https://other.example/master.m3u8"},
                "chosen":{"url":"https://media.example/signed?token=a%2Fb"}}},
              "progressive":[{"url":"https://media.example/file","height":720,"quality":"720p"}]
            }},"video":{"title":"Fixture","thumbs":{"base":"//images.example/poster.jpg"}}}
            """.trimIndent()
        val result = parsePlayerConfig(VIMEO_CONFIG_URL, config)!!
        val candidate = result.candidates.single()
        assertEquals("https://media.example/signed?token=a%2Fb", candidate.url)
        assertEquals("application/x-mpegURL", candidate.mimeType)
        assertEquals("Fixture", candidate.title)
        assertEquals("https://images.example/poster.jpg", candidate.thumbnailUrl)
        assertEquals(720, candidate.variants.last().videoHeight)
        assertEquals("video/mp4", candidate.variants.last().mimeType)
    }

    @Test
    fun `embedded config handles quoted braces and trailing JavaScript without executing it`() {
        val html =
            """
            <script>
            window.playerConfig = {
              "request":{"files":{"progressive":[{"url":"/signed-stream","quality":"360p"}]}},
              "video":{"title":"A } brace and \"quote\""}
            }; window.unrelated = {"url":"https://invalid.example/ignore.mp4"};
            </script>
            """.trimIndent()
        val result = parsePlayerConfig("https://player.vimeo.com/video/123?h=fixture", html)!!
        assertEquals("https://player.vimeo.com/signed-stream", result.candidates.single().url)
        assertEquals("A } brace and \"quote\"", result.pageTitle)
    }

    @Test
    fun `invalid default CDN falls back to a valid DASH CDN`() {
        val config =
            """
            {"request":{"files":{"dash":{"default_cdn":"missing","cdns":{
              "bad":{"url":"javascript:alert(1)"},"valid":{"url":"/dash-stream"}
            }}}}}
            """.trimIndent()
        val candidate = parsePlayerConfig(VIMEO_CONFIG_URL, config)!!.candidates.single()
        assertEquals("https://player.vimeo.com/dash-stream", candidate.url)
        assertEquals("application/dash+xml", candidate.mimeType)
    }

    @Test
    fun `tvid extracts HLS flavors and protocol relative thumbnail`() {
        val config =
            """
            {"name":"Fixture","image":"//images.example/poster.jpg","flavors":[
              {"type":"hls","url":"//media.example/signed-stream"},
              {"type":"image","url":"https://images.example/ignored.jpg"},
              {"type":"hls","url":"file:///private/video"}
            ]}
            """.trimIndent()
        val candidate = parsePlayerConfig("https://tvid.in/api/mediainfo/123.json", config)!!.candidates.single()
        assertEquals("https://media.example/signed-stream", candidate.url)
        assertEquals("https://images.example/poster.jpg", candidate.thumbnailUrl)
        assertEquals(1, candidate.variants.size)
    }

    @Test
    fun `unrelated origins malformed config and empty files are ignored`() {
        val config = """{"request":{"files":{"progressive":[{"url":"/stream"}]}}}"""
        assertNull(parsePlayerConfig("https://player.vimeo.com.attacker.example/video/123/config", config))
        assertNull(parsePlayerConfig("https://example.com/video/123/config", config))
        assertNull(parsePlayerConfig(VIMEO_CONFIG_URL, "not-json"))
        assertNull(parsePlayerConfig(VIMEO_CONFIG_URL, """{"request":{"files":{}}}"""))
        assertNull(parsePlayerConfig("https://tvid.in/other", config))
    }

    @Test
    fun `repository uses config extraction and retains scoped request context`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse().setHeader("Content-Type", "application/json").setBody(
                        """{"request":{"files":{"progressive":[{"url":"/signed-stream","quality":"360p"}]}}}""",
                    ),
                )
                val client = OkHttpClient.Builder().dns { listOf(InetAddress.getByName("127.0.0.1")) }.build()
                val repository =
                    DefaultMediaDetectionRepository(
                        client,
                        FxTwitterNetworkDataSource(client, Json),
                        Dispatchers.IO,
                    )
                val url = "http://player.vimeo.com:${server.port}/video/123/config"
                val result =
                    repository.detectRequest(url, mapOf("Cookie" to "fixture=session"))
                        as MediaDetectionResult.Success
                val candidate = result.candidates.single()
                assertEquals("http://player.vimeo.com:${server.port}/signed-stream", candidate.url)
                assertEquals("video/mp4", candidate.mimeType)
                assertEquals("fixture=session", candidate.variants.single().requestHeaders["cookie"])
                assertEquals("fixture=session", server.takeRequest().getHeader("Cookie"))
            }
        }
}

private const val VIMEO_CONFIG_URL = "https://player.vimeo.com/video/123/config"
