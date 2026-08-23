package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDownloadRepository
    @Inject
    internal constructor(
        private val dao: DownloadTaskDao,
        private val scheduler: DownloadWorkScheduler,
        private val fileStore: DownloadFileStore,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : DownloadRepository {
        override val downloads: Flow<List<DownloadTask>> = dao.observeAll().map { entities -> entities.map(DownloadTaskEntity::toDomain) }

        override suspend fun enqueue(requests: List<DownloadRequest>): EnqueueDownloadsResult =
            withContext(ioDispatcher) {
                var addedCount = 0
                var duplicateCount = 0
                requests.distinctBy { it.url to it.quality }.forEach { request ->
                    val inserted = dao.insert(request.toEntity(System.currentTimeMillis()))
                    if (inserted == INSERT_IGNORED) duplicateCount++ else addedCount++
                }
                if (addedCount > 0) scheduler.schedule()
                EnqueueDownloadsResult(addedCount, duplicateCount)
            }

        override suspend fun pause(id: String) {
            dao.pause(id, System.currentTimeMillis())
        }

        override suspend fun resume(id: String) {
            dao.queueAgain(id, System.currentTimeMillis())
            scheduler.schedule()
        }

        override suspend fun cancel(id: String) =
            withContext(ioDispatcher) {
                val task = dao.getById(id)
                dao.delete(id)
                fileStore.delete(id, task?.localFileName)
            }

        override suspend fun retry(id: String) {
            dao.queueAgain(id, System.currentTimeMillis())
            scheduler.schedule()
        }

        override suspend fun pauseAll() {
            dao.pauseAll(System.currentTimeMillis())
        }

        override suspend fun resumeAll() {
            dao.resumeAll(System.currentTimeMillis())
            scheduler.schedule()
        }
    }

private fun DownloadRequest.toEntity(now: Long): DownloadTaskEntity =
    DownloadTaskEntity(
        id = id,
        url = url,
        sourcePageUrl = sourcePageUrl,
        host = URI(url).host.orEmpty().lowercase(),
        title = title,
        mimeType = mimeType,
        expectedBytes = expectedBytes,
        downloadedBytes = 0L,
        quality = quality,
        status = DownloadStatus.QUEUED.name,
        failureReason = null,
        localFileName = null,
        createdAtMillis = now,
        updatedAtMillis = now,
    )

private fun DownloadTaskEntity.toDomain(): DownloadTask =
    DownloadTask(
        id = id,
        url = url,
        sourcePageUrl = sourcePageUrl,
        title = title,
        mimeType = mimeType,
        expectedBytes = expectedBytes,
        downloadedBytes = downloadedBytes,
        quality = quality,
        status = enumValueOrDefault(status, DownloadStatus.FAILED),
        failureReason = failureReason?.let { enumValueOrDefault(it, DownloadFailureReason.UNKNOWN) },
        localFileName = localFileName,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == value } ?: default

private const val INSERT_IGNORED = -1L
