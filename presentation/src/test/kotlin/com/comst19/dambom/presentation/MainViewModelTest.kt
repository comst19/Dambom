package com.comst19.dambom.presentation

import com.comst19.dambom.core.common.ui.SnackbarEventBus
import com.comst19.dambom.core.domain.error.ErrorHandler
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.model.NetworkConnection
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.NetworkMonitor
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.TopLevelNavKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.presentation.contract.AppStartupState
import com.comst19.dambom.presentation.contract.StartupFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `startup is ready with the coordinator destination`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                MainViewModel(
                    FakeSettingsRepository(),
                    FakeNetworkMonitor,
                    FakeDownloadRepository,
                    SuccessfulStartupCoordinator,
                    ErrorHandler(),
                    SnackbarEventBus(),
                )

            advanceUntilIdle()

            assertEquals(AppStartupState.Ready(HomeKey), viewModel.startupState.value)
        }

    @Test
    fun `startup failure clears the initializing state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                MainViewModel(
                    FakeSettingsRepository(),
                    FakeNetworkMonitor,
                    FakeDownloadRepository,
                    FailingStartupCoordinator,
                    ErrorHandler(),
                    SnackbarEventBus(),
                )

            advanceUntilIdle()

            assertEquals(AppStartupState.Failed(StartupFailure.InitializationFailed), viewModel.startupState.value)
        }

    @Test
    fun `download feedback reports visible changes without constraint reset noise`() {
        val task = downloadTask(DownloadStatus.DOWNLOADING)

        assertEquals(
            listOf(DownloadFeedback(DownloadFeedbackType.STARTED, task.title)),
            downloadFeedback(mapOf(task.id to DownloadStatus.QUEUED), listOf(task)),
        )
        assertEquals(
            listOf(DownloadFeedback(DownloadFeedbackType.COMPLETED, task.title)),
            downloadFeedback(
                mapOf(task.id to DownloadStatus.DOWNLOADING),
                listOf(task.copy(status = DownloadStatus.COMPLETED)),
            ),
        )
        assertEquals(
            emptyList<DownloadFeedback>(),
            downloadFeedback(
                mapOf(task.id to DownloadStatus.DOWNLOADING),
                listOf(task.copy(status = DownloadStatus.QUEUED)),
            ),
        )
    }
}

private object SuccessfulStartupCoordinator : StartupCoordinator {
    override suspend fun initialize(): TopLevelNavKey = HomeKey
}

private object FailingStartupCoordinator : StartupCoordinator {
    override suspend fun initialize(): TopLevelNavKey = error("Initialization failed")
}

private class FakeSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> = flowOf(AppSettings())

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    ) = Unit

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit
}

private object FakeNetworkMonitor : NetworkMonitor {
    override val connection: Flow<NetworkConnection> = flowOf(NetworkConnection.UNMETERED)
}

private object FakeDownloadRepository : DownloadRepository {
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

    override suspend fun refreshNetworkPolicy() = Unit
}

private fun downloadTask(status: DownloadStatus) =
    DownloadTask(
        id = "video-1",
        url = "https://example.com/video.mp4",
        sourcePageUrl = "https://example.com/watch",
        title = "여행 영상",
        mimeType = "video/mp4",
        expectedBytes = 100L,
        downloadedBytes = 50L,
        quality = "원본",
        status = status,
        failureReason = null,
        localFileName = null,
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
    )
