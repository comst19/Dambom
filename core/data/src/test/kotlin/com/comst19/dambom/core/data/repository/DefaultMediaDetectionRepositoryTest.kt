package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.data.network.fxtwitter.FxTwitterMediaDetector
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultMediaDetectionRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultMediaDetectionRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val rewrittenUrl =
                        if (request.url.host == FXTWITTER_HOST) {
                            server.url(request.url.encodedPath)
                        } else {
                            request.url
                        }
                    chain.proceed(request.newBuilder().url(rewrittenUrl).build())
                }.build()
        repository =
            DefaultMediaDetectionRepository(
                client = client,
                fxTwitterDetector = FxTwitterMediaDetector(client, Json { ignoreUnknownKeys = true }),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `html video sources are resolved and deduplicated`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody("<html><title>여행</title><video src='/media/trip.mp4'></video><source src='/media/trip.mp4'>")
                    .setHeader("Content-Type", "text/html"),
            )

            val result = repository.detect(server.url("/page").toString())

            assertTrue(result is MediaDetectionResult.Success)
            result as MediaDetectionResult.Success
            assertEquals("여행", result.pageTitle)
            assertEquals(server.url("/media/trip.mp4").toString(), result.candidates.single().url)
        }

    @Test
    fun `html video poster is attached to its candidate`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody(
                        "<html><video poster='/images/trip.jpg'><source src='/media/trip.mp4'></video></html>",
                    ).setHeader("Content-Type", "text/html"),
            )

            val result = repository.detect(server.url("/page").toString()) as MediaDetectionResult.Success

            assertEquals(server.url("/images/trip.jpg").toString(), result.candidates.single().thumbnailUrl)
        }

    @Test
    fun `opaque media file name is replaced with page title and position`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody(
                        "<html><title>3,000+개의 최고의 샘플 동영상 · Pexels</title><video src='/media/8b3917b0-21e5-41cb-b724-0bd24bc3b5d1.mp4'></video></html>",
                    ).setHeader("Content-Type", "text/html"),
            )

            val result = repository.detect(server.url("/page").toString()) as MediaDetectionResult.Success

            assertEquals("샘플 동영상 · 1", result.candidates.single().title)
        }

    @Test
    fun `restricted response is not bypassed`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(403))

            val result = repository.detect(server.url("/private").toString())

            assertEquals(MediaDetectionResult.Unsupported(UnsupportedReason.ACCESS_RESTRICTED), result)
        }

    @Test
    fun `x post groups quality variants as one video with its thumbnail`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(X_VIDEO_RESPONSE),
            )

            val result =
                repository.detect(
                    "https://x.com/FloodSocial/status/869318041078820864/video/1",
                )

            assertTrue(result is MediaDetectionResult.Success)
            result as MediaDetectionResult.Success
            assertEquals("API demos (@FloodSocial)", result.pageTitle)
            assertEquals(1, result.candidates.size)
            assertEquals("720×1280 · 2176 kbps", result.candidates.single().quality)
            assertEquals(
                listOf(
                    "720×1280 · 2176 kbps",
                    "360×640 · 832 kbps",
                    "180×320 · 256 kbps",
                ),
                result.candidates
                    .single()
                    .downloadVariants
                    .map { it.quality },
            )
            assertEquals(
                "https://pbs.twimg.com/ext_tw_video_thumb/869317980307415040/pu/img/demo.jpg",
                result.candidates.single().thumbnailUrl,
            )
            assertTrue(result.candidates.all { it.mimeType == "video/mp4" })
            assertEquals("/i/status/869318041078820864", server.takeRequest().path)
        }

    @Test
    fun `private x post is access restricted`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(403))

            val result = repository.detect("https://twitter.com/private/status/123456789")

            assertEquals(MediaDetectionResult.Unsupported(UnsupportedReason.ACCESS_RESTRICTED), result)
            assertEquals("/i/status/123456789", server.takeRequest().path)
        }
}

private const val FXTWITTER_HOST = "api.fxtwitter.com"
private val X_VIDEO_RESPONSE =
    """
    {
      "code": 200,
      "message": "OK",
      "tweet": {
        "id": "869318041078820864",
        "url": "https://x.com/FloodSocial/status/869318041078820864",
        "text": "Public demo video",
        "author": { "name": "API demos", "screen_name": "FloodSocial" },
        "media": {
          "videos": [{
            "id": "video-1",
            "type": "video",
            "url": "https://video.twimg.com/ext_tw_video/869317980307415040/pu/vid/720x1280/high.mp4",
            "width": 720,
            "height": 1280,
            "duration": 10.704,
            "thumbnail_url": "https://pbs.twimg.com/ext_tw_video_thumb/869317980307415040/pu/img/demo.jpg",
            "formats": [
              { "container": "m3u8", "url": "https://video.twimg.com/video.m3u8" },
              { "container": "mp4", "codec": "h264", "bitrate": 256000, "url": "https://video.twimg.com/pu/vid/180x320/low.mp4" },
              { "container": "mp4", "codec": "h264", "bitrate": 832000, "url": "https://video.twimg.com/pu/vid/360x640/medium.mp4" },
              { "container": "mp4", "codec": "h264", "bitrate": 2176000, "url": "https://video.twimg.com/pu/vid/720x1280/high.mp4" },
              { "container": "mp4", "codec": "vp9", "bitrate": 9999000, "url": "https://video.twimg.com/pu/vid/720x1280/not-mp4.webm" }
            ]
          }]
        }
      }
    }
    """.trimIndent()
