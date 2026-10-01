package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.DownloadOverview
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadStatusSnapshot
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.model.toDownloadOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface DownloadRepository {
    val downloads: Flow<List<DownloadTask>>

    val overview: Flow<DownloadOverview>
        get() = downloads.map { it.toDownloadOverview() }.distinctUntilChanged()

    val statuses: Flow<List<DownloadStatusSnapshot>>
        get() =
            downloads
                .map { tasks ->
                    tasks
                        .filter { !it.deletePending && it.status != DownloadStatus.COMPLETED }
                        .map { DownloadStatusSnapshot(it.id, it.title, it.status) }
                }.distinctUntilChanged()

    fun observeDownload(id: String): Flow<DownloadTask?> =
        downloads.map { tasks -> tasks.firstOrNull { it.id == id } }.distinctUntilChanged()

    val completedDownloads: Flow<List<DownloadTask>>
        get() =
            downloads
                .map { tasks -> tasks.filter { it.status == DownloadStatus.COMPLETED && !it.deletePending } }
                .distinctUntilChanged()

    val deletionPendingDownloads: Flow<List<DownloadTask>>
        get() =
            downloads
                .map { tasks -> tasks.filter(DownloadTask::deletePending) }
                .distinctUntilChanged()

    suspend fun enqueue(requests: List<DownloadRequest>): EnqueueDownloadsResult

    suspend fun pause(id: String)

    suspend fun resume(id: String)

    suspend fun cancel(id: String) = delete(id)

    suspend fun rename(
        id: String,
        title: String,
    )

    suspend fun delete(id: String)

    suspend fun toggleFavorite(id: String)

    suspend fun retry(id: String)

    suspend fun pauseAll()

    suspend fun resumeAll()

    suspend fun recoverPendingDownloads()

    suspend fun refreshNetworkPolicy()
}
