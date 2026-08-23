package com.comst19.dambom.core.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal interface DownloadWorkScheduler {
    fun schedule()
}

internal class WorkManagerDownloadScheduler
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DownloadWorkScheduler {
        private val workManager = WorkManager.getInstance(context)

        override fun schedule() {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
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
            workManager.enqueueUniqueWork(
                DOWNLOAD_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }

internal const val DOWNLOAD_WORK_NAME = "dambom-download-queue"
internal const val DOWNLOAD_WORK_TAG = "dambom-download"
private const val MIN_BACKOFF_SECONDS = 10L
