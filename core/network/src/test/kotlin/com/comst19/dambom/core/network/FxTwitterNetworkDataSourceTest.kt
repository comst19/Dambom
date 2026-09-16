package com.comst19.dambom.core.network

import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkDataSource
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkFailure
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FxTwitterNetworkDataSourceTest {
    @Test
    fun `cancelling metadata detection cancels the http call`() =
        runTest {
            val server = MockWebServer().apply { start() }
            try {
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                val client =
                    OkHttpClient
                        .Builder()
                        .addInterceptor { chain ->
                            chain.proceed(
                                chain
                                    .request()
                                    .newBuilder()
                                    .url(server.url("/i/status/123"))
                                    .build(),
                            )
                        }.build()
                val dataSource = FxTwitterNetworkDataSource(client, Json)
                val detection = launch(Dispatchers.IO) { dataSource.detect("https://x.com/test/status/123") }
                server.takeRequest()

                detection.cancelAndJoin()

                assertTrue(detection.isCancelled)
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun `metadata at limit remains readable`() =
        runTest {
            val body = TrackingBody(VALID_JSON.padEnd(LIMIT), LIMIT.toLong())

            assertEquals(FxTwitterNetworkResult.Unsupported(FxTwitterNetworkFailure.NO_MEDIA), detect(body))
            assertTrue(body.closed)
        }

    @Test
    fun `oversized metadata is rejected regardless of declared length`() =
        runTest {
            for (declaredLength in listOf(-1L, 1L, LIMIT.toLong() + 1)) {
                val body = TrackingBody(VALID_JSON.padEnd(LIMIT * 2), declaredLength)

                assertEquals(
                    FxTwitterNetworkResult.Unsupported(FxTwitterNetworkFailure.UNSUPPORTED_FORMAT),
                    detect(body),
                )
                assertTrue(body.closed)
                assertTrue(body.bytesRead < LIMIT * 2)
                if (declaredLength > LIMIT) assertEquals(0L, body.bytesRead)
            }
        }

    private suspend fun detect(body: ResponseBody): FxTwitterNetworkResult? {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body)
                        .build()
                }.build()
        return FxTwitterNetworkDataSource(client, Json).detect("https://x.com/test/status/123")
    }

    private class TrackingBody(
        content: String,
        private val declaredLength: Long,
    ) : ResponseBody() {
        var closed = false
        var bytesRead = 0L
        private val input =
            object : ForwardingSource(Buffer().writeUtf8(content)) {
                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long = super.read(sink, byteCount).also { if (it > 0) bytesRead += it }

                override fun close() {
                    closed = true
                    super.close()
                }
            }.buffer()

        override fun contentType() = "application/json".toMediaType()

        override fun contentLength() = declaredLength

        override fun source() = input
    }

    private companion object {
        const val LIMIT = 1024 * 1024
        const val VALID_JSON = "{\"code\":200,\"message\":\"OK\"}"
    }
}
