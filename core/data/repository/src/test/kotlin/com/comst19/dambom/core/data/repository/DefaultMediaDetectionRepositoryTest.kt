package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import kotlinx.coroutines.test.runTest
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
        repository = DefaultMediaDetectionRepository(OkHttpClient())
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
    fun `restricted response is not bypassed`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(403))

            val result = repository.detect(server.url("/private").toString())

            assertEquals(MediaDetectionResult.Unsupported(UnsupportedReason.ACCESS_RESTRICTED), result)
        }
}
