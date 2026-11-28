package com.comst19.dambom.core.data.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(UnstableApi::class)
class OkHttpMediaPreviewDataSourceFactoryTest {
    @Test
    fun `cross origin redirect strips cookie and authorization while keeping user agent`() {
        MockWebServer().use { first ->
            MockWebServer().use { second ->
                first.start()
                second.start()
                first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", second.url("/video.mp4")))
                second.enqueue(MockResponse().setBody("video"))

                val baseClient = OkHttpClient()
                val factory = OkHttpMediaPreviewDataSourceFactory(baseClient)
                val initialUrl = first.url("/video.mp4").toString()
                val dataSource =
                    factory
                        .create(
                            mediaUrl = initialUrl,
                            requestHeaders =
                                mapOf(
                                    "Cookie" to "session=secret",
                                    "Authorization" to "Bearer secret",
                                    "User-Agent" to "PreviewAgent",
                                ),
                        ).createDataSource()

                try {
                    dataSource.open(DataSpec(Uri.parse(initialUrl)))
                } finally {
                    dataSource.close()
                }

                val firstRequest = first.takeRequest()
                assertEquals("session=secret", firstRequest.getHeader("Cookie"))
                assertEquals("Bearer secret", firstRequest.getHeader("Authorization"))
                assertEquals("PreviewAgent", firstRequest.getHeader("User-Agent"))

                val redirectedRequest = second.takeRequest()
                assertNull(redirectedRequest.getHeader("Cookie"))
                assertNull(redirectedRequest.getHeader("Authorization"))
                assertEquals("PreviewAgent", redirectedRequest.getHeader("User-Agent"))
                assertTrue(baseClient.networkInterceptors.isEmpty())
            }
        }
    }

    @Test
    fun `same origin child hls playlist inherits required query from master playlist`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("#EXTM3U"))

            val baseClient =
                OkHttpClient
                    .Builder()
                    .dns { listOf(InetAddress.getByName("127.0.0.1")) }
                    .build()
            val factory = OkHttpMediaPreviewDataSourceFactory(baseClient)
            val masterUrl = "http://vod.pstatic.net:${server.port}/master.m3u8?token=a%2Fb&key=1"
            val childUrl = "http://vod.pstatic.net:${server.port}/720/playlist.m3u8"
            val dataSource = factory.create(masterUrl, emptyMap()).createDataSource()

            try {
                dataSource.open(DataSpec(Uri.parse(childUrl)))
            } finally {
                dataSource.close()
            }

            assertEquals("/720/playlist.m3u8?token=a%2Fb&key=1", server.takeRequest().path)
        }
    }

    @Test
    fun `candidate headers stay isolated on derived clients without leaking across candidates`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("first"))
            server.enqueue(MockResponse().setBody("second"))

            val baseClient = OkHttpClient()
            val factory = OkHttpMediaPreviewDataSourceFactory(baseClient)
            val firstUrl = server.url("/first.mp4").toString()
            val secondUrl = server.url("/second.mp4").toString()

            val firstSource =
                factory
                    .create(firstUrl, mapOf("Cookie" to "first=1", "User-Agent" to "FirstAgent"))
                    .createDataSource()
            try {
                firstSource.open(DataSpec(Uri.parse(firstUrl)))
            } finally {
                firstSource.close()
            }

            val secondSource = factory.create(secondUrl, emptyMap()).createDataSource()
            try {
                secondSource.open(DataSpec(Uri.parse(secondUrl)))
            } finally {
                secondSource.close()
            }

            val firstRequest = server.takeRequest()
            assertEquals("first=1", firstRequest.getHeader("Cookie"))
            assertEquals("FirstAgent", firstRequest.getHeader("User-Agent"))

            val secondRequest = server.takeRequest()
            assertNull(secondRequest.getHeader("Cookie"))
            assertEquals("Dambom", secondRequest.getHeader("User-Agent"))
        }
    }
}
