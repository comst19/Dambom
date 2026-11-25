package com.comst19.dambom.core.data.download

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

internal interface UidtJobBackend {
    fun schedule(
        wifiOnlyDownloads: Boolean,
        estimatedDownloadBytes: Long,
    ): Boolean

    fun hasPendingJob(wifiOnlyDownloads: Boolean): Boolean

    fun cancel()
}

@Singleton
internal class JobSchedulerUidtBackend
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UidtJobBackend {
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun schedule(
            wifiOnlyDownloads: Boolean,
            estimatedDownloadBytes: Long,
        ): Boolean {
            val jobScheduler = context.getSystemService(JobScheduler::class.java) ?: return false
            val jobInfo = buildUidtJobInfo(context, wifiOnlyDownloads, estimatedDownloadBytes)
            val result = jobScheduler.schedule(jobInfo)
            val succeeded = result == JobScheduler.RESULT_SUCCESS
            Log.i(
                UIDT_LOG_TAG,
                "schedule wifiOnly=$wifiOnlyDownloads estimatedBytes=$estimatedDownloadBytes result=$result",
            )
            return succeeded
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun hasPendingJob(wifiOnlyDownloads: Boolean): Boolean {
            val job = context.getSystemService(JobScheduler::class.java)?.getPendingJob(DOWNLOAD_UIDT_JOB_ID)
            return job?.extras?.getBoolean(EXTRA_WIFI_ONLY_DOWNLOADS, false) == wifiOnlyDownloads
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun cancel() {
            val jobScheduler = context.getSystemService(JobScheduler::class.java) ?: return
            jobScheduler.cancel(DOWNLOAD_UIDT_JOB_ID)
            Log.i(UIDT_LOG_TAG, "cancel jobId=$DOWNLOAD_UIDT_JOB_ID")
        }
    }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal fun buildUidtJobInfo(
    context: Context,
    wifiOnlyDownloads: Boolean,
    estimatedDownloadBytes: Long,
): JobInfo {
    val extras =
        PersistableBundle().apply {
            putBoolean(EXTRA_WIFI_ONLY_DOWNLOADS, wifiOnlyDownloads)
        }
    val networkType =
        if (wifiOnlyDownloads) {
            JobInfo.NETWORK_TYPE_UNMETERED
        } else {
            JobInfo.NETWORK_TYPE_ANY
        }
    val clampedEstimate =
        estimatedDownloadBytes
            .takeIf { it > 0L }
            ?.coerceAtLeast(MIN_NETWORK_CHUNK_BYTES)
            ?: DEFAULT_ESTIMATED_DOWNLOAD_BYTES
    return JobInfo
        .Builder(
            DOWNLOAD_UIDT_JOB_ID,
            ComponentName(context, DownloadQueueJobService::class.java),
        ).setUserInitiated(true)
        .setRequiredNetworkType(networkType)
        .setRequiresStorageNotLow(true)
        .setPersisted(true)
        .setBackoffCriteria(MIN_BACKOFF_MILLIS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
        .setEstimatedNetworkBytes(clampedEstimate, 0L)
        .setMinimumNetworkChunkBytes(MIN_NETWORK_CHUNK_BYTES)
        .setExtras(extras)
        .build()
}

@Singleton
internal class UserInitiatedDownloadCoordinator
    @Inject
    constructor() {
        private val stateMutex = Mutex()
        private val activeLock = Any()
        private var activeJob: ActiveUidtJob? = null
        private var pendingRecoverySchedule = false
        private var jobFinishedWithoutReschedule = false

        fun onJobStarting(
            token: Any,
            wifiOnlyDownloads: Boolean,
        ) {
            synchronized(activeLock) {
                activeJob = ActiveUidtJob(token = token, wifiOnlyDownloads = wifiOnlyDownloads)
                pendingRecoverySchedule = false
                jobFinishedWithoutReschedule = false
            }
        }

        fun onJobStopped(token: Any) {
            synchronized(activeLock) {
                val current = activeJob
                if (current?.token === token) {
                    current.stopped = true
                }
                jobFinishedWithoutReschedule = false
            }
        }

        fun hasPendingRecoverySchedule(): Boolean = synchronized(activeLock) { pendingRecoverySchedule }

        suspend fun schedule(
            wifiOnlyDownloads: Boolean,
            estimatedDownloadBytes: Long,
            backend: UidtJobBackend,
        ): Boolean = scheduleIfNeeded(wifiOnlyDownloads, estimatedDownloadBytes, backend)

        suspend fun ensureScheduled(
            wifiOnlyDownloads: Boolean,
            estimatedDownloadBytes: Long,
            backend: UidtJobBackend,
        ): Boolean = scheduleIfNeeded(wifiOnlyDownloads, estimatedDownloadBytes, backend)

        private suspend fun scheduleIfNeeded(
            wifiOnlyDownloads: Boolean,
            estimatedDownloadBytes: Long,
            backend: UidtJobBackend,
        ): Boolean =
            stateMutex.withLock {
                val (handledByActive, allowPendingReuse) =
                    synchronized(activeLock) {
                        val current = activeJob
                        if (current != null && !current.stopped && current.wifiOnlyDownloads == wifiOnlyDownloads) {
                            current.recheckRequested = true
                            pendingRecoverySchedule = false
                            true to false
                        } else {
                            false to !jobFinishedWithoutReschedule
                        }
                    }
                if (handledByActive) return@withLock true
                if (allowPendingReuse && backend.hasPendingJob(wifiOnlyDownloads)) {
                    synchronized(activeLock) { pendingRecoverySchedule = false }
                    return@withLock true
                }
                val scheduled = backend.schedule(wifiOnlyDownloads, estimatedDownloadBytes)
                synchronized(activeLock) {
                    pendingRecoverySchedule = !scheduled
                    if (scheduled) jobFinishedWithoutReschedule = false
                }
                scheduled
            }

        suspend fun reschedule(
            wifiOnlyDownloads: Boolean,
            estimatedDownloadBytes: Long,
            backend: UidtJobBackend,
        ): Boolean =
            stateMutex.withLock {
                synchronized(activeLock) {
                    activeJob?.stopped = true
                    jobFinishedWithoutReschedule = false
                }
                backend.cancel()
                val scheduled = backend.schedule(wifiOnlyDownloads, estimatedDownloadBytes)
                synchronized(activeLock) {
                    pendingRecoverySchedule = !scheduled
                    if (scheduled) jobFinishedWithoutReschedule = false
                }
                scheduled
            }

        @Suppress("LongParameterList")
        suspend fun executeJob(
            token: Any,
            wifiOnlyDownloads: Boolean,
            runQueue: suspend () -> DownloadQueueRunResult,
            hasPendingWork: suspend () -> Boolean,
            onJobFinished: (needsReschedule: Boolean) -> Unit,
            onBeforeFinishCheck: (suspend () -> Unit)? = null,
        ) {
            synchronized(activeLock) {
                if (activeJob?.token !== token) {
                    activeJob = ActiveUidtJob(token = token, wifiOnlyDownloads = wifiOnlyDownloads)
                }
                pendingRecoverySchedule = false
                jobFinishedWithoutReschedule = false
            }
            try {
                while (true) {
                    val result = runQueue()
                    if (result == DownloadQueueRunResult.RETRY) {
                        stateMutex.withLock {
                            val shouldFinish =
                                synchronized(activeLock) {
                                    val current = activeJob
                                    if (current?.token === token) {
                                        activeJob = null
                                        jobFinishedWithoutReschedule = false
                                        !current.stopped
                                    } else {
                                        false
                                    }
                                }
                            if (shouldFinish) onJobFinished(true)
                        }
                        return
                    }
                    onBeforeFinishCheck?.invoke()
                    val shouldContinue = decideContinueOrFinish(token, hasPendingWork, onJobFinished)
                    if (!shouldContinue) return
                }
            } catch (cancellation: CancellationException) {
                synchronized(activeLock) {
                    if (activeJob?.token === token) activeJob = null
                }
                throw cancellation
            }
        }

        @Suppress("ReturnCount")
        private suspend fun decideContinueOrFinish(
            token: Any,
            hasPendingWork: suspend () -> Boolean,
            onJobFinished: (needsReschedule: Boolean) -> Unit,
        ): Boolean =
            stateMutex.withLock {
                when (consumeRecheckState(token)) {
                    RecheckState.STOPPED_OR_REPLACED -> return@withLock false
                    RecheckState.RECHECK_REQUESTED -> return@withLock true
                    RecheckState.IDLE -> Unit
                }
                if (hasPendingWork()) return@withLock true
                when (consumeRecheckState(token)) {
                    RecheckState.STOPPED_OR_REPLACED -> return@withLock false
                    RecheckState.RECHECK_REQUESTED -> return@withLock true
                    RecheckState.IDLE -> Unit
                }
                onJobFinished(false)
                synchronized(activeLock) {
                    if (activeJob?.token === token) {
                        activeJob = null
                        jobFinishedWithoutReschedule = true
                    }
                }
                false
            }

        private fun consumeRecheckState(token: Any): RecheckState =
            synchronized(activeLock) {
                val current = activeJob
                when {
                    current?.token !== token || current.stopped -> {
                        RecheckState.STOPPED_OR_REPLACED
                    }

                    current.recheckRequested -> {
                        current.recheckRequested = false
                        RecheckState.RECHECK_REQUESTED
                    }

                    else -> {
                        RecheckState.IDLE
                    }
                }
            }
    }

private data class ActiveUidtJob(
    val token: Any,
    val wifiOnlyDownloads: Boolean,
    var recheckRequested: Boolean = false,
    var stopped: Boolean = false,
)

private enum class RecheckState {
    IDLE,
    RECHECK_REQUESTED,
    STOPPED_OR_REPLACED,
}

internal const val DOWNLOAD_UIDT_JOB_ID = 42_100
internal const val EXTRA_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
internal const val DEFAULT_ESTIMATED_DOWNLOAD_BYTES = 10L * 1024L * 1024L
internal const val MIN_NETWORK_CHUNK_BYTES = 64L * 1024L
private const val MIN_BACKOFF_MILLIS = 10_000L
internal const val UIDT_LOG_TAG = "DambomDownloadJob"
