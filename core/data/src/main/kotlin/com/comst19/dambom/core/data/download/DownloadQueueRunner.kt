package com.comst19.dambom.core.data.download

import android.content.Context
import android.os.SystemClock
import com.comst19.dambom.core.common.net.hasVideoFileExtension
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.data.media.MP4_SIGNATURE_BYTES
import com.comst19.dambom.core.data.media.hasMp4FileTypeBox
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.network.header.mediaRequestClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

internal enum class DownloadQueueRunResult {
    COMPLETED,
    RETRY,
}

internal fun interface DownloadProgressReporter {
    suspend fun onProgress(
        taskId: String,
        title: String,
        downloadedBytes: Long,
        totalBytes: Long?,
    )
}

@Singleton
@Suppress("TooManyFunctions")
internal class DownloadQueueRunner
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val dao: DownloadTaskDao,
        private val client: OkHttpClient,
        private val fileStore: DownloadFileStore,
        private val notifier: DownloadNotifier,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val executionMutex = Mutex()

        suspend fun run(
            networkSession: DownloadNetworkSession? = null,
            isConstraintSatisfied: () -> Boolean = { true },
            constraintRecheckDelayMillis: Long = DEFAULT_CONSTRAINT_RECHECK_DELAY_MS,
            progressReporter: DownloadProgressReporter = DownloadProgressReporter { _, _, _, _ -> },
        ): DownloadQueueRunResult =
            withContext(ioDispatcher) {
                executionMutex.withLock {
                    dao.resetInterrupted(System.currentTimeMillis())
                    try {
                        drainQueue(
                            networkSession = networkSession,
                            isConstraintSatisfied = isConstraintSatisfied,
                            constraintRecheckDelayMillis = constraintRecheckDelayMillis,
                            progressReporter = progressReporter,
                        )
                        DownloadQueueRunResult.COMPLETED
                    } catch (_: RetryQueueException) {
                        DownloadQueueRunResult.RETRY
                    } finally {
                        withContext(NonCancellable) {
                            networkSession?.evictAll()
                            dao.resetInterrupted(System.currentTimeMillis())
                        }
                    }
                }
            }

        private suspend fun drainQueue(
            networkSession: DownloadNetworkSession?,
            isConstraintSatisfied: () -> Boolean,
            constraintRecheckDelayMillis: Long,
            progressReporter: DownloadProgressReporter,
        ) = coroutineScope {
            val running = linkedMapOf<String, RunningDownload>()
            var retryRequired = false
            val queueUpdates = dao.observeQueuedCandidates().distinctUntilChanged().produceIn(this)
            queueUpdates.receive()
            try {
                while (true) {
                    val queued = dao.getQueued().toMutableList()
                    if (queued.isNotEmpty() && !canStartTransfer(networkSession, isConstraintSatisfied)) {
                        retryRequired = true
                    } else {
                        fillDownloadSlots(
                            queued = queued,
                            running = running,
                            networkSession = networkSession,
                            isConstraintSatisfied = isConstraintSatisfied,
                            constraintRecheckDelayMillis = constraintRecheckDelayMillis,
                            progressReporter = progressReporter,
                        )
                    }
                    if (running.isEmpty()) {
                        if (retryRequired) throw RetryQueueException()
                        return@coroutineScope
                    }
                    val completed =
                        select<Pair<String, DownloadOutcome>?> {
                            queueUpdates.onReceive { null }
                            running.forEach { (id, download) ->
                                download.job.onAwait { id to it }
                            }
                        } ?: continue
                    val (completedId, outcome) = completed
                    running.remove(completedId)
                    if (outcome == DownloadOutcome.RETRYABLE_FAILURE || outcome == DownloadOutcome.CONSTRAINT_LOST) {
                        retryRequired = true
                    }
                }
            } finally {
                queueUpdates.cancel()
            }
        }

        @Suppress("LongParameterList")
        private suspend fun kotlinx.coroutines.CoroutineScope.fillDownloadSlots(
            queued: MutableList<DownloadTaskEntity>,
            running: LinkedHashMap<String, RunningDownload>,
            networkSession: DownloadNetworkSession?,
            isConstraintSatisfied: () -> Boolean,
            constraintRecheckDelayMillis: Long,
            progressReporter: DownloadProgressReporter,
        ) {
            while (running.size < MAX_CONCURRENT_DOWNLOADS) {
                val activeHosts = running.values.groupingBy { it.host }.eachCount()
                val next =
                    selectNextDownload(
                        queued = queued,
                        runningIds = running.keys,
                        activeHosts = activeHosts,
                    ) ?: break
                queued.remove(next)
                val claimed =
                    dao.compareAndSetStatus(
                        id = next.id,
                        expectedStatus = DownloadStatus.QUEUED.name,
                        nextStatus = DownloadStatus.DOWNLOADING.name,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                if (claimed != 0) {
                    running[next.id] =
                        RunningDownload(
                            host = next.host,
                            job =
                                async {
                                    downloadSafely(
                                        task = next,
                                        networkSession = networkSession,
                                        isConstraintSatisfied = isConstraintSatisfied,
                                        constraintRecheckDelayMillis = constraintRecheckDelayMillis,
                                        progressReporter = progressReporter,
                                    )
                                },
                        )
                }
            }
        }

        private fun canStartTransfer(
            networkSession: DownloadNetworkSession?,
            isConstraintSatisfied: () -> Boolean,
        ): Boolean = (networkSession == null || networkSession.currentBinding() != null) && isConstraintSatisfied()

        private suspend fun isConstraintLoss(
            failure: IOException,
            networkSession: DownloadNetworkSession?,
            isConstraintSatisfied: () -> Boolean,
            constraintRecheckDelayMillis: Long,
        ): Boolean =
            when {
                failure.hasMissingJobNetworkCause() || !canStartTransfer(networkSession, isConstraintSatisfied) -> {
                    true
                }

                networkSession == null || constraintRecheckDelayMillis <= 0L -> {
                    false
                }

                else -> {
                    delay(constraintRecheckDelayMillis)
                    !canStartTransfer(networkSession, isConstraintSatisfied)
                }
            }

        private fun Throwable.hasMissingJobNetworkCause(): Boolean =
            generateSequence<Throwable>(this) { it.cause }
                .take(MAX_CAUSE_CHAIN_DEPTH)
                .any { it is MissingJobNetworkException }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun downloadSafely(
            task: DownloadTaskEntity,
            networkSession: DownloadNetworkSession?,
            isConstraintSatisfied: () -> Boolean,
            constraintRecheckDelayMillis: Long,
            progressReporter: DownloadProgressReporter,
        ): DownloadOutcome =
            try {
                if (!canStartTransfer(networkSession, isConstraintSatisfied)) {
                    throw MissingJobNetworkException()
                }
                download(task, networkSession, progressReporter, allowRestart = true)
                DownloadOutcome.COMPLETED
            } catch (stopped: DownloadStoppedException) {
                stopped.reason.toOutcome()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: ForegroundUpdateException) {
                throw failure.cause
            } catch (failure: StreamDownloadException) {
                markFailed(task, failure.reason)
                DownloadOutcome.PERMANENT_FAILURE
            } catch (_: MissingJobNetworkException) {
                DownloadOutcome.CONSTRAINT_LOST
            } catch (failure: IOException) {
                if (isConstraintLoss(failure, networkSession, isConstraintSatisfied, constraintRecheckDelayMillis)) {
                    DownloadOutcome.CONSTRAINT_LOST
                } else {
                    handleNetworkFailure(task)
                }
            } catch (failure: DownloadFailureException) {
                markFailed(task, failure.reason)
                DownloadOutcome.PERMANENT_FAILURE
            } catch (_: Exception) {
                markFailed(task, DownloadFailureReason.UNKNOWN)
                DownloadOutcome.PERMANENT_FAILURE
            } finally {
                withContext(NonCancellable) { cleanupDeletedStream(task) }
            }

        private suspend fun cleanupDeletedStream(task: DownloadTaskEntity) {
            val current = dao.getById(task.id)
            if ((current == null || current.deletePending) && streamMimeType(task.url, task.mimeType) != null) {
                val localName =
                    current?.localFileName ?: fileStore.completedFile(task.id, task.url, task.mimeType).name
                if (fileStore.delete(task.id, localName) && current != null) dao.deleteClaimed(task.id)
            }
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "ReturnCount")
        private suspend fun download(
            task: DownloadTaskEntity,
            networkSession: DownloadNetworkSession?,
            progressReporter: DownloadProgressReporter,
            allowRestart: Boolean,
        ): Unit =
            coroutineScope {
                val recoveredFile = fileStore.completedFile(task.id, task.url, task.mimeType)
                if (recoveredFile.isFile && recoveredFile.length() > 0L) {
                    complete(task, recoveredFile, recoveredFile.length())
                    return@coroutineScope
                }
                val streamMime = streamMimeType(task.url, task.mimeType)
                if (streamMime != null) {
                    downloadStream(task, streamMime, networkSession, progressReporter)
                    return@coroutineScope
                }
                val partialFile = fileStore.partialFile(task.id)
                val rangeStart = partialFile.length().coerceAtLeast(0L)
                val validatorFile = fileStore.partialValidatorFile(task.id)
                val validator =
                    storageOperation { validatorFile.takeIf(File::isFile)?.readText() }
                        ?.takeIf { it.isNotBlank() && !it.startsWith("W/") }
                if (rangeStart > 0L && validator == null) {
                    fileStore.clearPartial(task.id)
                    return@coroutineScope download(task, networkSession, progressReporter, allowRestart = false)
                }
                val request = buildDownloadRequest(task.url, rangeStart, validator)
                val requestClient = mediaRequestClient(client, task.url, decodeDownloadHeaders(task.requestHeaders))
                val call = requestClient.bindToNetworkCallFactory(networkSession).newCall(request)
                val stopWatcher =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            dao
                                .observeTransferStatus(task.id)
                                .distinctUntilChanged()
                                .first { it != DownloadStatus.DOWNLOADING.name }
                        } finally {
                            call.cancel()
                        }
                    }
                try {
                    requireActiveTask(task.id)
                    call.execute().use { response ->
                        if (rangeStart > 0L && response.requiresRestart(rangeStart)) {
                            fileStore.clearPartial(task.id)
                            if (allowRestart) {
                                return@coroutineScope download(
                                    task,
                                    networkSession,
                                    progressReporter,
                                    allowRestart = false,
                                )
                            }
                            throw DownloadFailureException(DownloadFailureReason.SERVER)
                        }
                        validateResponse(task, response)
                        saveResponse(task, response, rangeStart, progressReporter)
                    }
                } catch (failure: IOException) {
                    requireActiveTask(task.id)
                    throw failure
                } finally {
                    stopWatcher.cancel()
                }
            }

        private suspend fun downloadStream(
            task: DownloadTaskEntity,
            mimeType: String,
            networkSession: DownloadNetworkSession?,
            progressReporter: DownloadProgressReporter,
        ) = coroutineScope {
            val partial = fileStore.partialFile(task.id)
            val transfer =
                async {
                    StreamDownloadEngine(
                        context = appContext,
                        client = client,
                        fileStore = fileStore,
                        ioDispatcher = ioDispatcher,
                        networkSession = networkSession,
                    ).download(
                        id = task.id,
                        url = task.url,
                        mimeType = mimeType,
                        headers = decodeDownloadHeaders(task.requestHeaders),
                        output = partial,
                        videoHeight = task.videoHeight,
                    ) { bytes, total -> checkpoint(task, bytes, total, progressReporter) }
                }
            val stopWatcher =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    dao
                        .observeTransferStatus(task.id)
                        .distinctUntilChanged()
                        .first { it != DownloadStatus.DOWNLOADING.name }
                    transfer.cancel()
                }
            try {
                requireActiveTask(task.id)
                try {
                    transfer.await()
                } catch (cancellation: CancellationException) {
                    requireActiveTask(task.id)
                    throw cancellation
                }
                complete(task, partial, partial.length())
            } finally {
                stopWatcher.cancel()
            }
        }

        private suspend fun saveResponse(
            task: DownloadTaskEntity,
            response: Response,
            rangeStart: Long,
            progressReporter: DownloadProgressReporter,
        ) {
            val partialFile = fileStore.partialFile(task.id)
            val validatorFile = fileStore.partialValidatorFile(task.id)
            val append = rangeStart > 0L && response.code == HTTP_PARTIAL_CONTENT
            val initialBytes = if (append) rangeStart else 0L
            val totalBytes = response.totalBytes(initialBytes)
            val body = response.body
            if (!append) validatorFile.delete()
            val output = storageOperation { FileOutputStream(partialFile, append) }
            Closeable { storageOperation(output::close) }.use {
                requireSpaceFor(BUFFER_SIZE)
                storageOperation { response.downloadValidator()?.let(validatorFile::writeText) }
                checkpoint(task, initialBytes, totalBytes, progressReporter)
                val input = body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var downloadedBytes = initialBytes
                var lastCheckpointBytes = initialBytes
                var lastCheckpointAtMillis = SystemClock.elapsedRealtime()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    requireSpaceFor(read)
                    storageOperation { output.write(buffer, 0, read) }
                    downloadedBytes += read
                    val nowMillis = SystemClock.elapsedRealtime()
                    val deltaBytes = downloadedBytes - lastCheckpointBytes
                    if (shouldCheckpoint(deltaBytes, nowMillis - lastCheckpointAtMillis)) {
                        checkpoint(task, downloadedBytes, totalBytes, progressReporter)
                        lastCheckpointBytes = downloadedBytes
                        lastCheckpointAtMillis = nowMillis
                    }
                }
                storageOperation { output.fd.sync() }
                checkpoint(task, downloadedBytes, totalBytes, progressReporter)
                if (totalBytes != null && downloadedBytes < totalBytes) {
                    throw IOException("Download ended early")
                }
                if (downloadedBytes == 0L) {
                    requireActiveTask(task.id)
                    throw DownloadFailureException(DownloadFailureReason.SERVER)
                }
                complete(task, partialFile, downloadedBytes)
            }
        }

        private fun requireSpaceFor(byteCount: Int) {
            if (!fileStore.hasSpaceFor(byteCount)) {
                throw DownloadFailureException(DownloadFailureReason.INSUFFICIENT_STORAGE)
            }
        }

        @Suppress("ThrowsCount")
        private fun validateResponse(
            task: DownloadTaskEntity,
            response: Response,
        ) {
            when (response.code) {
                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> {
                    throw DownloadFailureException(DownloadFailureReason.ACCESS_RESTRICTED)
                }

                HTTP_RANGE_NOT_SATISFIABLE -> {
                    throw DownloadFailureException(DownloadFailureReason.SERVER)
                }
            }
            if (!response.isSuccessful) throw DownloadFailureException(DownloadFailureReason.SERVER)
            val contentType = response.body.contentType()?.toString()
            if (contentType?.startsWith("text/") == true || contentType?.contains("json") == true) {
                throw DownloadFailureException(DownloadFailureReason.UNSUPPORTED_FORMAT)
            }
            val hasVideoType = contentType?.startsWith("video/") == true || task.url.hasVideoFileExtension()
            val hasMp4Signature = !hasVideoType && hasMp4Signature(task.id, response)
            if (!hasVideoType && !hasMp4Signature) {
                throw DownloadFailureException(DownloadFailureReason.UNSUPPORTED_FORMAT)
            }
            if (response.code != HTTP_PARTIAL_CONTENT) {
                val prefix =
                    response
                        .peekBody(ERROR_DOCUMENT_PREFIX_BYTES)
                        .string()
                        .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                        .lowercase()
                val isTextPrefix = prefix.none { it < ' ' && it !in "\t\r\n" }
                if (isTextPrefix && ERROR_DOCUMENT_PREFIXES.any(prefix::startsWith)) {
                    throw DownloadFailureException(DownloadFailureReason.UNSUPPORTED_FORMAT)
                }
            }
        }

        private fun hasMp4Signature(
            id: String,
            response: Response,
        ): Boolean =
            if (response.contentRangeStart()?.let { it > 0L } == true) {
                storageOperation {
                    fileStore.partialFile(id).inputStream().use { input ->
                        val prefix = ByteArray(MP4_SIGNATURE_BYTES.toInt())
                        input.read(prefix)
                        prefix.hasMp4FileTypeBox()
                    }
                }
            } else {
                response.peekBody(MP4_SIGNATURE_BYTES).bytes().hasMp4FileTypeBox()
            }

        private suspend fun checkpoint(
            task: DownloadTaskEntity,
            downloadedBytes: Long,
            totalBytes: Long?,
            progressReporter: DownloadProgressReporter,
        ) {
            val updated = dao.updateProgress(task.id, downloadedBytes, totalBytes, System.currentTimeMillis())
            if (updated == 0) {
                requireActiveTask(task.id)
                return
            }
            progressReporter.onProgress(task.id, task.title, downloadedBytes, totalBytes)
        }

        private suspend fun complete(
            task: DownloadTaskEntity,
            partialFile: File,
            downloadedBytes: Long,
        ) {
            requireActiveTask(task.id)
            val completedFile = fileStore.completedFile(task.id, task.url, task.mimeType)
            if (partialFile != completedFile && !partialFile.renameTo(completedFile)) {
                throw DownloadFailureException(DownloadFailureReason.STORAGE)
            }
            val completed =
                dao.markCompleted(
                    id = task.id,
                    downloadedBytes = downloadedBytes,
                    localFileName = completedFile.name,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            if (completed == 0) {
                if (dao.getById(task.id) == null && partialFile != completedFile) {
                    completedFile.delete()
                }
                requireActiveTask(task.id)
                throw DownloadStoppedException(DownloadStopReason.OWNERSHIP_LOST)
            }
            fileStore.partialValidatorFile(task.id).delete()
            fileStore.clearStream(task.id)
            notifier.completed(task.id, task.title)
        }

        private suspend fun requireActiveTask(id: String): DownloadTaskEntity {
            val current = dao.getById(id)
            if (current?.status == DownloadStatus.DOWNLOADING.name && !current.deletePending) return current
            val reason =
                when {
                    current?.deletePending == true -> DownloadStopReason.CANCELLED
                    current?.status == DownloadStatus.PAUSED.name -> DownloadStopReason.PAUSED
                    current == null -> DownloadStopReason.CANCELLED
                    else -> DownloadStopReason.OWNERSHIP_LOST
                }
            throw DownloadStoppedException(reason)
        }

        private suspend fun markFailed(
            task: DownloadTaskEntity,
            reason: DownloadFailureReason,
        ) {
            val changed = dao.markFailed(task.id, reason.name, System.currentTimeMillis())
            if (changed == 1) notifier.failed(task.id, task.title, reason)
        }

        @Suppress("ReturnCount")
        private suspend fun handleNetworkFailure(task: DownloadTaskEntity): DownloadOutcome {
            val now = System.currentTimeMillis()
            if (dao.incrementNetworkRetry(task.id, MAX_NETWORK_RETRIES, now) == 1) {
                return DownloadOutcome.RETRYABLE_FAILURE
            }
            val failed =
                dao.markNetworkFailed(
                    id = task.id,
                    reason = DownloadFailureReason.NETWORK.name,
                    maxRetries = MAX_NETWORK_RETRIES,
                    updatedAtMillis = now,
                )
            if (failed == 1) {
                notifier.failed(task.id, task.title, DownloadFailureReason.NETWORK)
                return DownloadOutcome.PERMANENT_FAILURE
            }
            requireActiveTask(task.id)
            return DownloadOutcome.OWNERSHIP_LOST
        }
    }

