package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    val downloads: Flow<List<DownloadTask>>

    suspend fun enqueue(requests: List<DownloadRequest>): EnqueueDownloadsResult

    suspend fun pause(id: String)

    suspend fun resume(id: String)

    suspend fun cancel(id: String)

    suspend fun retry(id: String)

    suspend fun pauseAll()

    suspend fun resumeAll()
}
