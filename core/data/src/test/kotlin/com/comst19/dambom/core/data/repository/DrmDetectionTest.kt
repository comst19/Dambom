package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.common.net.isUnsupportedVideoService
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
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
class DrmDetectionTest {
    @Test
    fun `commas inside a key URI cannot change the encryption method or format`() {
        val key = "URI=\"https://example.com/key,METHOD=SAMPLE-AES,KEYFORMAT=other\",METHOD=AES-128"
        val result = detectStreamManifest(URL, HLS, vod(key).toResponseBody())
        assertTrue(result is MediaDetectionResult.Success)
    }

    @Test
    fun `mixed HTML excludes DRM while preserving clear downloads and the exclusion notice`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                val html = "<video src='/clear.mp4'></video><video src='/protected.m3u8'></video>"
                server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(html))
                server.enqueue(MockResponse().setBody(vod("METHOD=SAMPLE-AES,URI=\"skd://asset\"")))
                val result = repository().detect(server.url("/page").toString()) as MediaDetectionResult.Success
                assertTrue(result.hasProtectedMedia)
                assertEquals(server.url("/clear.mp4").toString(), result.candidates.single().url)
                assertEquals(2, server.requestCount)
            }
        }

    @Test
    fun `extensionless protected manifest is excluded before download`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("<video src='/stream'></video>"),
                )
                server.enqueue(MockResponse().setBody(vod("METHOD=SAMPLE-AES,URI=\"skd://asset\"")))
                val result = repository().detect(server.url("/page").toString())
                assertEquals(MediaDetectionResult.Unsupported(UnsupportedReason.DRM_PROTECTED), result)
            }
        }

    @Test
    fun `oversized master is bounded before any child request`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                val variants = (1..17).joinToString("\n") { "#EXT-X-STREAM-INF:BANDWIDTH=${it * 100000}\n$it.m3u8" }
                server.enqueue(MockResponse().setBody("#EXTM3U\n$variants\n"))
                val result = repository().detect(server.url("/master.m3u8").toString())
                assertEquals(MediaDetectionResult.Unsupported(UnsupportedReason.UNSUPPORTED_FORMAT), result)
                assertEquals(1, server.requestCount)
            }
        }

    @Test
    fun `sample encryption and FairPlay are DRM while identity AES128 remains supported`() {
        for (key in listOf(
            "METHOD=SAMPLE-AES,URI=\"skd://asset\",KEYFORMAT=\"com.apple.streamingkeydelivery\"",
            "METHOD=SAMPLE-AES-CTR,URI=\"data:text/plain;base64,AAAA\"",
            "METHOD=AES-128,URI=\"key\",KEYFORMAT=\"com.apple.streamingkeydelivery\"",
        )) {
            assertEquals(
                MediaDetectionResult.Unsupported(UnsupportedReason.DRM_PROTECTED),
                detectStreamManifest(URL, HLS, vod(key).toResponseBody()),
            )
        }
        val aes128 = detectStreamManifest(URL, HLS, vod("METHOD=AES-128,URI=\"key\"").toResponseBody())
        assertTrue(aes128 is MediaDetectionResult.Success)
        assertTrue(detectStreamManifest(URL, HLS, vod("METHOD=NONE").toResponseBody()) is MediaDetectionResult.Success)
    }

    @Test
    fun `session key is rejected before any media playlist or key request`() {
        val text =
            "#EXTM3U\n#EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI=\"skd://asset\"\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=100000,RESOLUTION=320x180\nvideo.m3u8\n"
        assertEquals(
            MediaDetectionResult.Unsupported(UnsupportedReason.DRM_PROTECTED),
            detectStreamManifest(URL, HLS, text.toResponseBody()),
        )
    }

    @Test
    fun `DASH content protection has a dedicated reason and clear DASH still succeeds`() {
        val protected = "<ContentProtection schemeIdUri=\"urn:mpeg:dash:mp4protection:2011\" value=\"cenc\"/>"
        for (protection in listOf(protected, "")) {
            val manifest =
                """
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT2S" minBufferTime="PT1S">
                <Period duration="PT2S"><AdaptationSet mimeType="video/mp4">$protection
                <Representation id="v" bandwidth="100000" width="320" height="180" codecs="avc1.42c01e">
                <BaseURL>video.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>
                """.trimIndent()
            val result =
                detectStreamManifest(
                    "https://example.com/video.mpd",
                    "application/dash+xml",
                    manifest.toResponseBody(),
                )
            if (protection.isEmpty()) {
                assertTrue(result is MediaDetectionResult.Success)
            } else {
                assertEquals(MediaDetectionResult.Unsupported(UnsupportedReason.DRM_PROTECTED), result)
            }
        }
    }

    @Test
    fun `master checks child audio protection before offering download without fetching segments`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse().setBody(
                        """
                        #EXTM3U
                        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="Audio",URI="audio.m3u8"
                        #EXT-X-STREAM-INF:BANDWIDTH=100000,RESOLUTION=320x180,AUDIO="a"
                        video.m3u8
                        """.trimIndent(),
                    ),
                )
                server.enqueue(MockResponse().setBody(vod("METHOD=NONE")))
                server.enqueue(MockResponse().setBody(vod("METHOD=SAMPLE-AES,URI=\"skd://asset\"")))
                val result = repository().detect(server.url("/master.m3u8").toString())
                assertEquals(MediaDetectionResult.Unsupported(UnsupportedReason.DRM_PROTECTED), result)
                assertEquals(3, server.requestCount)
                assertEquals("/master.m3u8", server.takeRequest().path)
                assertEquals("/video.m3u8", server.takeRequest().path)
                assertEquals("/audio.m3u8", server.takeRequest().path)
            }
        }

    @Test
    fun `html manifest candidate is checked before it becomes a download result`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("<video src='/protected.m3u8'></video>"),
                )
                server.enqueue(MockResponse().setBody(vod("METHOD=SAMPLE-AES,URI=\"skd://asset\"")))
                assertEquals(
                    MediaDetectionResult.Unsupported(UnsupportedReason.DRM_PROTECTED),
                    repository().detect(server.url("/page").toString()),
                )
                assertEquals(2, server.requestCount)
            }
        }

    @Test
    fun `YouTube service is unsupported before network access and lookalike host is not blocked`() =
        runTest {
            val repository = repository()
            val hosts =
                listOf(
                    "www.youtube.com",
                    "m.youtube.com",
                    "youtu.be",
                    "www.youtube-nocookie.com",
                    "youtube.com.",
                )
            for (host in hosts) {
                assertEquals(
                    MediaDetectionResult.Unsupported(UnsupportedReason.UNSUPPORTED_SERVICE),
                    repository.detect("https://$host/watch?v=123"),
                )
            }
            assertEquals(false, "https://youtube.com.example.org/".isUnsupportedVideoService())
        }
}

private fun repository(): DefaultMediaDetectionRepository {
    val client = OkHttpClient()
    return DefaultMediaDetectionRepository(client, FxTwitterNetworkDataSource(client, Json), Dispatchers.IO)
}

private fun vod(key: String): String =
    """
    #EXTM3U
    #EXT-X-TARGETDURATION:2
    #EXT-X-KEY:$key
    #EXTINF:2,
    segment.ts
    #EXT-X-ENDLIST
    """.trimIndent()

private const val URL = "https://example.com/video.m3u8"
private const val HLS = "application/x-mpegURL"
