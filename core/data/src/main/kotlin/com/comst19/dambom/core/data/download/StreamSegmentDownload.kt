@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import android.os.SystemClock
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadRequest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal suspend fun downloadStreamSegments(
    request: DownloadRequest,
    source: CacheDataSource.Factory,
    checkSpace: () -> Unit,
    parallelism: Int = SEGMENT_PARALLELISM,
    onProgress: suspend (Long, Long?) -> Unit,
) {
    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        val downloader = DefaultDownloaderFactory(source, executor).createDownloader(request)
        coroutineScope {
            val updates = Channel<Pair<Long, Long?>>(Channel.CONFLATED)
            val progress = launch { for ((bytes, total) in updates) onProgress(bytes, total) }
            val cancellation =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        downloader.cancel()
                    }
                }
            val transfer =
                async(Dispatchers.IO) {
                    runInterruptible {
                        val progressLock = Any()
                        var lastUpdate = -PROGRESS_INTERVAL_MILLIS
                        var receivedBytes = 0L
                        downloader.download { length, bytes, _ ->
                            checkSpace()
                            synchronized(progressLock) {
                                receivedBytes = maxOf(receivedBytes, bytes)
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastUpdate >= PROGRESS_INTERVAL_MILLIS) {
                                    updates.trySend(receivedBytes to length.takeIf { it > 0L })
                                    lastUpdate = now
                                }
                            }
                        }
                    }
                }
            try {
                transfer.await()
            } finally {
                cancellation.cancel()
                updates.close()
                progress.join()
            }
        }
    } finally {
        // The scope has joined the downloader and its canceled segment tasks before cache release.
        withContext(NonCancellable + Dispatchers.IO) {
            executor.shutdownNow()
            while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                // Wait for every executor thread before the caller can release its cache.
            }
        }
    }
}

private const val SEGMENT_PARALLELISM = 3
private const val PROGRESS_INTERVAL_MILLIS = 500L