private data class RunningDownload(
    val host: String,
    val job: Deferred<DownloadOutcome>,
)

internal data class ForegroundNotificationState(
    val title: String,
    val progress: Int?,
)

internal fun foregroundNotificationState(
    title: String,
    downloadedBytes: Long,
    totalBytes: Long?,
): ForegroundNotificationState =
    ForegroundNotificationState(
        title = title,
        progress = totalBytes?.takeIf { it > 0L }?.let { downloadProgress(downloadedBytes, it) },
    )

private enum class DownloadOutcome {
    COMPLETED,
    PAUSED,
    OWNERSHIP_LOST,
    CANCELLED,
    CONSTRAINT_LOST,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
}

private const val MAX_CAUSE_CHAIN_DEPTH = 16
private const val DEFAULT_CONSTRAINT_RECHECK_DELAY_MS = 500L

private class DownloadFailureException(
    val reason: DownloadFailureReason,
    cause: Throwable? = null,
) : Exception(cause)

private class DownloadStoppedException(
    val reason: DownloadStopReason,
) : Exception()

private enum class DownloadStopReason {
    PAUSED,
    OWNERSHIP_LOST,
    CANCELLED,
}

private fun DownloadStopReason.toOutcome(): DownloadOutcome =
    when (this) {
        DownloadStopReason.PAUSED -> DownloadOutcome.PAUSED
        DownloadStopReason.OWNERSHIP_LOST -> DownloadOutcome.OWNERSHIP_LOST
        DownloadStopReason.CANCELLED -> DownloadOutcome.CANCELLED
    }

