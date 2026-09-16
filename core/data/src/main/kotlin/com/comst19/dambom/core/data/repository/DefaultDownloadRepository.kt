package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.data.download.DownloadFileStore
import com.comst19.dambom.core.data.download.DownloadWorkScheduler
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class DefaultDownloadRepository
    @Inject
    internal constructor(
        private val dao: DownloadTaskDao,
        private val scheduler: DownloadWorkScheduler,
        private val fileStore: DownloadFileStore,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : DownloadRepository {
        private val deletionMutex = Mutex()

        override val downloads: Flow<List<DownloadTask>> =
            dao
                .observeAll()
                .map { entities ->
                    entities.filterNot(DownloadTaskEntity::deletePending).map { entity ->
                        entity.toDomain(localFilePath = fileStore.completedFilePath(entity.localFileName))
                    }
                }.distinctUntilChanged()
                .flowOn(ioDispatcher)

        override fun observeDownload(id: String): Flow<DownloadTask?> =
            dao
                .observeById(id)
                .distinctUntilChanged()
                .map { entity ->
                    entity?.toDomain(localFilePath = fileStore.completedFilePath(entity.localFileName))
                }.flowOn(ioDispatcher)

        override val completedDownloads: Flow<List<DownloadTask>> =
            dao
                .observeCompleted()
                .distinctUntilChanged()
                .map { entities ->
                    entities.map { entity ->
                        entity.toDomain(localFilePath = fileStore.completedFilePath(entity.localFileName))
                    }
                }.distinctUntilChanged()
                .flowOn(ioDispatcher)

        override val deletionPendingDownloads: Flow<List<DownloadTask>> =
            dao
                .observePendingDeletions()
                .distinctUntilChanged()
                .map { entities ->
                    entities.map { entity ->
                        entity.toDomain(localFilePath = fileStore.completedFilePath(entity.localFileName))
                    }
                }.distinctUntilChanged()
                .flowOn(ioDispatcher)

        override suspend fun enqueue(requests: List<DownloadRequest>): EnqueueDownloadsResult =
            withContext(ioDispatcher) {
                var addedCount = 0
                var duplicateCount = 0
                requests.distinctBy { it.url to it.quality }.forEach { request ->
                    val inserted = dao.insert(request.toEntity(System.currentTimeMillis()))
                    if (inserted == INSERT_IGNORED) duplicateCount++ else addedCount++
                }
                if (addedCount > 0) {
                    scheduler.schedule()
                } else if (dao.countSchedulable() > 0) {
                    scheduler.ensureScheduled()
                }
                EnqueueDownloadsResult(addedCount, duplicateCount)
            }

        override suspend fun pause(id: String) {
            dao.pause(id, System.currentTimeMillis())
        }

        override suspend fun resume(id: String) {
            dao.queueAgain(id, System.currentTimeMillis())
            scheduler.schedule()
        }

        override suspend fun rename(
            id: String,
            title: String,
        ) = withContext(ioDispatcher) {
            dao.updateTitle(id, title, System.currentTimeMillis())
        }

        override suspend fun delete(id: String) =
            withContext(ioDispatcher) {
                deletionMutex.withLock { cleanupDeletion(id, recordIntent = true) }
            }

        override suspend fun retry(id: String) {
            dao.retry(id, System.currentTimeMillis())
            scheduler.schedule()
        }

        override suspend fun pauseAll() {
            dao.pauseAll(System.currentTimeMillis())
        }

        override suspend fun resumeAll() {
            dao.resumeAll(System.currentTimeMillis())
            scheduler.schedule()
        }

        override suspend fun recoverPendingDownloads() =
            withContext(ioDispatcher) {
                dao.getPendingDeletions().forEach { task ->
                    try {
                        deletionMutex.withLock { cleanupDeletion(task.id, recordIntent = false) }
                    } catch (_: IOException) {
                    }
                }
                if (dao.countSchedulable() > 0) scheduler.ensureScheduled()
            }

        override suspend fun refreshNetworkPolicy() {
            if (dao.countSchedulable() > 0) scheduler.reschedule()
        }

        private suspend fun cleanupDeletion(
            id: String,
            recordIntent: Boolean,
        ) {
            val task =
                dao.getById(id)?.let { current ->
                    when {
                        current.deletePending -> current
                        !recordIntent -> null
                        dao.claimForDeletion(id, System.currentTimeMillis()) == 0 -> null
                        else -> dao.getById(id)
                    }
                } ?: return
            val localFileName = task.localFileName ?: fileStore.completedFile(id, task.url, task.mimeType).name
            if (!fileStore.delete(id, localFileName)) throw IOException("Unable to delete download files")
            if (dao.deleteClaimed(id) == 0) throw IOException("Unable to delete download record")
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
        retryCount = 0,
        deletePending = false,
        localFileName = null,
        createdAtMillis = now,
        updatedAtMillis = now,
    )

private fun DownloadTaskEntity.toDomain(localFilePath: String?): DownloadTask =
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
        localFilePath = localFilePath,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        deletePending = deletePending,
    )

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == value } ?: default

private const val INSERT_IGNORED = -1L
