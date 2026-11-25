package com.comst19.dambom.core.data.download

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.await
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal interface DownloadWorkScheduler {
    suspend fun schedule()

    suspend fun ensureScheduled()

    suspend fun reschedule()
}

@Singleton
internal class AdaptiveDownloadWorkScheduler
    internal constructor(
        private val settingsRepository: SettingsRepository,
        private val dao: DownloadTaskDao,
        private val workManagerScheduler: WorkManagerDownloadScheduler,
        private val uidtCoordinator: UserInitiatedDownloadCoordinator,
        private val uidtBackend: UidtJobBackend,
        private val sdkInt: Int,
    ) : DownloadWorkScheduler {
        @Inject
        constructor(
            settingsRepository: SettingsRepository,
            dao: DownloadTaskDao,
            workManagerScheduler: WorkManagerDownloadScheduler,
            uidtCoordinator: UserInitiatedDownloadCoordinator,
            uidtBackend: UidtJobBackend,
        ) : this(
            settingsRepository = settingsRepository,
            dao = dao,
            workManagerScheduler = workManagerScheduler,
            uidtCoordinator = uidtCoordinator,
            uidtBackend = uidtBackend,
            sdkInt = Build.VERSION.SDK_INT,
        )

        override suspend fun schedule() {
            if (useUserInitiatedTransfers()) {
                workManagerScheduler.cancel()
                uidtCoordinator.schedule(
                    wifiOnlyDownloads = wifiOnlyDownloads(),
                    estimatedDownloadBytes = dao.estimateRemainingDownloadBytes(),
                    backend = uidtBackend,
                )
            } else {
                workManagerScheduler.schedule()
            }
        }

        override suspend fun ensureScheduled() {
            if (useUserInitiatedTransfers()) {
                workManagerScheduler.cancel()
                uidtCoordinator.ensureScheduled(
                    wifiOnlyDownloads = wifiOnlyDownloads(),
                    estimatedDownloadBytes = dao.estimateRemainingDownloadBytes(),
                    backend = uidtBackend,
                )
            } else {
                workManagerScheduler.ensureScheduled()
            }
        }

        override suspend fun reschedule() {
            if (useUserInitiatedTransfers()) {
                workManagerScheduler.cancel()
                uidtCoordinator.reschedule(
                    wifiOnlyDownloads = wifiOnlyDownloads(),
                    estimatedDownloadBytes = dao.estimateRemainingDownloadBytes(),
                    backend = uidtBackend,
                )
            } else {
                workManagerScheduler.reschedule()
            }
        }

        private fun useUserInitiatedTransfers(): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        private suspend fun wifiOnlyDownloads(): Boolean = settingsRepository.settings.first().wifiOnlyDownloads
    }

@Singleton
internal class WorkManagerDownloadScheduler
    @Inject
    internal constructor(
        private val settingsRepository: SettingsRepository,
        private val workManager: DownloadWorkManager,
    ) : DownloadWorkScheduler {
        override suspend fun schedule() {
            enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
        }

        override suspend fun ensureScheduled() {
            enqueue(ExistingWorkPolicy.KEEP)
        }

        override suspend fun reschedule() {
            cancel()
            enqueue(ExistingWorkPolicy.REPLACE)
        }

        internal suspend fun cancel() {
            workManager.cancelUniqueWork(DOWNLOAD_WORK_NAME).await()
        }

        private suspend fun enqueue(existingWorkPolicy: ExistingWorkPolicy) {
            val wifiOnlyDownloads = settingsRepository.settings.first().wifiOnlyDownloads
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(requiredNetworkType(wifiOnlyDownloads))
                    .setRequiresStorageNotLow(true)
                    .build()
            val request =
                OneTimeWorkRequestBuilder<DownloadQueueWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        MIN_BACKOFF_SECONDS,
                        TimeUnit.SECONDS,
                    ).addTag(DOWNLOAD_WORK_TAG)
                    .build()
            workManager
                .enqueueUniqueWork(
                    DOWNLOAD_WORK_NAME,
                    existingWorkPolicy,
                    request,
                ).await()
        }
    }

internal interface DownloadWorkManager {
    fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        request: androidx.work.OneTimeWorkRequest,
    ): Operation

    fun cancelUniqueWork(uniqueWorkName: String): Operation
}

@Singleton
internal class ContextDownloadWorkManager
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DownloadWorkManager {
        private val delegate by lazy { WorkManager.getInstance(context) }

        override fun enqueueUniqueWork(
            uniqueWorkName: String,
            existingWorkPolicy: ExistingWorkPolicy,
            request: androidx.work.OneTimeWorkRequest,
        ): Operation = delegate.enqueueUniqueWork(uniqueWorkName, existingWorkPolicy, request)

        override fun cancelUniqueWork(uniqueWorkName: String): Operation = delegate.cancelUniqueWork(uniqueWorkName)
    }

internal fun requiredNetworkType(wifiOnlyDownloads: Boolean): NetworkType =
    if (wifiOnlyDownloads) NetworkType.UNMETERED else NetworkType.CONNECTED

internal const val DOWNLOAD_WORK_NAME = "dambom-download-queue"
internal const val DOWNLOAD_WORK_TAG = "dambom-download"
private const val MIN_BACKOFF_SECONDS = 10L
