package com.comst19.dambom.core.data.download

import android.app.job.JobParameters
import android.app.job.JobService
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.await
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.database.download.DownloadTaskDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class DownloadQueueJobService : JobService() {
    @Inject
    internal lateinit var runner: DownloadQueueRunner

    @Inject
    internal lateinit var coordinator: UserInitiatedDownloadCoordinator

    @Inject
    internal lateinit var dao: DownloadTaskDao

    @Inject
    internal lateinit var notifier: DownloadNotifier

    @Inject
    internal lateinit var workManager: DownloadWorkManager

    @Inject
    @IoDispatcher
    internal lateinit var ioDispatcher: CoroutineDispatcher

    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private val activeExecution = AtomicReference<JobExecution?>()

    override fun onStartJob(params: JobParameters): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            jobFinished(params, false)
            return false
        }
        return startUserInitiatedJob(params)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startUserInitiatedJob(params: JobParameters): Boolean {
        val wifiOnlyDownloads = params.extras.getBoolean(EXTRA_WIFI_ONLY_DOWNLOADS, false)
        setNotification(
            params,
            DOWNLOAD_PROGRESS_NOTIFICATION_ID,
            notifier.progressNotification(),
            JOB_END_NOTIFICATION_POLICY_REMOVE,
        )
        val token = Any()
        val initialBinding = params.network?.asDownloadNetworkBinding(connectivityManager)
        val execution =
            JobExecution(
                token = token,
                networkSession = DownloadNetworkSession(initialBinding),
            )
        activeExecution.getAndSet(execution)?.let { previous ->
            previous.stopped.set(true)
            coordinator.onJobStopped(previous.token)
            previous.job?.cancel()
        }
        coordinator.onJobStarting(token, wifiOnlyDownloads)
        Log.i(
            UIDT_LOG_TAG,
            "onStartJob jobId=${params.jobId} wifiOnly=$wifiOnlyDownloads hasNetwork=${params.network != null}",
        )
        val progressReporter = UidtProgressReporter(params, execution)
        execution.job =
            serviceScope.launch {
                runUidtJobExecution(
                    token = token,
                    wifiOnlyDownloads = wifiOnlyDownloads,
                    cancelLegacyWork = { workManager.cancelUniqueWork(DOWNLOAD_WORK_NAME).await() },
                    isStopped = { execution.stopped.get() },
                    coordinator = coordinator,
                    runQueue = {
                        runner.run(
                            networkSession = execution.networkSession,
                            isConstraintSatisfied = {
                                !execution.stopped.get() &&
                                    execution.networkSession
                                        .currentBinding()
                                        ?.isAvailableFor(wifiOnlyDownloads) == true
                            },
                            progressReporter = progressReporter,
                        )
                    },
                    hasPendingWork = { dao.countSchedulable() > 0 },
                    onJobFinished = { needsReschedule ->
                        finishJobExecution(params, execution, needsReschedule)
                    },
                    onFailure = { error ->
                        Log.e(UIDT_LOG_TAG, "UIDT job failed jobId=${params.jobId}", error)
                    },
                )
            }
        return true
    }

    private fun finishJobExecution(
        params: JobParameters,
        execution: JobExecution,
        needsReschedule: Boolean,
    ) {
        if (execution.stopped.compareAndSet(false, true)) {
            activeExecution.compareAndSet(execution, null)
            Log.i(
                UIDT_LOG_TAG,
                "jobFinished jobId=${params.jobId} needsReschedule=$needsReschedule",
            )
            jobFinished(params, needsReschedule)
        }
    }

    override fun onNetworkChanged(params: JobParameters) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val nextBinding = params.network?.asDownloadNetworkBinding(connectivityManager)
        val changed =
            activeExecution.get()?.networkSession?.update(nextBinding) { evict ->
                serviceScope.launch { evict() }
            } == true
        Log.i(
            UIDT_LOG_TAG,
            "onNetworkChanged jobId=${params.jobId} hasNetwork=${params.network != null} changed=$changed",
        )
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val execution = activeExecution.getAndSet(null)
        if (execution != null) {
            execution.stopped.set(true)
            coordinator.onJobStopped(execution.token)
            execution.job?.cancel()
        }
        val stopReason =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.stopReason
            } else {
                0
            }
        val reschedule =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                shouldRescheduleUidtOnStop(stopReason)
        Log.i(
            UIDT_LOG_TAG,
            "onStopJob jobId=${params.jobId} stopReason=$stopReason reschedule=$reschedule",
        )
        return reschedule
    }

    override fun onDestroy() {
        activeExecution.getAndSet(null)?.let { execution ->
            execution.stopped.set(true)
            coordinator.onJobStopped(execution.token)
            execution.job?.cancel()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private inner class UidtProgressReporter(
        private val params: JobParameters,
        private val execution: JobExecution,
    ) : DownloadProgressReporter {
        private val updateMutex = Mutex()
        private var notificationState: ForegroundNotificationState? = null
        private var lastEstimatedBytes = 0L
        private val lastBytesByTask = mutableMapOf<String, Long>()
        private var transferredBytes = 0L

        override suspend fun onProgress(
            taskId: String,
            title: String,
            downloadedBytes: Long,
            totalBytes: Long?,
        ) {
            updateMutex.withLock {
                if (execution.stopped.get()) return
                val nextState = foregroundNotificationState(title, downloadedBytes, totalBytes)
                if (nextState != notificationState) {
                    runCatching {
                        setNotification(
                            params,
                            DOWNLOAD_PROGRESS_NOTIFICATION_ID,
                            notifier.progressNotification(title, downloadedBytes, totalBytes),
                            JOB_END_NOTIFICATION_POLICY_REMOVE,
                        )
                    }
                    notificationState = nextState
                }
                if (totalBytes != null && totalBytes > lastEstimatedBytes) {
                    lastEstimatedBytes = totalBytes.coerceAtLeast(MIN_NETWORK_CHUNK_BYTES)
                    runCatching { updateEstimatedNetworkBytes(params, lastEstimatedBytes, 0L) }
                }
                val previousBytes = lastBytesByTask.put(taskId, downloadedBytes)
                if (previousBytes != null && downloadedBytes > previousBytes) {
                    transferredBytes += downloadedBytes - previousBytes
                    runCatching { updateTransferredNetworkBytes(params, transferredBytes, 0L) }
                }
            }
        }
    }

    private class JobExecution(
        val token: Any,
        val networkSession: DownloadNetworkSession,
        val stopped: AtomicBoolean = AtomicBoolean(false),
        @Volatile var job: Job? = null,
    )
}

@Suppress("LongParameterList")
internal suspend fun runUidtJobExecution(
    token: Any,
    wifiOnlyDownloads: Boolean,
    cancelLegacyWork: suspend () -> Unit,
    isStopped: () -> Boolean,
    coordinator: UserInitiatedDownloadCoordinator,
    runQueue: suspend () -> DownloadQueueRunResult,
    hasPendingWork: suspend () -> Boolean,
    onJobFinished: (needsReschedule: Boolean) -> Unit,
    onFailure: (Throwable) -> Unit = {},
) {
    suspendRunCatching {
        suspendRunCatching { cancelLegacyWork() }
        currentCoroutineContext().ensureActive()
        if (isStopped()) return@suspendRunCatching
        coordinator.executeJob(
            token = token,
            wifiOnlyDownloads = wifiOnlyDownloads,
            runQueue = runQueue,
            hasPendingWork = hasPendingWork,
            onJobFinished = onJobFinished,
        )
    }.onFailure { error ->
        onFailure(error)
        coordinator.onJobFailed(token, onJobFinished)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
internal fun shouldRescheduleUidtOnStop(stopReason: Int): Boolean =
    stopReason != JobParameters.STOP_REASON_CANCELLED_BY_APP &&
        stopReason != JobParameters.STOP_REASON_USER
