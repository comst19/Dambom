package com.comst19.dambom.core.data.download

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.database.download.DownloadTaskDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@HiltWorker
internal class DownloadQueueWorker
    internal constructor(
        appContext: Context,
        params: WorkerParameters,
        private val runner: DownloadQueueRunner,
        private val notifier: DownloadNotifier,
        private val ioDispatcher: CoroutineDispatcher,
        private val useUserInitiatedTransfers: Boolean,
    ) : CoroutineWorker(appContext, params) {
        @AssistedInject
        constructor(
            @Assisted appContext: Context,
            @Assisted params: WorkerParameters,
            runner: DownloadQueueRunner,
            notifier: DownloadNotifier,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ) : this(
            appContext = appContext,
            params = params,
            runner = runner,
            notifier = notifier,
            ioDispatcher = ioDispatcher,
            useUserInitiatedTransfers = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        )

        @Suppress("LongParameterList")
        internal constructor(
            appContext: Context,
            params: WorkerParameters,
            dao: DownloadTaskDao,
            client: OkHttpClient,
            fileStore: DownloadFileStore,
            notifier: DownloadNotifier,
            ioDispatcher: CoroutineDispatcher,
            useUserInitiatedTransfers: Boolean = false,
        ) : this(
            appContext = appContext,
            params = params,
            runner = DownloadQueueRunner(appContext, dao, client, fileStore, notifier, ioDispatcher),
            notifier = notifier,
            ioDispatcher = ioDispatcher,
            useUserInitiatedTransfers = useUserInitiatedTransfers,
        )

        private var isForegroundActive = false
        private var foregroundState: ForegroundNotificationState? = null
        private val foregroundMutex = Mutex()

        override suspend fun doWork(): Result =
            withContext(ioDispatcher) {
                if (useUserInitiatedTransfers) return@withContext Result.success()
                if (!promoteInitialForeground()) return@withContext Result.retry()
                when (
                    runner.run(
                        progressReporter = { _, title, downloadedBytes, totalBytes ->
                            updateForeground(title, downloadedBytes, totalBytes)
                        },
                    )
                ) {
                    DownloadQueueRunResult.COMPLETED -> Result.success()
                    DownloadQueueRunResult.RETRY -> Result.retry()
                }
            }

        private suspend fun promoteInitialForeground(): Boolean =
            try {
                setForeground(notifier.foreground())
                isForegroundActive = true
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: IllegalStateException) {
                if (isForegroundServiceStartNotAllowed(error)) {
                    false
                } else {
                    throw error
                }
            }

        private suspend fun updateForeground(
            title: String,
            downloadedBytes: Long,
            totalBytes: Long?,
        ) {
            foregroundMutex.withLock {
                if (!isForegroundActive) return
                val nextState = foregroundNotificationState(title, downloadedBytes, totalBytes)
                if (nextState == foregroundState) return
                try {
                    setForeground(notifier.foreground(title, downloadedBytes, totalBytes))
                    foregroundState = nextState
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: IllegalStateException) {
                    if (isForegroundServiceStartNotAllowed(error)) {
                        isForegroundActive = false
                    } else {
                        throw ForegroundUpdateException(error)
                    }
                }
            }
        }
    }

private fun isForegroundServiceStartNotAllowed(error: Throwable): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        error is ForegroundServiceStartNotAllowedException
