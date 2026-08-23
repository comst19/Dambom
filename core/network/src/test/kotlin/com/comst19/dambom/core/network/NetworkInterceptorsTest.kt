package com.comst19.dambom.core.network

import com.comst19.dambom.core.network.interceptor.HeaderInterceptor
import com.comst19.dambom.core.network.interceptor.RequestIdInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class NetworkInterceptorsTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `interceptors add default custom and request id headers`() {
        server.enqueue(MockResponse())
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(RequestIdInterceptor())
                .addInterceptor(HeaderInterceptor { mapOf("Authorization" to "Bearer token") })
                .build()

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        val request = server.takeRequest()
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertNotNull(request.getHeader("X-Request-Id"))
    }

    @Test
    fun `request id interceptor preserves existing id`() {
        server.enqueue(MockResponse())
        val client = OkHttpClient.Builder().addInterceptor(RequestIdInterceptor()).build()
        val request =
            Request
                .Builder()
                .url(server.url("/"))
                .header("X-Request-Id", "fixed-id")
                .build()

        client.newCall(request).execute().close()

        assertEquals("fixed-id", server.takeRequest().getHeader("X-Request-Id"))
    }
}
