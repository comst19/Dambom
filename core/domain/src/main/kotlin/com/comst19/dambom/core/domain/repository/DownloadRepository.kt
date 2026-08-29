package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface DownloadRepository {
    val downloads: Flow<List<DownloadTask>>

    val completedDownloads: Flow<List<DownloadTask>>
        get() =
            downloads
                .map { tasks -> tasks.filter { it.status == DownloadStatus.COMPLETED } }
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

    suspend fun retry(id: String)

    suspend fun pauseAll()

    suspend fun resumeAll()

    suspend fun ensureDownloadsScheduled() = Unit

    suspend fun refreshNetworkPolicy()
}
