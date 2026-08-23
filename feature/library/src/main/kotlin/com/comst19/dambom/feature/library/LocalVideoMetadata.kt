package com.comst19.dambom.feature.library

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LocalVideoMetadata(
    val thumbnail: Bitmap?,
    val durationMillis: Long?,
    val width: Int?,
    val height: Int?,
)

@Composable
internal fun rememberLocalVideoMetadata(path: String?): State<LocalVideoMetadata?> =
    produceState<LocalVideoMetadata?>(initialValue = null, key1 = path) {
        value = path?.let { LocalVideoMetadataLoader.load(it) }
    }

private object LocalVideoMetadataLoader {
    private val cache =
        object : LruCache<String, LocalVideoMetadata>(THUMBNAIL_CACHE_KB) {
            override fun sizeOf(
                key: String,
                value: LocalVideoMetadata,
            ): Int =
                value.thumbnail
                    ?.allocationByteCount
                    ?.div(1024)
                    ?.coerceAtLeast(1)
                    ?: 1
        }

    suspend fun load(path: String): LocalVideoMetadata =
        cache[path] ?: withContext(Dispatchers.IO) { readMetadata(path) }.also { cache.put(path, it) }

    private fun readMetadata(path: String): LocalVideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val rotation = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            val rawWidth = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val rawHeight = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val (width, height) =
                if (rotation == 90 || rotation == 270) {
                    rawHeight to rawWidth
                } else {
                    rawWidth to rawHeight
                }
            LocalVideoMetadata(
                thumbnail = retriever.thumbnail(rawWidth, rawHeight),
                durationMillis = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
                width = width,
                height = height,
            )
        } catch (_: RuntimeException) {
            LocalVideoMetadata(null, null, null, null)
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.thumbnail(
        width: Int?,
        height: Int?,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && width != null && height != null && width > 0 && height > 0) {
            val targetWidth = width.coerceAtMost(THUMBNAIL_WIDTH)
            val targetHeight = (height * targetWidth.toFloat() / width).toInt().coerceAtLeast(1)
            return getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetWidth, targetHeight)
        }
        return getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int? = extractMetadata(key)?.toIntOrNull()?.takeIf { it > 0 }

    private fun MediaMetadataRetriever.longMetadata(key: Int): Long? = extractMetadata(key)?.toLongOrNull()?.takeIf { it > 0L }
}

private const val THUMBNAIL_CACHE_KB = 24 * 1024
private const val THUMBNAIL_WIDTH = 640
