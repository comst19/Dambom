package com.comst19.dambom.core.data.download

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import com.comst19.dambom.core.domain.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@HiltWorker
internal class DownloadQueueWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val dao: DownloadTaskDao,
        private val client: OkHttpClient,
        private val fileStore: DownloadFileStore,
        private val notifier: DownloadNotifier,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            withContext(ioDispatcher) {
                setForeground(notifier.foreground())
                dao.resetInterrupted(System.currentTimeMillis())
                try {
                    drainQueue()
                    Result.success()
                } catch (_: RetryQueueException) {
                    Result.retry()
                } finally {
                    withContext(NonCancellable) {
                        dao.resetInterrupted(System.currentTimeMillis())
                    }
                }
            }

        private suspend fun drainQueue() =
            coroutineScope {
                val running = linkedMapOf<String, RunningDownload>()
                var retryRequired = false
                while (true) {
                    val queued = dao.getQueued().toMutableList()
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
                        if (claimed == 0) continue
                        running[next.id] =
                            RunningDownload(
                                host = next.host,
                                job = async { downloadSafely(next) },
                            )
                    }
                    if (running.isEmpty()) {
                        if (retryRequired) throw RetryQueueException()
                        return@coroutineScope
                    }
                    val (completedId, outcome) =
                        select {
                            running.forEach { (id, download) ->
                                download.job.onAwait { id to it }
                            }
                        }
                    running.remove(completedId)
                    if (outcome == DownloadOutcome.RETRYABLE_FAILURE) retryRequired = true
                }
            }

        private suspend fun downloadSafely(task: DownloadTaskEntity): DownloadOutcome =
            try {
                download(task, allowRestart = true)
                DownloadOutcome.COMPLETED
            } catch (stopped: DownloadStoppedException) {
                when (stopped.reason) {
                    DownloadStopReason.PAUSED -> {
                        DownloadOutcome.PAUSED
                    }

                    DownloadStopReason.OWNERSHIP_LOST -> {
                        DownloadOutcome.OWNERSHIP_LOST
                    }

                    DownloadStopReason.CANCELLED -> {
                        fileStore.clearPartial(task.id)
                        DownloadOutcome.CANCELLED
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: IOException) {
                if (runAttemptCount >= MAX_NETWORK_RETRIES) {
                    markFailed(task, DownloadFailureReason.NETWORK)
                    DownloadOutcome.PERMANENT_FAILURE
                } else {
                    DownloadOutcome.RETRYABLE_FAILURE
                }
            } catch (failure: DownloadFailureException) {
                markFailed(task, failure.reason)
                DownloadOutcome.PERMANENT_FAILURE
            } catch (_: Exception) {
                markFailed(task, DownloadFailureReason.UNKNOWN)
                DownloadOutcome.PERMANENT_FAILURE
            }

        private suspend fun download(
            task: DownloadTaskEntity,
            allowRestart: Boolean,
        ) {
            val partialFile = fileStore.partialFile(task.id)
            val rangeStart = partialFile.length().coerceAtLeast(0L)
            val validatorFile = fileStore.partialValidatorFile(task.id)
            val validator = validatorFile.takeIf(File::isFile)?.readText()?.takeIf(String::isNotBlank)
            if (rangeStart > 0L && validator == null) {
                fileStore.clearPartial(task.id)
                return download(task, allowRestart = false)
            }
            val request =
                Request
                    .Builder()
                    .url(task.url)
                    .apply {
                        if (rangeStart > 0L) {
                            header("Range", "bytes=$rangeStart-")
                            header("If-Range", checkNotNull(validator))
                        }
                    }.build()
            client.newCall(request).execute().use { response ->
                if (response.code == HTTP_RANGE_NOT_SATISFIABLE && rangeStart > 0L && allowRestart) {
                    fileStore.clearPartial(task.id)
                    return download(task, allowRestart = false)
                }
                validateResponse(task, response)
                if (rangeStart > 0L && response.code == HTTP_PARTIAL_CONTENT && response.contentRangeStart() != rangeStart) {
                    fileStore.clearPartial(task.id)
                    if (allowRestart) return download(task, allowRestart = false)
                    throw DownloadFailureException(DownloadFailureReason.SERVER)
                }
                response.downloadValidator()?.let(validatorFile::writeText)
                    ?: if (response.code != HTTP_PARTIAL_CONTENT) validatorFile.delete() else Unit
                val append = rangeStart > 0L && response.code == HTTP_PARTIAL_CONTENT
                val initialBytes = if (append) rangeStart else 0L
                val totalBytes = response.totalBytes(initialBytes) ?: task.expectedBytes
                val body = response.body
                FileOutputStream(partialFile, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloadedBytes = initialBytes
                        var lastCheckpointBytes = initialBytes
                        var lastCheckpointAtMillis = SystemClock.elapsedRealtime()
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val nowMillis = SystemClock.elapsedRealtime()
                            if (shouldCheckpoint(downloadedBytes - lastCheckpointBytes, nowMillis - lastCheckpointAtMillis)) {
                                checkpoint(task.id, downloadedBytes, totalBytes)
                                lastCheckpointBytes = downloadedBytes
                                lastCheckpointAtMillis = nowMillis
                            }
                        }
                        output.fd.sync()
                        checkpoint(task.id, downloadedBytes, totalBytes)
                        if (totalBytes != null && downloadedBytes < totalBytes) throw IOException("Download ended early")
                        complete(task, partialFile, downloadedBytes)
                    }
                }
            }
        }

        private fun validateResponse(
            task: DownloadTaskEntity,
            response: Response,
        ) {
            when (response.code) {
                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> throw DownloadFailureException(DownloadFailureReason.ACCESS_RESTRICTED)
                HTTP_RANGE_NOT_SATISFIABLE -> throw DownloadFailureException(DownloadFailureReason.SERVER)
            }
            if (!response.isSuccessful) throw DownloadFailureException(DownloadFailureReason.SERVER)
            val contentType = response.body.contentType()?.toString()
            if (contentType?.startsWith("video/") != true && !task.url.hasVideoExtension()) {
                throw DownloadFailureException(DownloadFailureReason.UNSUPPORTED_FORMAT)
            }
        }

        private suspend fun checkpoint(
            id: String,
            downloadedBytes: Long,
            totalBytes: Long?,
        ) {
            dao.updateProgress(id, downloadedBytes, totalBytes, System.currentTimeMillis())
            val current = requireActiveTask(id)
            setForeground(notifier.foreground(current.title, downloadedBytes, totalBytes))
        }

        private suspend fun complete(
            task: DownloadTaskEntity,
            partialFile: File,
            downloadedBytes: Long,
        ) {
            requireActiveTask(task.id)
            val completedFile = fileStore.completedFile(task.id, task.url, task.mimeType)
            completedFile.delete()
            if (!partialFile.renameTo(completedFile)) throw DownloadFailureException(DownloadFailureReason.STORAGE)
            val completed =
                dao.markCompleted(
                    id = task.id,
                    downloadedBytes = downloadedBytes,
                    localFileName = completedFile.name,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            if (completed == 0) {
                completedFile.delete()
                throw DownloadStoppedException(DownloadStopReason.CANCELLED)
            }
            fileStore.partialValidatorFile(task.id).delete()
            notifier.completed(task.id, task.title)
        }

        private suspend fun requireActiveTask(id: String): DownloadTaskEntity {
            val current = dao.getById(id)
            if (current?.status == DownloadStatus.DOWNLOADING.name) return current
            val reason =
                when (current?.status) {
                    DownloadStatus.PAUSED.name -> DownloadStopReason.PAUSED
                    null -> DownloadStopReason.CANCELLED
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
    }

private data class RunningDownload(
    val host: String,
    val job: Deferred<DownloadOutcome>,
)

private enum class DownloadOutcome {
    COMPLETED,
    PAUSED,
    OWNERSHIP_LOST,
    CANCELLED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
}

private class DownloadFailureException(
    val reason: DownloadFailureReason,
) : Exception()

private class DownloadStoppedException(
    val reason: DownloadStopReason,
) : Exception()

private enum class DownloadStopReason {
    PAUSED,
    OWNERSHIP_LOST,
    CANCELLED,
}

private class RetryQueueException : IOException()

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

private fun Response.downloadValidator(): String? = header("ETag") ?: header("Last-Modified")

internal fun shouldCheckpoint(
    bytesSinceLastCheckpoint: Long,
    millisSinceLastCheckpoint: Long,
): Boolean =
    bytesSinceLastCheckpoint >= CHECKPOINT_BYTES &&
        millisSinceLastCheckpoint >= CHECKPOINT_INTERVAL_MILLIS

private fun String.hasVideoExtension(): Boolean =
    VIDEO_EXTENSIONS.any { extension -> substringBefore('?').endsWith(extension, ignoreCase = true) }

private val VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".mov", ".m4v")
private const val MAX_CONCURRENT_DOWNLOADS = 3
private const val MAX_CONCURRENT_PER_HOST = 2
private const val MAX_NETWORK_RETRIES = 2
private const val BUFFER_SIZE = 64 * 1024
private const val CHECKPOINT_BYTES = 1024 * 1024L
private const val CHECKPOINT_INTERVAL_MILLIS = 500L
private const val HTTP_PARTIAL_CONTENT = 206
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
