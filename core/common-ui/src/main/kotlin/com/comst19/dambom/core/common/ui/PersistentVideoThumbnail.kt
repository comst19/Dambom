package com.comst19.dambom.core.common.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.video.VideoFrameDecoder
import com.comst19.dambom.core.common.io.videoThumbnailFile
import com.comst19.dambom.core.common.io.videoThumbnailTemporaryFile
import com.comst19.dambom.core.common.io.videoThumbnailUnavailableFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private val thumbnailGenerationCoordinator = VideoThumbnailGenerationCoordinator()

suspend fun loadOrCreateVideoThumbnailFile(
    context: Context,
    path: String,
): File? =
    withContext(Dispatchers.IO) {
        val videoFile = File(path)
        thumbnailGenerationCoordinator.load(
            videoFile = videoFile,
            existingThumbnail = ::existingVideoThumbnailFile,
            isUnavailable = ::isVideoThumbnailUnavailable,
        ) { file ->
            generateVideoThumbnailFile(context.applicationContext, file)
        }
    }

internal class VideoThumbnailGenerationCoordinator(
    maxConcurrentGenerations: Int = MAX_CONCURRENT_THUMBNAIL_GENERATIONS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val generationSemaphore = Semaphore(maxConcurrentGenerations)
    private val inFlightLock = Mutex()
    private val inFlight = mutableMapOf<String, PendingGeneration>()

    suspend fun load(
        videoFile: File,
        existingThumbnail: (File) -> File?,
        isUnavailable: (File) -> Boolean,
        generate: suspend (File) -> File?,
    ): File? {
        val existing = existingThumbnail(videoFile)
        return if (existing != null || isUnavailable(videoFile)) {
            existing
        } else {
            val pathKey =
                videoFile
                    .toPath()
                    .toAbsolutePath()
                    .normalize()
                    .toString()
            val generation =
                inFlightLock.withLock {
                    inFlight[pathKey]?.also { it.waiters++ } ?: startGeneration(
                        pathKey = pathKey,
                        videoFile = videoFile,
                        existingThumbnail = existingThumbnail,
                        isUnavailable = isUnavailable,
                        generate = generate,
                    )
                }
            try {
                generation.deferred.await()
            } finally {
                releaseWaiter(pathKey, generation)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startGeneration(
        pathKey: String,
        videoFile: File,
        existingThumbnail: (File) -> File?,
        isUnavailable: (File) -> Boolean,
        generate: suspend (File) -> File?,
    ): PendingGeneration {
        val generation = PendingGeneration()
        inFlight[pathKey] = generation
        CoroutineScope(generation.ownerJob + dispatcher).launch {
            try {
                val result =
                    generationSemaphore.withPermit {
                        inFlightLock.withLock {
                            if (inFlight[pathKey] !== generation) {
                                throw CancellationException("Thumbnail request has no waiters")
                            }
                            generation.started = true
                        }
                        existingThumbnail(videoFile)
                            ?: if (isUnavailable(videoFile)) null else generate(videoFile)
                    }
                removeInFlight(pathKey, generation)
                generation.deferred.complete(result)
            } catch (cancellation: CancellationException) {
                removeInFlight(pathKey, generation)
                generation.deferred.cancel(cancellation)
                throw cancellation
            } catch (throwable: Throwable) {
                removeInFlight(pathKey, generation)
                generation.deferred.completeExceptionally(throwable)
            } finally {
                generation.ownerJob.cancel()
            }
        }
        return generation
    }

    private suspend fun releaseWaiter(
        pathKey: String,
        generation: PendingGeneration,
    ) = withContext(NonCancellable) {
        inFlightLock.withLock {
            generation.waiters--
            if (generation.waiters == 0 && !generation.started && inFlight[pathKey] === generation) {
                inFlight.remove(pathKey)
                generation.ownerJob.cancel()
                generation.deferred.cancel()
            }
        }
    }

    private suspend fun removeInFlight(
        pathKey: String,
        generation: PendingGeneration,
    ) = withContext(NonCancellable) {
        inFlightLock.withLock {
            if (inFlight[pathKey] === generation) inFlight.remove(pathKey)
        }
    }

    private class PendingGeneration {
        val deferred = CompletableDeferred<File?>()
        val ownerJob = SupervisorJob()
        var waiters = 1
        var started = false
    }
}

private suspend fun generateVideoThumbnailFile(
    context: Context,
    videoFile: File,
): File? {
    val bitmap = decodeVideoThumbnail(context, videoFile)
    if (bitmap == null) {
        rememberVideoThumbnailUnavailable(videoFile)
        return null
    }
    return try {
        storeDecodedVideoThumbnail(videoFile) { output -> writeVideoThumbnail(bitmap, output) }
    } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

internal fun storeDecodedVideoThumbnail(
    videoFile: File,
    writeThumbnail: (File) -> Boolean,
): File? =
    ensureVideoThumbnailFile(videoFile, writeThumbnail)
        .also { thumbnail ->
            if (thumbnail != null) {
                videoFile.videoThumbnailUnavailableFile().delete()
            }
        }

internal fun ensureVideoThumbnailFile(
    videoFile: File,
    writeThumbnail: (File) -> Boolean,
): File? {
    if (!videoFile.isFile) return null
    val thumbnailFile = videoFile.videoThumbnailFile()
    existingVideoThumbnailFile(videoFile)?.let { return it }
    val temporaryFile = videoFile.videoThumbnailTemporaryFile()
    temporaryFile.delete()
    if (!writeThumbnail(temporaryFile)) {
        temporaryFile.delete()
        return null
    }
    thumbnailFile.delete()
    if (!temporaryFile.renameTo(thumbnailFile)) {
        temporaryFile.delete()
        return null
    }
    return thumbnailFile
}

private fun existingVideoThumbnailFile(videoFile: File): File? {
    val thumbnailFile = videoFile.videoThumbnailFile()
    return thumbnailFile.takeIf {
        it.isFile && it.length() > 0L && it.lastModified() >= videoFile.lastModified()
    }
}

internal fun isVideoThumbnailUnavailable(videoFile: File): Boolean =
    videoFile.videoThumbnailUnavailableFile().let { marker ->
        marker.isFile && marker.lastModified() >= videoFile.lastModified()
    }

internal fun rememberVideoThumbnailUnavailable(videoFile: File) {
    try {
        videoFile.videoThumbnailUnavailableFile().apply {
            writeBytes(byteArrayOf())
            setLastModified(maxOf(System.currentTimeMillis(), videoFile.lastModified()))
        }
    } catch (_: IOException) {
    } catch (_: RuntimeException) {
    }
}

private suspend fun decodeVideoThumbnail(
    context: Context,
    videoFile: File,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return try {
            ThumbnailUtils.createVideoThumbnail(videoFile, Size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT), null)
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
    val imageLoader =
        ImageLoader
            .Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    return try {
        val request =
            ImageRequest
                .Builder(context)
                .data(videoFile)
                .size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        val image = (imageLoader.execute(request) as? SuccessResult)?.image ?: return null
        Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            image.draw(Canvas(bitmap))
        }
    } finally {
        imageLoader.shutdown()
    }
}

private fun writeVideoThumbnail(
    bitmap: Bitmap,
    outputFile: File,
): Boolean =
    try {
        outputFile.outputStream().buffered().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
    } catch (_: IOException) {
        false
    } catch (_: RuntimeException) {
        false
    }

private const val THUMBNAIL_WIDTH = 640
private const val THUMBNAIL_HEIGHT = 360
private const val JPEG_QUALITY = 85
private const val MAX_CONCURRENT_THUMBNAIL_GENERATIONS = 2