private class RetryQueueException : IOException()

internal class ForegroundUpdateException(
    override val cause: IllegalStateException,
) : RuntimeException(cause)

internal fun selectNextDownload(
    queued: List<DownloadTaskEntity>,
    runningIds: Set<String>,
    activeHosts: Map<String, Int>,
): DownloadTaskEntity? =
    queued.firstOrNull { task ->
        task.id !in runningIds && activeHosts.getOrDefault(task.host, 0) < MAX_CONCURRENT_PER_HOST
    }

private fun Response.totalBytes(initialBytes: Long): Long? {
    val contentRangeTotal =
        header("Content-Range")
            ?.substringAfter('/')
            ?.takeIf { it != "*" }
            ?.toLongOrNull()
    if (contentRangeTotal != null) return contentRangeTotal
    return body.contentLength().takeIf { it >= 0L }?.plus(initialBytes)
}

private fun Response.contentRangeStart(): Long? =
    header("Content-Range")
        ?.substringAfter("bytes ", missingDelimiterValue = "")
        ?.substringBefore('-')
        ?.toLongOrNull()

private fun buildDownloadRequest(
    url: String,
    rangeStart: Long,
    validator: String?,
): Request =
    Request
        .Builder()
        .url(url)
        .apply {
            if (rangeStart > 0L) {
                header("Range", "bytes=$rangeStart-")
                header("If-Range", checkNotNull(validator))
            }
        }.build()

