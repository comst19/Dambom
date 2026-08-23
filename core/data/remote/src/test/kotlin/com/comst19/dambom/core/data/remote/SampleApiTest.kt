package com.comst19.dambom.core.data.remote

import com.comst19.dambom.core.data.remote.api.SampleApi
import com.comst19.dambom.core.data.remote.datasource.RetrofitSampleRemoteDataSource
import com.comst19.dambom.core.data.remote.error.RemoteDecodingException
import com.comst19.dambom.core.data.remote.error.RemoteNetworkException
import com.comst19.dambom.core.data.remote.error.RemoteNetworkFailureReason
import com.comst19.dambom.core.data.remote.error.RemoteRequestException
import com.comst19.dambom.core.data.remote.model.NetworkSample
import com.comst19.dambom.core.data.remote.model.SampleResponse
import com.comst19.dambom.core.network.calladapter.NetworkCallAdapterFactory
import com.comst19.dambom.core.network.calladapter.NetworkHttpException
import com.comst19.dambom.core.network.model.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SampleApiTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `response is deserialized without external network`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"data":[{"id":1,"title":"Title","description":"Description","ignored":true}]}""",
                ),
            )
            val api =
                Retrofit
                    .Builder()
                    .baseUrl(server.url("/"))
                    .addCallAdapterFactory(NetworkCallAdapterFactory(json))
                    .addConverterFactory(
                        json.asConverterFactory("application/json".toMediaType()),
                    ).build()
                    .create(SampleApi::class.java)

            val response = api.getSamples()

            assertEquals("Title", response.data.single().title)
            assertEquals("/samples", server.takeRequest().path)
        }

    @Test
    fun `remote data source maps api response to network model`() =
        runTest {
            val api =
                object : SampleApi {
                    override suspend fun getSamples(): ApiResponse<List<SampleResponse>> =
                        ApiResponse(
                            listOf(SampleResponse(id = 1, title = "Title", description = null)),
                        )
                }

            val result = RetrofitSampleRemoteDataSource(api).fetchSamples()

            assertEquals(NetworkSample(1, "Title", ""), result.single())
        }

    @Test
    fun `remote data source hides serialization implementation error`() {
        val api =
            object : SampleApi {
                override suspend fun getSamples() = throw SerializationException("invalid response")
            }

        assertThrows(RemoteDecodingException::class.java) {
            runTest { RetrofitSampleRemoteDataSource(api).fetchSamples() }
        }
    }

    @Test
    fun `remote data source hides network implementation error`() {
        val api =
            object : SampleApi {
                override suspend fun getSamples() =
                    throw NetworkHttpException(
                        statusCode = 401,
                        errorCode = "TOKEN_EXPIRED",
                        message = "Token expired",
                    )
            }

        val exception =
            assertThrows(RemoteRequestException::class.java) {
                runTest { RetrofitSampleRemoteDataSource(api).fetchSamples() }
            }

        assertEquals(401, exception.statusCode)
        assertEquals("TOKEN_EXPIRED", exception.errorCode)
        assertEquals("Token expired", exception.message)
    }

    @Test
    fun `remote data source classifies connection error`() {
        val api =
            object : SampleApi {
                override suspend fun getSamples() = throw UnknownHostException("offline")
            }

        val exception =
            assertThrows(RemoteNetworkException::class.java) {
                runTest { RetrofitSampleRemoteDataSource(api).fetchSamples() }
            }

        assertEquals(RemoteNetworkFailureReason.CONNECTION, exception.reason)
    }

    @Test
    fun `remote data source classifies timeout error`() {
        val api =
            object : SampleApi {
                override suspend fun getSamples() = throw SocketTimeoutException("timeout")
            }

        val exception =
            assertThrows(RemoteNetworkException::class.java) {
                runTest { RetrofitSampleRemoteDataSource(api).fetchSamples() }
            }

        assertEquals(RemoteNetworkFailureReason.TIMEOUT, exception.reason)
    }

    @Test
    fun `remote data source rethrows coroutine cancellation`() {
        val api =
            object : SampleApi {
                override suspend fun getSamples() = throw CancellationException("cancelled")
            }

        assertThrows(CancellationException::class.java) {
            runTest { RetrofitSampleRemoteDataSource(api).fetchSamples() }
        }
    }

    @Test
    fun `call adapter converts unsuccessful response to network exception`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"code":"TOKEN_EXPIRED","message":"Token expired"}"""),
        )
        val api =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .addCallAdapterFactory(NetworkCallAdapterFactory(json))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(SampleApi::class.java)

        val exception =
            assertThrows(NetworkHttpException::class.java) {
                runTest { api.getSamples() }
            }

        assertEquals(401, exception.statusCode)
        assertEquals("TOKEN_EXPIRED", exception.errorCode)
        assertEquals("Token expired", exception.message)
    }
}
