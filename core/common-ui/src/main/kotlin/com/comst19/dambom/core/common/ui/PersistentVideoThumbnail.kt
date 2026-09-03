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
    private val inFlight = mutableMapOf<String, CompletableDeferred<File?>>()

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
            val deferred =
                inFlightLock.withLock {
                    inFlight[pathKey] ?: startGeneration(
                        pathKey = pathKey,
                        videoFile = videoFile,
                        existingThumbnail = existingThumbnail,
                        isUnavailable = isUnavailable,
                        generate = generate,
                    )
                }
            deferred.await()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startGeneration(
        pathKey: String,
        videoFile: File,
        existingThumbnail: (File) -> File?,
        isUnavailable: (File) -> Boolean,
        generate: suspend (File) -> File?,
    ): CompletableDeferred<File?> {
        val deferred = CompletableDeferred<File?>()
        val ownerJob = SupervisorJob()
        inFlight[pathKey] = deferred
        CoroutineScope(ownerJob + dispatcher).launch {
            try {
                val result =
                    generationSemaphore.withPermit {
                        existingThumbnail(videoFile)
                            ?: if (isUnavailable(videoFile)) null else generate(videoFile)
                    }
                removeInFlight(pathKey, deferred)
                deferred.complete(result)
            } catch (cancellation: CancellationException) {
                removeInFlight(pathKey, deferred)
                deferred.cancel(cancellation)
                throw cancellation
            } catch (throwable: Throwable) {
                removeInFlight(pathKey, deferred)
                deferred.completeExceptionally(throwable)
            } finally {
                ownerJob.cancel()
            }
        }
        return deferred
    }

    private suspend fun removeInFlight(
        pathKey: String,
        deferred: CompletableDeferred<File?>,
    ) = withContext(NonCancellable) {
        inFlightLock.withLock {
            if (inFlight[pathKey] === deferred) inFlight.remove(pathKey)
        }
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
        ensureVideoThumbnailFile(videoFile) { output -> writeVideoThumbnail(bitmap, output) }
            .also { thumbnail ->
                if (thumbnail == null) {
                    rememberVideoThumbnailUnavailable(videoFile)
                } else {
                    videoThumbnailUnavailableFile(videoFile).delete()
                }
            }
    } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

internal fun ensureVideoThumbnailFile(
    videoFile: File,
    writeThumbnail: (File) -> Boolean,
): File? {
    if (!videoFile.isFile) return null
    val thumbnailFile = File(videoFile.absolutePath + VIDEO_THUMBNAIL_SUFFIX)
    existingVideoThumbnailFile(videoFile)?.let { return it }
    val temporaryFile = File(thumbnailFile.absolutePath + TEMPORARY_FILE_SUFFIX)
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
    val thumbnailFile = File(videoFile.absolutePath + VIDEO_THUMBNAIL_SUFFIX)
    return thumbnailFile.takeIf {
        it.isFile && it.length() > 0L && it.lastModified() >= videoFile.lastModified()
    }
}

internal fun isVideoThumbnailUnavailable(videoFile: File): Boolean =
    videoThumbnailUnavailableFile(videoFile).let { marker ->
        marker.isFile && marker.lastModified() >= videoFile.lastModified()
    }

internal fun rememberVideoThumbnailUnavailable(videoFile: File) {
    videoThumbnailUnavailableFile(videoFile).apply {
        writeBytes(byteArrayOf())
        setLastModified(maxOf(System.currentTimeMillis(), videoFile.lastModified()))
    }
}

private fun videoThumbnailUnavailableFile(videoFile: File): File = File(videoFile.absolutePath + VIDEO_THUMBNAIL_UNAVAILABLE_SUFFIX)

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

private const val VIDEO_THUMBNAIL_SUFFIX = ".thumbnail.jpg"
private const val VIDEO_THUMBNAIL_UNAVAILABLE_SUFFIX = ".thumbnail.unavailable"
private const val TEMPORARY_FILE_SUFFIX = ".tmp"
private const val THUMBNAIL_WIDTH = 640
private const val THUMBNAIL_HEIGHT = 360
private const val JPEG_QUALITY = 85
private const val MAX_CONCURRENT_THUMBNAIL_GENERATIONS = 2
