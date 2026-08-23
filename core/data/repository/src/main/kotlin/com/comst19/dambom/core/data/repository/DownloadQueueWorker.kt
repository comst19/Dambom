package com.comst19.dambom.core.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
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
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            withContext(ioDispatcher) {
                setForeground(createForegroundInfo())
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
                    if (running.isEmpty()) return@coroutineScope
                    val completedId = select { running.forEach { (id, download) -> download.job.onAwait { id } } }
                    running.remove(completedId)
                }
            }

        private suspend fun downloadSafely(task: DownloadTaskEntity) {
            try {
                download(task, allowRestart = true)
            } catch (_: PausedDownloadException) {
                return
            } catch (_: CancelledDownloadException) {
                return
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: IOException) {
                if (runAttemptCount >= MAX_NETWORK_RETRIES) {
                    dao.markFailed(task.id, DownloadFailureReason.NETWORK.name, System.currentTimeMillis())
                } else {
                    throw RetryQueueException()
                }
            } catch (failure: DownloadFailureException) {
                dao.markFailed(task.id, failure.reason.name, System.currentTimeMillis())
            } catch (_: Exception) {
                dao.markFailed(task.id, DownloadFailureReason.UNKNOWN.name, System.currentTimeMillis())
            }
        }

        private suspend fun download(
            task: DownloadTaskEntity,
            allowRestart: Boolean,
        ) {
            val partialFile = fileStore.partialFile(task.id)
            val rangeStart = partialFile.length().coerceAtLeast(0L)
            val request =
                Request
                    .Builder()
                    .url(task.url)
                    .apply { if (rangeStart > 0L) header("Range", "bytes=$rangeStart-") }
                    .build()
            client.newCall(request).execute().use { response ->
                if (response.code == HTTP_RANGE_NOT_SATISFIABLE && rangeStart > 0L && allowRestart) {
                    partialFile.delete()
                    return download(task, allowRestart = false)
                }
                validateResponse(task, response)
                val append = rangeStart > 0L && response.code == HTTP_PARTIAL_CONTENT
                val initialBytes = if (append) rangeStart else 0L
                val totalBytes = response.totalBytes(initialBytes) ?: task.expectedBytes
                val body = response.body
                FileOutputStream(partialFile, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloadedBytes = initialBytes
                        var lastCheckpointBytes = initialBytes
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (downloadedBytes - lastCheckpointBytes >= CHECKPOINT_BYTES) {
                                checkpoint(task.id, downloadedBytes, totalBytes)
                                lastCheckpointBytes = downloadedBytes
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
            when (dao.getById(id)?.status) {
                DownloadStatus.PAUSED.name -> throw PausedDownloadException()
                null -> throw CancelledDownloadException()
            }
        }

        private suspend fun complete(
            task: DownloadTaskEntity,
            partialFile: File,
            downloadedBytes: Long,
        ) {
            val current = dao.getById(task.id) ?: throw CancelledDownloadException()
            if (current.status == DownloadStatus.PAUSED.name) throw PausedDownloadException()
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
                throw CancelledDownloadException()
            }
        }

        private fun createForegroundInfo(): ForegroundInfo {
            createNotificationChannel()
            val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            val contentIntent =
                launchIntent?.let {
                    PendingIntent.getActivity(
                        applicationContext,
                        0,
                        it,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }
            val notification =
                NotificationCompat
                    .Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(applicationContext.getString(R.string.download_notification_title))
                    .setContentText(applicationContext.getString(R.string.download_notification_description))
                    .setContentIntent(contentIntent)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(0, 0, true)
                    .build()
            val serviceType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            return ForegroundInfo(NOTIFICATION_ID, notification, serviceType)
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    applicationContext.getString(R.string.download_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            manager.createNotificationChannel(channel)
        }
    }

private data class RunningDownload(
    val host: String,
    val job: Deferred<Unit>,
)

private class DownloadFailureException(
    val reason: DownloadFailureReason,
) : Exception()

private class PausedDownloadException : Exception()

private class CancelledDownloadException : Exception()

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

private fun String.hasVideoExtension(): Boolean =
    VIDEO_EXTENSIONS.any { extension -> substringBefore('?').endsWith(extension, ignoreCase = true) }

private val VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".mov", ".m4v")
private const val MAX_CONCURRENT_DOWNLOADS = 3
private const val MAX_CONCURRENT_PER_HOST = 2
private const val MAX_NETWORK_RETRIES = 2
private const val BUFFER_SIZE = 64 * 1024
private const val CHECKPOINT_BYTES = 256 * 1024L
private const val HTTP_PARTIAL_CONTENT = 206
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val NOTIFICATION_CHANNEL_ID = "downloads"
private const val NOTIFICATION_ID = 1001
