package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal class LibraryTestDownloads(
    initial: List<DownloadTask>,
) : DownloadRepository {
    override val downloads = MutableStateFlow(initial)

    override suspend fun enqueue(requests: List<DownloadRequest>) = EnqueueDownloadsResult(0, 0)

    override suspend fun pause(id: String) = Unit

    override suspend fun resume(id: String) = Unit

    override suspend fun toggleFavorite(id: String) {
        downloads.value = downloads.value.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
    }

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

internal object LibraryTestSettings : SettingsRepository {
    override val settings = flowOf(AppSettings(useConfiguredDownloadLocation = false))

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

internal fun libraryTestVideo(path: String): DownloadTask =
    DownloadTask(
        id = "saved-video",
        url = "https://example.com/video.mp4",
        sourcePageUrl = "https://example.com",
        title = "Saved video",
        mimeType = "video/mp4",
        expectedBytes = null,
        downloadedBytes = 1L,
        quality = "original",
        status = DownloadStatus.COMPLETED,
        failureReason = null,
        localFileName = "video.mp4",
        localFilePath = path,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
