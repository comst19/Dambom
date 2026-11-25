package com.comst19.dambom.core.data.download

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import androidx.annotation.RequiresApi
import com.comst19.dambom.core.common.util.suspendRunCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
        private val ownership = UidtJobOwnership()

        fun onJobStarting(
            token: Any,
            wifiOnlyDownloads: Boolean,
        ) {
            ownership.start(token, wifiOnlyDownloads)
        }

        fun onJobStopped(token: Any) {
            ownership.stop(token)
        }

        suspend fun onJobFailed(
            token: Any,
            onJobFinished: (needsReschedule: Boolean) -> Unit,
        ) {
            withContext(NonCancellable) {
                stateMutex.withLock {
                    val termination = ownership.finishFailed(token)
                    if (termination.shouldNotify) {
                        onJobFinished(termination.needsReschedule)
                    }
                }
            }
        }

        fun hasPendingRecoverySchedule(): Boolean = ownership.hasPendingRecovery

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
                val (handledByActive, allowPendingReuse) = ownership.tryHandleByActive(wifiOnlyDownloads)
                if (handledByActive) return@withLock true
                if (allowPendingReuse && backend.hasPendingJob(wifiOnlyDownloads)) {
                    ownership.recordScheduleResult(scheduled = true)
                    return@withLock true
                }
                val scheduled = backend.schedule(wifiOnlyDownloads, estimatedDownloadBytes)
                ownership.recordScheduleResult(scheduled)
                scheduled
            }

        suspend fun reschedule(
            wifiOnlyDownloads: Boolean,
            estimatedDownloadBytes: Long,
            backend: UidtJobBackend,
        ): Boolean =
            stateMutex.withLock {
                ownership.stopActiveForReschedule()
                backend.cancel()
                val scheduled = backend.schedule(wifiOnlyDownloads, estimatedDownloadBytes)
                ownership.recordScheduleResult(scheduled)
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
            currentCoroutineContext().ensureActive()
            if (!ownership.claim(token, wifiOnlyDownloads)) return
            var terminated = false
            try {
                suspendRunCatching {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (!ownership.isActive(token)) return@suspendRunCatching
                        val result = runQueue()
                        if (result == DownloadQueueRunResult.RETRY) {
                            stateMutex.withLock {
                                if (ownership.finish(token, markFinishedWithoutReschedule = false)) {
                                    onJobFinished(true)
                                }
                            }
                            return@suspendRunCatching
                        }
                        onBeforeFinishCheck?.invoke()
                        val shouldContinue = decideContinueOrFinish(token, hasPendingWork, onJobFinished)
                        if (!shouldContinue) return@suspendRunCatching
                    }
                }.onSuccess {
                    terminated = true
                }.onFailure { error ->
                    onJobFailed(token, onJobFinished)
                    terminated = true
                    throw error
                }
            } finally {
                if (!terminated) {
                    ownership.stop(token)
                }
            }
        }

        @Suppress("ReturnCount")
        private suspend fun decideContinueOrFinish(
            token: Any,
            hasPendingWork: suspend () -> Boolean,
            onJobFinished: (needsReschedule: Boolean) -> Unit,
        ): Boolean =
            stateMutex.withLock {
                when (ownership.consumeRecheck(token)) {
                    RecheckState.STOPPED_OR_REPLACED -> return@withLock false
                    RecheckState.RECHECK_REQUESTED -> return@withLock true
                    RecheckState.IDLE -> Unit
                }
                if (hasPendingWork()) return@withLock true
                when (ownership.consumeRecheck(token)) {
                    RecheckState.STOPPED_OR_REPLACED -> return@withLock false
                    RecheckState.RECHECK_REQUESTED -> return@withLock true
                    RecheckState.IDLE -> Unit
                }
                if (ownership.finish(token, markFinishedWithoutReschedule = true)) {
                    onJobFinished(false)
                }
                false
            }
    }

private class UidtJobOwnership {
    private val lock = Any()
    private var activeJob: ActiveUidtJob? = null
    private var pendingRecoverySchedule = false
    private var jobFinishedWithoutReschedule = false

    val hasPendingRecovery: Boolean
        get() = synchronized(lock) { pendingRecoverySchedule }

    fun start(
        token: Any,
        wifiOnlyDownloads: Boolean,
    ) {
        synchronized(lock) {
            activeJob?.let { previous ->
                if (previous.token !== token) {
                    previous.stopped = true
                }
            }
            activeJob = ActiveUidtJob(token = token, wifiOnlyDownloads = wifiOnlyDownloads)
            pendingRecoverySchedule = false
            jobFinishedWithoutReschedule = false
        }
    }

    fun stop(token: Any) {
        synchronized(lock) {
            val current = activeJob
            if (current?.token === token) {
                current.stopped = true
                activeJob = null
                jobFinishedWithoutReschedule = false
            }
        }
    }

    fun stopActiveForReschedule() {
        synchronized(lock) {
            activeJob?.stopped = true
            activeJob = null
            jobFinishedWithoutReschedule = false
        }
    }

    fun claim(
        token: Any,
        wifiOnlyDownloads: Boolean,
    ): Boolean =
        synchronized(lock) {
            val current = activeJob
            val matchesRegistered =
                current != null &&
                    current.token === token &&
                    current.wifiOnlyDownloads == wifiOnlyDownloads
            if (matchesRegistered && !current.stopped && !current.claimed) {
                current.claimed = true
                pendingRecoverySchedule = false
                jobFinishedWithoutReschedule = false
                true
            } else {
                false
            }
        }

    fun isActive(token: Any): Boolean =
        synchronized(lock) {
            val current = activeJob
            current?.token === token && !current.stopped
        }

    fun finish(
        token: Any,
        markFinishedWithoutReschedule: Boolean,
    ): Boolean =
        synchronized(lock) {
            val current = activeJob
            if (current?.token === token) {
                activeJob = null
                jobFinishedWithoutReschedule = markFinishedWithoutReschedule
                !current.stopped
            } else {
                false
            }
        }

    fun finishFailed(token: Any): FailureTermination =
        synchronized(lock) {
            val current = activeJob
            if (current?.token === token) {
                activeJob = null
                val needsReschedule = current.recheckRequested
                jobFinishedWithoutReschedule = !needsReschedule
                pendingRecoverySchedule = false
                FailureTermination(
                    shouldNotify = !current.stopped,
                    needsReschedule = needsReschedule,
                )
            } else {
                FailureTermination(shouldNotify = false, needsReschedule = false)
            }
        }

    fun consumeRecheck(token: Any): RecheckState =
        synchronized(lock) {
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

    fun tryHandleByActive(wifiOnlyDownloads: Boolean): Pair<Boolean, Boolean> =
        synchronized(lock) {
            val current = activeJob
            if (current != null && !current.stopped && current.wifiOnlyDownloads == wifiOnlyDownloads) {
                current.recheckRequested = true
                pendingRecoverySchedule = false
                true to false
            } else {
                false to !jobFinishedWithoutReschedule
            }
        }

    fun recordScheduleResult(scheduled: Boolean) {
        synchronized(lock) {
            pendingRecoverySchedule = !scheduled
            if (scheduled) jobFinishedWithoutReschedule = false
        }
    }
}

private data class ActiveUidtJob(
    val token: Any,
    val wifiOnlyDownloads: Boolean,
    var claimed: Boolean = false,
    var recheckRequested: Boolean = false,
    var stopped: Boolean = false,
)

private data class FailureTermination(
    val shouldNotify: Boolean,
    val needsReschedule: Boolean,
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
