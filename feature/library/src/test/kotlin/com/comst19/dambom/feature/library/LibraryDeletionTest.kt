package com.comst19.dambom.feature.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
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
import com.comst19.dambom.feature.library.file.LibraryFileManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryDeletionTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `batch continues after failure ignores duplicate calls and retries only failed item`() =
        runTest(dispatcherRule.dispatcher) {
            val repository = DeletionRepository()
            val events = AppEventBus()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val viewModel =
                LibraryViewModel(
                    repository,
                    DeletionSettings,
                    SpyNavigationDispatcher(),
                    SavedStateHandle(),
                    LibraryFileManager(context, dispatcherRule.dispatcher),
                    events,
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            viewModel.selectAllVisible()
            runCurrent()

            viewModel.deleteSelected()
            runCurrent()
            viewModel.deleteSelected()
            viewModel.clearSelection()
            runCurrent()
            assertEquals(listOf("first"), repository.calls)
            repository.gate.complete(Unit)
            runCurrent()

            assertEquals(listOf("first", "failed", "last"), repository.calls)
            assertEquals(setOf("failed"), viewModel.uiState.value.selectedIds)
            assertEquals(
                AppEvent.ShowSnackbar(UiText.Resource(R.string.library_delete_selected_partial, listOf(2, 1))),
                events.events.first(),
            )

            repository.fail = false
            viewModel.deleteSelected()
            runCurrent()

            assertEquals(listOf("first", "failed", "last", "failed"), repository.calls)
            assertEquals(emptySet<String>(), viewModel.uiState.value.selectedIds)
            assertEquals(
                AppEvent.ShowSnackbar(UiText.Resource(R.string.library_delete_selected_success, listOf(1))),
                events.events.first(),
            )
        }

    @Test
    fun `pending deletion stays visible and blocks playback and file actions`() =
        runTest(dispatcherRule.dispatcher) {
            val pending = savedVideo("pending").copy(localFilePath = null, deletePending = true)
            val activePending =
                savedVideo("active-pending").copy(
                    status = DownloadStatus.DOWNLOADING,
                    localFilePath = null,
                    deletePending = true,
                )
            val repository = DeletionRepository().apply { downloads.value = listOf(pending, activePending) }
            val navigation = SpyNavigationDispatcher()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val viewModel =
                LibraryViewModel(
                    repository,
                    DeletionSettings,
                    navigation,
                    SavedStateHandle(),
                    LibraryFileManager(context, dispatcherRule.dispatcher),
                    AppEventBus(),
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            assertEquals(
                listOf("pending"),
                viewModel.uiState.value.videos
                    .map(DownloadTask::id),
            )
            assertEquals(
                true,
                viewModel.uiState.value.videos
                    .single()
                    .deletePending,
            )
            viewModel.openVideo(pending.id)
            viewModel.rename(pending, "renamed")
            viewModel.export(pending, Uri.EMPTY)
            viewModel.exportToConfiguredLocation(pending)
            runCurrent()

            assertTrue(navigation.dispatched.isEmpty())
            assertEquals(0, repository.renameCount)
            assertNull(viewModel.createShareIntent(pending))

            repository.gate.complete(Unit)
            viewModel.delete(pending)
            runCurrent()

            assertEquals(listOf("pending"), repository.calls)
        }

    @Test
    fun `overlapping repository flows show a completed pending deletion once`() =
        runTest(dispatcherRule.dispatcher) {
            val normal = savedVideo("normal")
            val pending = savedVideo("pending").copy(localFilePath = null, deletePending = true)
            val baseRepository = DeletionRepository()
            val repository =
                object : DownloadRepository by baseRepository {
                    override val completedDownloads = flowOf(listOf(normal, pending))
                    override val deletionPendingDownloads = flowOf(listOf(pending))
                }
            val viewModel =
                LibraryViewModel(
                    repository,
                    DeletionSettings,
                    SpyNavigationDispatcher(),
                    SavedStateHandle(),
                    LibraryFileManager(ApplicationProvider.getApplicationContext(), dispatcherRule.dispatcher),
                    AppEventBus(),
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            assertEquals(
                listOf("normal", "pending"),
                viewModel.uiState.value.videos
                    .map(DownloadTask::id),
            )
        }
}

private class DeletionRepository : DownloadRepository {
    val calls = mutableListOf<String>()
    val gate = CompletableDeferred<Unit>()
    var fail = true
    var renameCount = 0
    override val downloads = MutableStateFlow(listOf("first", "failed", "last").map(::savedVideo))

    override suspend fun delete(id: String) {
        calls += id
        gate.await()
        if (id == "failed" && fail) throw IOException("Deletion failed")
        downloads.value = downloads.value.filterNot { it.id == id }
    }

    override suspend fun enqueue(requests: List<DownloadRequest>) = EnqueueDownloadsResult(0, 0)

    override suspend fun pause(id: String) = Unit

    override suspend fun resume(id: String) = Unit

    override suspend fun rename(
        id: String,
        title: String,
    ) {
        renameCount++
    }

    override suspend fun retry(id: String) = Unit

    override suspend fun pauseAll() = Unit

    override suspend fun resumeAll() = Unit

    override suspend fun recoverPendingDownloads() = Unit

    override suspend fun refreshNetworkPolicy() = Unit
}

private object DeletionSettings : SettingsRepository {
    override val settings = flowOf(AppSettings())

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

internal fun savedVideo(id: String) =
    DownloadTask(
        id = id,
        url = "https://example.com/$id.mp4",
        sourcePageUrl = "https://example.com",
        title = id,
        mimeType = "video/mp4",
        expectedBytes = 100L,
        downloadedBytes = 100L,
        quality = "original",
        status = DownloadStatus.COMPLETED,
        failureReason = null,
        localFileName = "$id.mp4",
        localFilePath = "/videos/$id.mp4",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
