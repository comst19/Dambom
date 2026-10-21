package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StreamManifestDetectionTest {
    @Test
    fun `metadata and escaped JSON media URLs are discovered`() {
        val html =
            "<meta property='og:video' content='/stream'>" +
                """<script>{"contentUrl":"https:\/\/cdn.example.com\/master.m3u8"}</script>"""
        val result = parseHtmlMedia("https://example.com/page", html) as MediaDetectionResult.Success
        val expected = setOf("https://example.com/stream", "https://cdn.example.com/master.m3u8")
        assertEquals(expected, result.candidates.map { it.url }.toSet())
    }

    @Test
    fun `extensionless HLS is sniffed and request context retained`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody(VOD))
                val client = OkHttpClient()
                val source = FxTwitterNetworkDataSource(client, Json)
                val repository = DefaultMediaDetectionRepository(client, source, Dispatchers.IO)
                val headers = mapOf("Cookie" to "session=test")
                val result = repository.detectRequest(server.url("/stream").toString(), headers)
                assertTrue(result is MediaDetectionResult.Success)
                val candidate = (result as MediaDetectionResult.Success).candidates.single()
                assertEquals("application/x-mpegURL", candidate.mimeType)
                assertEquals("session=test", candidate.requestHeaders["cookie"])
                assertEquals("session=test", server.takeRequest().getHeader("Cookie"))
            }
        }

    @Test
    fun `live HLS is rejected instead of downloading a partial broadcast`() {
        val mime = "application/x-mpegURL"
        val body = VOD.replace("#EXT-X-ENDLIST", "").toResponseBody()
        assertTrue(detectStreamManifest("https://example.com/live", mime, body) is MediaDetectionResult.Unsupported)
    }

    @Test
    fun `master playlist remains whole to preserve separate audio renditions`() {
        val playlist =
            """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",URI="audio.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=100000,RESOLUTION=320x180,AUDIO="audio"
            video.m3u8
            """.trimIndent()
        val body = playlist.toResponseBody()
        val result =
            detectStreamManifest(
                "https://example.com/master",
                "application/x-mpegURL",
                body,
            ) as MediaDetectionResult.Success
        val candidate = result.candidates.single()
        assertEquals("https://example.com/master", candidate.url)
        assertEquals(180, candidate.downloadVariants.single().videoHeight)
        assertEquals("https://example.com/master#dambom-height=180", candidate.downloadVariants.single().selectionId)
    }

    @Test
    fun `html discovers manifests and extensionless video source`() {
        val result =
            parseHtmlMedia(
                "https://example.com/page",
                "<video src='/stream'></video><source src='/master.m3u8'>",
            ) as MediaDetectionResult.Success
        val expected = setOf("https://example.com/stream", "https://example.com/master.m3u8")
        assertEquals(expected, result.candidates.map { it.url }.toSet())
    }
}

private const val VOD = "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\nsegment.ts\n#EXT-X-ENDLIST\n"
