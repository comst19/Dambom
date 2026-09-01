package com.comst19.dambom.presentation.startup

import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.presentation.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupCoordinatorTest {
    @Test
    fun `startup returns Home without waiting for download recovery`() =
        runTest {
            val coordinator = DefaultStartupCoordinator(BlockingRecoveryRepository, AppEventBus(), backgroundScope)

            assertEquals(HomeKey, withTimeoutOrNull(100) { coordinator.initialize() })
        }

    @Test
    fun `download recovery failure does not block app startup`() =
        runTest {
            val events = AppEventBus()
            val coordinator = DefaultStartupCoordinator(FailingRecoveryRepository, events, backgroundScope)

            assertEquals(HomeKey, coordinator.initialize())
            runCurrent()
            assertEquals(
                AppEvent.ShowSnackbar(UiText.Resource(R.string.download_recovery_failed)),
                events.events.first(),
            )
        }
}

private object BlockingRecoveryRepository : DownloadRepository by FailingRecoveryRepository {
    override suspend fun recoverPendingDownloads() = awaitCancellation()
}

private object FailingRecoveryRepository : DownloadRepository {
    override val downloads: Flow<List<DownloadTask>> = flowOf(emptyList())

    override suspend fun enqueue(requests: List<DownloadRequest>) = EnqueueDownloadsResult(0, 0)

    override suspend fun pause(id: String) = Unit

    override suspend fun resume(id: String) = Unit

    override suspend fun cancel(id: String) = Unit

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