private inline fun <T> storageOperation(block: () -> T): T =
    try {
        block()
    } catch (failure: IOException) {
        throw DownloadFailureException(failure.storageFailureReason(), failure)
    }

private fun Response.downloadValidator(): String? =
    (header("ETag") ?: header("Last-Modified"))?.takeUnless {
        it.startsWith("W/")
    }

internal fun shouldCheckpoint(
    bytesSinceLastCheckpoint: Long,
    millisSinceLastCheckpoint: Long,
): Boolean =
    bytesSinceLastCheckpoint > 0L &&
        millisSinceLastCheckpoint >= CHECKPOINT_INTERVAL_MILLIS

private fun Response.requiresRestart(rangeStart: Long): Boolean =
    code == HTTP_RANGE_NOT_SATISFIABLE || (code == HTTP_PARTIAL_CONTENT && contentRangeStart() != rangeStart)

private val ERROR_DOCUMENT_PREFIXES = listOf("<!doctype html", "<html", "{", "[")
private const val ERROR_DOCUMENT_PREFIX_BYTES = 512L
private const val MAX_CONCURRENT_DOWNLOADS = 3
private const val MAX_CONCURRENT_PER_HOST = 2
private const val MAX_NETWORK_RETRIES = 2
private const val BUFFER_SIZE = 64 * 1024
private const val CHECKPOINT_INTERVAL_MILLIS = 500L
private const val HTTP_PARTIAL_CONTENT = 206
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
