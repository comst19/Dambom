package com.comst19.dambom.feature.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.comst19.dambom.core.common.url.SharedUrlBus
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
}

private fun createViewModel() =
    HomeViewModel(
        navigation = SpyNavigationDispatcher(),
        settingsRepository = EnabledClipboardSettingsRepository,
        downloadRepository = EmptyDownloadRepository,
        sharedUrlBus = SharedUrlBus(),
        savedStateHandle = SavedStateHandle(),
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

    override suspend fun refreshNetworkPolicy() = Unit
}

private const val FIRST_URL = "https://example.com/first"
private const val SECOND_URL = "https://example.com/second"
