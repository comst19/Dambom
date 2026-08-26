package com.comst19.dambom.work

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject

class LegacyWorkerFactory
    @Inject
    constructor(
        private val delegate: HiltWorkerFactory,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? =
            delegate.createWorker(
                appContext,
                workerClassName.remapLegacyWorkerClassName(),
                workerParameters,
            )
    }

internal fun String.remapLegacyWorkerClassName(): String =
    when (this) {
        LEGACY_DOWNLOAD_QUEUE_WORKER -> DOWNLOAD_QUEUE_WORKER
        else -> this
    }

private const val LEGACY_DOWNLOAD_QUEUE_WORKER =
    "com.comst19.dambom.core.data.repository.DownloadQueueWorker"
private const val DOWNLOAD_QUEUE_WORKER =
    "com.comst19.dambom.core.data.download.DownloadQueueWorker"
