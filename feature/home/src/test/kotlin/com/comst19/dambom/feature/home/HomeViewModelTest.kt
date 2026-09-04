package com.comst19.dambom.feature.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.url.SharedUrlBus
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `dismissed clipboard URL is suggested only once`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                assertEquals(true, awaitItem().clipboardSuggestionEnabled)

                viewModel.suggestClipboardText(FIRST_URL)
                assertEquals(FIRST_URL, awaitItem().clipboardUrl)

                viewModel.dismissClipboardSuggestion()
                assertEquals(null, awaitItem().clipboardUrl)

                viewModel.suggestClipboardText(FIRST_URL)
                runCurrent()
                assertEquals(null, viewModel.uiState.value.clipboardUrl)

                viewModel.suggestClipboardText(SECOND_URL)
                assertEquals(SECOND_URL, awaitItem().clipboardUrl)
            }
        }

    @Test
    fun `clipboard text without a valid URL is not suggested`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem()
                assertEquals(true, awaitItem().clipboardSuggestionEnabled)

                viewModel.suggestClipboardText("copied https://???")

                runCurrent()
                assertEquals(null, viewModel.uiState.value.clipboardUrl)
            }
        }

    @Test
    fun `download progress excludes bytes with unknown expected size`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(MixedExpectedSizeDownloadRepository)

            viewModel.uiState.test {
                awaitItem()

                assertEquals(0.25f, awaitItem().downloadSummary.progress, 0f)
            }
        }

    @Test
    fun `editing URL does not rescan unchanged download history`() =
        runTest(mainDispatcherRule.dispatcher) {
            val downloads =
                CountingDownloadList(
                    List(1_000) { activeDownload("video-$it", expectedBytes = 100L, downloadedBytes = 25L) },
                )
            val repository =
                object : DownloadRepository by EmptyDownloadRepository {
                    override val downloads: Flow<List<DownloadTask>> = flowOf(downloads)
                }
            val viewModel = createViewModel(repository)

            viewModel.uiState.test {
                awaitItem()
                assertEquals(1_000, awaitItem().downloadSummary.activeCount)
                val readsBeforeEditing = downloads.readCount

                repeat(20) { index ->
                    val url = "https://example.com/$index"
                    viewModel.updateUrl(url)
                    assertEquals(url, awaitItem().url)
                }

                assertEquals("URL edits must not reread download history", readsBeforeEditing, downloads.readCount)
            }
        }

    @Test
    fun `clipboard preference write failure does not escape the ViewModel`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settings =
                object : SettingsRepository by EnabledClipboardSettingsRepository {
                    override suspend fun setClipboardSuggestion(
                        promptShown: Boolean,
                        enabled: Boolean,
                    ): Unit = throw IOException("Storage unavailable")
                }
            val events = AppEventBus()
            val viewModel = createViewModel(settingsRepository = settings, appEventBus = events)

            viewModel.setClipboardSuggestionEnabled(true)
            runCurrent()
            assertEquals(
                AppEvent.ShowSnackbar(UiText.Resource(R.string.home_clipboard_save_failed)),
                events.events.first(),
            )
        }

    @Test
    fun `download updates still refresh summary after URL edits`() =
        runTest(mainDispatcherRule.dispatcher) {
            val task = activeDownload("video", expectedBytes = 100L, downloadedBytes = 25L)
            val downloads = MutableStateFlow(listOf(task))
            val repository =
                object : DownloadRepository by EmptyDownloadRepository {
                    override val downloads: Flow<List<DownloadTask>> = downloads
                }
            val viewModel = createViewModel(repository)
            viewModel.uiState.test {
                awaitItem()
                assertEquals(0.25f, awaitItem().downloadSummary.progress)
                viewModel.updateUrl(FIRST_URL)
                assertEquals(FIRST_URL, awaitItem().url)
                downloads.value = listOf(task.copy(downloadedBytes = 75L))
                assertEquals(0.75f, awaitItem().downloadSummary.progress)
            }
        }

    @Test
    fun `cancelled clipboard preference write does not show a failure message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settings =
                object : SettingsRepository by EnabledClipboardSettingsRepository {
                    override suspend fun setClipboardSuggestion(
                        promptShown: Boolean,
                        enabled: Boolean,
                    ): Unit = throw CancellationException("Cancelled")
                }
            val events = AppEventBus()
            val viewModel = createViewModel(settingsRepository = settings, appEventBus = events)
            events.events.test {
                viewModel.setClipboardSuggestionEnabled(true)
                runCurrent()
                expectNoEvents()
            }
        }
}

private class CountingDownloadList(
    private val tasks: List<DownloadTask>,
) : AbstractList<DownloadTask>() {
    var readCount: Int = 0
        private set

    override val size: Int get() = tasks.size

    override fun get(index: Int): DownloadTask {
        readCount++
        return tasks[index]
    }
}

private fun createViewModel(
    downloadRepository: DownloadRepository = EmptyDownloadRepository,
    settingsRepository: SettingsRepository = EnabledClipboardSettingsRepository,
    appEventBus: AppEventBus = AppEventBus(),
) = HomeViewModel(
    navigation = SpyNavigationDispatcher(),
    settingsRepository = settingsRepository,
    downloadRepository = downloadRepository,
    sharedUrlBus = SharedUrlBus(),
    savedStateHandle = SavedStateHandle(),
    appEventBus = appEventBus,
)

private object EnabledClipboardSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> =
        flowOf(
            AppSettings(
                clipboardPromptShown = true,
                clipboardSuggestionEnabled = true,
            ),
        )

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    ) = Unit

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit

    override suspend fun setDownloadLocation(
        enabled: Boolean,
        treeUri: String?,
    ) = Unit
}

private object EmptyDownloadRepository : DownloadRepository {
    override val downloads: Flow<List<DownloadTask>> = flowOf(emptyList())

    override suspend fun enqueue(requests: List<DownloadRequest>): EnqueueDownloadsResult =
        EnqueueDownloadsResult(addedCount = 0, duplicateCount = 0)

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

    override suspend fun recoverPendingDownloads() = Unit

    override suspend fun refreshNetworkPolicy() = Unit
}

private object MixedExpectedSizeDownloadRepository : DownloadRepository by EmptyDownloadRepository {
    override val downloads: Flow<List<DownloadTask>> =
        flowOf(
            listOf(
                activeDownload(id = "known", expectedBytes = 100L, downloadedBytes = 25L),
                activeDownload(id = "unknown", expectedBytes = null, downloadedBytes = 1_000L),
            ),
        )
}

private fun activeDownload(
    id: String,
    expectedBytes: Long?,
    downloadedBytes: Long,
) = DownloadTask(
    id = id,
    url = "https://example.com/$id.mp4",
    sourcePageUrl = "https://example.com",
    title = id,
    mimeType = "video/mp4",
    expectedBytes = expectedBytes,
    downloadedBytes = downloadedBytes,
    quality = "원본",
    status = DownloadStatus.DOWNLOADING,
    failureReason = null,
    localFileName = null,
    createdAtMillis = 0L,
    updatedAtMillis = 0L,
)

private const val FIRST_URL = "https://example.com/first"
private const val SECOND_URL = "https://example.com/second"
