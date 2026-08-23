package com.comst19.dambom.core.data.repository

import app.cash.turbine.test
import com.comst19.dambom.core.data.remote.datasource.SampleRemoteDataSource
import com.comst19.dambom.core.data.remote.error.RemoteDecodingException
import com.comst19.dambom.core.data.remote.error.RemoteNetworkException
import com.comst19.dambom.core.data.remote.error.RemoteNetworkFailureReason
import com.comst19.dambom.core.data.remote.error.RemoteRequestException
import com.comst19.dambom.core.data.remote.model.NetworkSample
import com.comst19.dambom.core.database.SampleDao
import com.comst19.dambom.core.database.SampleEntity
import com.comst19.dambom.core.domain.error.AppDecodingException
import com.comst19.dambom.core.domain.error.AppErrorCode
import com.comst19.dambom.core.domain.error.AppNetworkException
import com.comst19.dambom.core.domain.error.AppRequestException
import com.comst19.dambom.core.domain.error.NetworkFailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultSampleRepositoryTest {
    @Test
    fun `refresh saves remote data and observers receive domain models`() =
        runTest {
            val sampleDao = FakeSampleDao()
            val repository =
                DefaultSampleRepository(
                    remote = Remote { listOf(NetworkSample(1, " Title ", "")) },
                    sampleDao = sampleDao,
                )

            repository.refreshSamples()
            repository.observeSamples().test {
                assertEquals("Title", awaitItem().single().title)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `network failure preserves local data and propagates error`() =
        runTest {
            val sampleDao = FakeSampleDao(listOf(SampleEntity(7, "Cached", "value", 0)))
            val repository =
                DefaultSampleRepository(
                    Remote {
                        throw RemoteRequestException(
                            statusCode = 401,
                            errorCode = "TOKEN_EXPIRED",
                            message = "Token expired",
                            cause = IllegalStateException(),
                        )
                    },
                    sampleDao,
                )

            val failure = runCatching { repository.refreshSamples() }.exceptionOrNull()

            require(failure is AppRequestException)
            assertEquals(401, failure.statusCode)
            assertEquals(AppErrorCode.TOKEN_EXPIRED, failure.errorCode)
            assertEquals("TOKEN_EXPIRED", failure.rawErrorCode)
            assertEquals("Token expired", failure.message)
            repository.observeSamples().test {
                assertEquals(7L, awaitItem().single().id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `transport failure preserves local data and propagates network reason`() =
        runTest {
            val sampleDao = FakeSampleDao(listOf(SampleEntity(7, "Cached", "value", 0)))
            val repository =
                DefaultSampleRepository(
                    Remote {
                        throw RemoteNetworkException(
                            RemoteNetworkFailureReason.TIMEOUT,
                            java.net.SocketTimeoutException(),
                        )
                    },
                    sampleDao,
                )

            val failure = runCatching { repository.refreshSamples() }.exceptionOrNull()

            require(failure is AppNetworkException)
            assertEquals(NetworkFailureReason.TIMEOUT, failure.reason)
            repository.observeSamples().test {
                assertEquals(7L, awaitItem().single().id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `decoding failure is mapped to domain error`() =
        runTest {
            val repository =
                DefaultSampleRepository(
                    Remote { throw RemoteDecodingException(IllegalArgumentException()) },
                    FakeSampleDao(),
                )

            val failure = runCatching { repository.refreshSamples() }.exceptionOrNull()

            require(failure is AppDecodingException)
        }

    @Test
    fun `unexpected failure is not converted`() =
        runTest {
            val expected = IllegalStateException("Unexpected")
            val repository = DefaultSampleRepository(Remote { throw expected }, FakeSampleDao())

            val failure = runCatching { repository.refreshSamples() }.exceptionOrNull()

            assertEquals(expected, failure)
        }

    @Test
    fun `cancellation is rethrown`() {
        val repository = DefaultSampleRepository(Remote { throw CancellationException() }, FakeSampleDao())
        assertThrows(CancellationException::class.java) {
            runTest { repository.refreshSamples() }
        }
    }
}

private class Remote(
    private val block: suspend () -> List<NetworkSample>,
) : SampleRemoteDataSource {
    override suspend fun fetchSamples(): List<NetworkSample> = block()
}

private class FakeSampleDao(
    initial: List<SampleEntity> = emptyList(),
) : SampleDao {
    private val samples = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<SampleEntity>> = samples

    override fun observeById(id: Long): Flow<SampleEntity?> =
        MutableStateFlow(
            samples.value.firstOrNull { it.id == id },
        )

    override suspend fun upsertAll(samples: List<SampleEntity>) {
        this.samples.value = samples
    }

    override suspend fun deleteAll() {
        samples.value = emptyList()
    }
}
