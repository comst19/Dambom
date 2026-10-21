@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.system.ErrnoException
import android.system.OsConstants
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.Clock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import com.comst19.dambom.core.network.header.mediaRequestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class StreamDownloadEngine(
    private val context: Context,
    private val client: OkHttpClient,
    private val fileStore: DownloadFileStore,
) {
    // Keep the transfer boundary explicit for worker and instrumentation callers.
    @Suppress("LongParameterList")
    suspend fun download(
        id: String,
        url: String,
        mimeType: String,
        headers: Map<String, String>,
        output: File,
        videoHeight: Int? = null,
        onProgress: suspend (Long, Long?) -> Unit,
    ) {
        val cache = SimpleCache(fileStore.streamDirectory(id), NoOpCacheEvictor(), fileStore.streamDatabaseProvider)
        var completed = false
        try {
            requireSpaceFor(STREAM_SPACE_CHECK_BYTES)
            val upstream = OkHttpDataSource.Factory(mediaRequestClient(client, url, headers))
            val downloadSource = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
            val item =
                MediaItem
                    .Builder()
                    .setMediaId(id)
                    .setUri(Uri.parse(url))
                    .setMimeType(streamMimeType(url, mimeType) ?: mimeType)
                    .build()
            val request = prepareRequest(id, item, downloadSource, videoHeight)
            cacheMedia(request, downloadSource, onProgress)
            requireSpaceFor(cache.cacheSpace)
            onProgress(cache.cacheSpace, null)
            val cachedSource =
                CacheDataSource
                    .Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(null)
                    .setCacheWriteDataSinkFactory(null)
            output.delete()
            export(request.toMediaItem(), cachedSource, output)
            requireCompletedOutput(output)
            completed = true
        } catch (failure: IOException) {
            throw classifyStreamFailure(failure)
        } catch (failure: ExportException) {
            throw classifyStreamFailure(failure)
        } finally {
            if (!completed) output.delete()
            cache.release()
        }
    }

    private suspend fun prepareRequest(
        id: String,
        item: MediaItem,
        source: DataSource.Factory,
        videoHeight: Int?,
    ): DownloadRequest =
        withContext(Dispatchers.Main.immediate) {
            val helper =
                DownloadHelper
                    .Factory()
                    .setDataSourceFactory(source)
                    .setRenderersFactory(DefaultRenderersFactory(context))
                    .setTrackSelectionParameters(
                        DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .setTrackTypeDisabled(C.TRACK_TYPE_METADATA, true)
                            .apply {
                                if (videoHeight != null) {
                                    setMaxVideoSize(Int.MAX_VALUE, videoHeight)
                                    setExceedVideoConstraintsIfNecessary(false)
                                }
                            }.build(),
                    ).create(item)
            try {
                suspendCancellableCoroutine { continuation ->
                    helper.prepare(
                        object : DownloadHelper.Callback {
                            // Complete the suspended caller when third-party callback processing fails.
                            @Suppress("TooGenericExceptionCaught")
                            override fun onPrepared(
                                helper: DownloadHelper,
                                tracksInfoAvailable: Boolean,
                            ) {
                                if (!continuation.isActive) return
                                try {
                                    val request = preparedRequest(id, helper, tracksInfoAvailable)
                                    continuation.resume(request)
                                } catch (failure: Exception) {
                                    continuation.resumeWithException(failure)
                                }
                            }

                            override fun onPrepareError(
                                helper: DownloadHelper,
                                e: IOException,
                            ) {
                                if (continuation.isActive) continuation.resumeWithException(e)
                            }
                        },
                    )
                }
            } finally {
                helper.release()
            }
        }

    private fun requireSpaceFor(byteCount: Long) {
        if (!fileStore.hasSpaceFor(byteCount)) {
            throw StreamDownloadException(DownloadFailureReason.INSUFFICIENT_STORAGE)
        }
    }

    private fun requireCompletedOutput(output: File) {
        if (!output.isFile || output.length() == 0L) {
            throw StreamDownloadException(DownloadFailureReason.UNSUPPORTED_FORMAT)
        }
    }

    private fun preparedRequest(
        id: String,
        helper: DownloadHelper,
        tracksInfoAvailable: Boolean,
    ): DownloadRequest {
        if (!tracksInfoAvailable || helper.periodCount == 0) {
            throw StreamDownloadException(DownloadFailureReason.UNSUPPORTED_FORMAT)
        }
        requireClearTracks(helper)
        val request = helper.getDownloadRequest(id, null)
        if (request.streamKeys.isEmpty()) {
            throw StreamDownloadException(DownloadFailureReason.UNSUPPORTED_FORMAT)
        }
        return request
    }

    private fun requireClearTracks(helper: DownloadHelper) {
        for (period in 0 until helper.periodCount) {
            val groups = helper.getTrackGroups(period)
            for (group in 0 until groups.length) {
                val tracks = groups[group]
                for (track in 0 until tracks.length) {
                    requireClearFormat(tracks.getFormat(track))
                }
            }
        }
    }

    private fun requireClearFormat(format: androidx.media3.common.Format) {
        if (format.drmInitData != null) throw StreamDownloadException(DownloadFailureReason.UNSUPPORTED_FORMAT)
    }

    private suspend fun cacheMedia(
        request: DownloadRequest,
        source: CacheDataSource.Factory,
        onProgress: suspend (Long, Long?) -> Unit,
    ) = coroutineScope {
        val downloader = DefaultDownloaderFactory(source, Executor(Runnable::run)).createDownloader(request)
        val updates = Channel<Pair<Long, Long?>>(Channel.CONFLATED)
        val progress = launch { for ((bytes, total) in updates) onProgress(bytes, total) }
        val transfer =
            async(Dispatchers.IO) {
                runInterruptible {
                    var lastUpdate = 0L
                    downloader.download { length, bytes, _ ->
                        requireSpaceFor(STREAM_SPACE_CHECK_BYTES)
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUpdate >= STREAM_CHECKPOINT_INTERVAL_MILLIS) {
                            updates.trySend(bytes to length.takeIf { it > 0L })
                            lastUpdate = now
                        }
                    }
                }
            }
        val cancellation =
            launch {
                try {
                    awaitCancellation()
                } finally {
                    downloader.cancel()
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

    private suspend fun export(
        item: MediaItem,
        source: DataSource.Factory,
        output: File,
    ) = coroutineScope {
        val storageMonitor =
            launch(Dispatchers.IO) {
                while (true) {
                    delay(STREAM_CHECKPOINT_INTERVAL_MILLIS)
                    requireSpaceFor(STREAM_SPACE_CHECK_BYTES)
                }
            }
        try {
            exportOnMain(item, source, output)
        } finally {
            storageMonitor.cancel()
        }
    }

    private suspend fun exportOnMain(
        item: MediaItem,
        source: DataSource.Factory,
        output: File,
    ) = withContext(Dispatchers.Main.immediate) {
        val assetLoader =
            ExoPlayerAssetLoader.Factory(
                context,
                DefaultDecoderFactory.Builder(context).build(),
                Clock.DEFAULT,
                DefaultMediaSourceFactory(context).setDataSourceFactory(source),
            )
        val transformer = Transformer.Builder(context).setAssetLoaderFactory(assetLoader).build()
        try {
            suspendCancellableCoroutine { continuation ->
                transformer.addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult,
                        ) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    },
                )
                transformer.start(EditedMediaItem.Builder(item).build(), output.absolutePath)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) { transformer.cancel() }
        }
    }
}

