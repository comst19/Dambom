package com.comst19.dambom.core.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.await
import com.comst19.dambom.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal interface DownloadWorkScheduler {
    suspend fun schedule()

    suspend fun ensureScheduled()

    suspend fun reschedule()
}

internal class WorkManagerDownloadScheduler
    internal constructor(
        private val settingsRepository: SettingsRepository,
        private val workManager: DownloadWorkManager,
    ) : DownloadWorkScheduler {
        @Inject
        constructor(
            @ApplicationContext context: Context,
            settingsRepository: SettingsRepository,
        ) : this(settingsRepository, ContextDownloadWorkManager(context))

        override suspend fun schedule() {
            enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
        }

        override suspend fun ensureScheduled() {
            enqueue(ExistingWorkPolicy.KEEP)
        }

        override suspend fun reschedule() {
            workManager.cancelUniqueWork(DOWNLOAD_WORK_NAME).await()
            enqueue(ExistingWorkPolicy.REPLACE)
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

private class ContextDownloadWorkManager(
    context: Context,
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
