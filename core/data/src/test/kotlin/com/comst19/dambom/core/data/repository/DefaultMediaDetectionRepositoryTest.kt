package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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
                fxTwitterNetworkDataSource = FxTwitterNetworkDataSource(client, Json { ignoreUnknownKeys = true }),
                ioDispatcher = Dispatchers.IO,
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
    fun `html video sources reject non http schemes`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody(
                        "<html><video src='file:///data/local.mp4'></video>" +
                            "<source src='content://media/external/video.mp4'>" +
                            "<source src='data:video/mp4;base64,AAAA.mp4'>" +
                            "<source src='udp://127.0.0.1/video.mp4'></html>",
                    ).setHeader("Content-Type", "text/html"),
            )

            val result = repository.detect(server.url("/page").toString())

            assertEquals(MediaDetectionResult.Unsupported(UnsupportedReason.NO_MEDIA), result)
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
    fun `redirected html resolves base media and entities against the final document url`() =
        runTest {
            val redirectedServer = MockWebServer().apply { start() }
            try {
                server.enqueue(
                    MockResponse()
                        .setResponseCode(302)
                        .setHeader("Location", redirectedServer.url("/pages/watch/index.html")),
                )
                redirectedServer.enqueue(
                    MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody(
                            "<html><base href='../assets/'><video poster='poster.jpg?size=&#49;&amp;fit=cover'>" +
                                "<source src='clip.mp4?token=a&amp;b=&#99;#player'></video></html>",
                        ),
                )

                val result = repository.detect(server.url("/start").toString()) as MediaDetectionResult.Success

                assertEquals(
                    redirectedServer.url("/pages/assets/clip.mp4?token=a&b=c#player").toString(),
                    result.candidates.single().url,
                )
                assertEquals(
                    redirectedServer.url("/pages/assets/poster.jpg?size=1&fit=cover").toString(),
                    result.candidates.single().thumbnailUrl,
                )
            } finally {
                redirectedServer.shutdown()
            }
        }

    @Test
    fun `redirected direct video uses the final response url without changing its signed query`() =
        runTest {
            val finalUrl = server.url("/media/final.mp4?token=a%2Bb&expires=1#player")
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", finalUrl))
            server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("video"))

            val result = repository.detect(server.url("/old/video").toString()) as MediaDetectionResult.Success

            assertEquals(finalUrl.toString(), result.candidates.single().url)
        }

    @Test
    fun `invalid base scheme cannot turn a relative source into a local candidate`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><base href='file:///private/'><video src='file:///private/video.mp4'></video></html>"),
            )

            assertEquals(
                MediaDetectionResult.Unsupported(UnsupportedReason.NO_MEDIA),
                repository.detect(server.url("/page").toString()),
            )
        }

    @Test
    fun `cancelling while waiting for headers cancels detection`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val detection = launch(Dispatchers.IO) { repository.detect(server.url("/slow").toString()) }
            server.takeRequest()

            detection.cancelAndJoin()

            assertTrue(detection.isCancelled)
        }

    @Test
    fun `cancelling while reading body is not mapped to network error`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/html")
                    .setBody("x".repeat(512 * 1024))
                    .throttleBody(1024, 100, java.util.concurrent.TimeUnit.MILLISECONDS),
            )
            val detection = async(Dispatchers.IO) { repository.detect(server.url("/slow-body").toString()) }
            server.takeRequest()
            delay(100)

            detection.cancelAndJoin()

            assertTrue(detection.isCancelled)
        }

    @Test
    fun `video extension checks only the uri path and preserves query and fragment`() =
        runTest {
            val signedVideo = server.url("/video.MP4?token=a%2Bb#player").toString()
            server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody("video"))

            val result = repository.detect(signedVideo) as MediaDetectionResult.Success

            assertEquals(signedVideo, result.candidates.single().url)
        }

    @Test
    fun `direct html video url preserves a fragment after the extension`() =
        runTest {
            val videoUrl = "https://example.com/video.mp4#player"
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><body>$videoUrl</body></html>"),
            )

            val result = repository.detect(server.url("/page").toString()) as MediaDetectionResult.Success

            assertEquals(videoUrl, result.candidates.single().url)
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
    fun `oversized html response is rejected before parsing`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/html")
                    .setBody("x".repeat(2 * 1024 * 1024 + 1)),
            )

            val result = repository.detect(server.url("/large").toString())

            assertEquals(MediaDetectionResult.Unsupported(UnsupportedReason.UNSUPPORTED_FORMAT), result)
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
