package com.comst19.dambom.presentation.startup

import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.ui.locale.AppLanguagePreferences
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.feature.home.api.HomeKey
import com.comst19.dambom.presentation.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StartupCoordinatorTest {
    @Test
    fun `startup returns Home without waiting for download recovery`() =
        runTest {
            val languagePreferences = FakeStartupAppLanguagePreferences()
            val coordinator =
                DefaultStartupCoordinator(
                    BlockingRecoveryRepository,
                    languagePreferences,
                    AppEventBus(),
                    backgroundScope,
                )

            assertEquals(HomeKey, withTimeoutOrNull(100) { coordinator.initialize() })
            assertEquals(1, languagePreferences.syncCalls)
        }

    @Test
    fun `startup waits for initial language synchronization before returning Home`() =
        runTest {
            val syncGate = CompletableDeferred<Unit>()
            val languagePreferences = FakeStartupAppLanguagePreferences(syncGate = syncGate)
            val coordinator =
                DefaultStartupCoordinator(
                    BlockingRecoveryRepository,
                    languagePreferences,
                    AppEventBus(),
                    backgroundScope,
                )

            val startupResult = async { coordinator.initialize() }
            runCurrent()

            assertFalse(startupResult.isCompleted)

            syncGate.complete(Unit)
            assertEquals(HomeKey, startupResult.await())
            assertEquals(1, languagePreferences.syncCalls)
        }

    @Test
    fun `language synchronization failure does not block app startup`() =
        runTest {
            val languagePreferences = FakeStartupAppLanguagePreferences(syncFailure = IOException("Disk error"))
            val coordinator =
                DefaultStartupCoordinator(
                    BlockingRecoveryRepository,
                    languagePreferences,
                    AppEventBus(),
                    backgroundScope,
                )

            assertEquals(HomeKey, coordinator.initialize())
            assertEquals(1, languagePreferences.syncCalls)
        }

    @Test
    fun `download recovery failure does not block app startup`() =
        runTest {
            val events = AppEventBus()
            val coordinator =
                DefaultStartupCoordinator(
                    FailingRecoveryRepository,
                    FakeStartupAppLanguagePreferences(),
                    events,
                    backgroundScope,
                )

            assertEquals(HomeKey, coordinator.initialize())
            runCurrent()
            assertEquals(
                AppEvent.ShowSnackbar(UiText.Resource(R.string.download_recovery_failed)),
                events.events.first(),
            )
        }
}

private class FakeStartupAppLanguagePreferences(
    private val syncGate: CompletableDeferred<Unit>? = null,
    private val syncFailure: Throwable? = null,
) : AppLanguagePreferences {
    var syncCalls = 0

    override suspend fun selectedLanguageTags(): String = ""

    override suspend fun apply(languageTag: String) = Unit

    override suspend fun syncSystemLanguage() {
        syncCalls++
        syncGate?.await()
        syncFailure?.let { throw it }
    }
}

private object BlockingRecoveryRepository : DownloadRepository by FailingRecoveryRepository {
    override suspend fun recoverPendingDownloads() = awaitCancellation()
}

private object FailingRecoveryRepository : DownloadRepository {
    override val downloads: Flow<List<DownloadTask>> = flowOf(emptyList())

    override suspend fun setPrivate(
        ids: Set<String>,
        isPrivate: Boolean,
    ) = Unit

    override suspend fun enqueue(requests: List<DownloadRequest>) = EnqueueDownloadsResult(0, 0)

    override suspend fun pause(id: String) = Unit

    override suspend fun resume(id: String) = Unit

    override suspend fun cancel(id: String) = Unit

    override suspend fun toggleFavorite(id: String) = Unit

    override suspend fun rename(
        id: String,
        title: String,
    ) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun retry(id: String) = Unit

    override suspend fun pauseAll() = Unit

    override suspend fun resumeAll() = Unit

    override suspend fun recoverPendingDownloads(): Unit = error("WorkManager unavailable")

    override suspend fun refreshNetworkPolicy() = Unit
}
