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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private val thumbnailGenerationLock = Mutex()

suspend fun loadOrCreateVideoThumbnailFile(
    context: Context,
    path: String,
): File? =
    withContext(Dispatchers.IO) {
        val videoFile = File(path)
        thumbnailGenerationLock.withLock {
            existingVideoThumbnailFile(videoFile)?.let { return@withLock it }
            if (isVideoThumbnailUnavailable(videoFile)) return@withLock null
            val bitmap = decodeVideoThumbnail(context.applicationContext, videoFile)
            if (bitmap == null) {
                rememberVideoThumbnailUnavailable(videoFile)
                return@withLock null
            }
            try {
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