internal fun streamMimeType(
    url: String,
    mimeType: String?,
): String? {
    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
    return when {
        mime in HLS_MIME_TYPES -> MimeTypes.APPLICATION_M3U8
        mime == MimeTypes.APPLICATION_MPD -> MimeTypes.APPLICATION_MPD
        url.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
        url.substringBefore('?').substringBefore('#').endsWith(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
        else -> null
    }
}

internal fun classifyStreamFailure(failure: Exception): Exception {
    val causes = generateSequence<Throwable>(failure) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
    val streamFailure = causes.filterIsInstance<StreamDownloadException>().firstOrNull()
    val httpFailure = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
    val cacheFailure = causes.filterIsInstance<CacheDataSink.CacheDataSinkException>().firstOrNull()
    return when {
        streamFailure != null -> {
            streamFailure
        }

        causes.any { it is ErrnoException && it.errno == OsConstants.ENOSPC } -> {
            StreamDownloadException(DownloadFailureReason.INSUFFICIENT_STORAGE, failure)
        }

        httpFailure != null -> {
            val reason =
                if (httpFailure.responseCode in ACCESS_RESTRICTED_HTTP_CODES) {
                    DownloadFailureReason.ACCESS_RESTRICTED
                } else {
                    DownloadFailureReason.SERVER
                }
            StreamDownloadException(reason, failure)
        }

        cacheFailure != null -> {
            StreamDownloadException(cacheFailure.storageFailureReason(), failure)
        }

        causes.any { it is DownloadHelper.LiveContentUnsupportedException || it is ParserException } -> {
            StreamDownloadException(DownloadFailureReason.UNSUPPORTED_FORMAT, failure)
        }

        failure is IOException -> {
            failure
        }

        else -> {
            StreamDownloadException(DownloadFailureReason.UNSUPPORTED_FORMAT, failure)
        }
    }
}

internal class StreamDownloadException(
    val reason: DownloadFailureReason,
    cause: Throwable? = null,
) : IOException(cause)

private val HLS_MIME_TYPES =
    setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl")
private val ACCESS_RESTRICTED_HTTP_CODES = setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN)
private const val STREAM_SPACE_CHECK_BYTES = 64L * 1024
private const val STREAM_CHECKPOINT_INTERVAL_MILLIS = 500L
private const val MAX_CAUSE_DEPTH = 16

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
