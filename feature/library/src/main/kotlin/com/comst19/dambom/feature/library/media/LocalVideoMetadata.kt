package com.comst19.dambom.feature.library.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.comst19.dambom.core.common.ui.loadOrCreateVideoThumbnailFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LocalVideoMetadata(
    val thumbnail: Bitmap?,
    val durationMillis: Long?,
    val width: Int?,
    val height: Int?,
)

internal data class LocalVideoCacheKey(
    val path: String,
    val revision: Long,
)

@Composable
internal fun rememberLocalVideoMetadata(
    path: String?,
    revision: Long,
): State<LocalVideoMetadata?> =
    LocalContext.current.let { context ->
        val cacheKey = path?.let { LocalVideoCacheKey(it, revision) }
        produceState<LocalVideoMetadata?>(initialValue = null, key1 = context, key2 = cacheKey) {
            value = cacheKey?.let { LocalVideoMetadataLoader.load(context.applicationContext, it) }
        }
    }

private object LocalVideoMetadataLoader {
    private val cache =
        object : LruCache<LocalVideoCacheKey, LocalVideoMetadata>(THUMBNAIL_CACHE_KB) {
            override fun sizeOf(
                key: LocalVideoCacheKey,
                value: LocalVideoMetadata,
            ): Int =
                value.thumbnail
                    ?.allocationByteCount
                    ?.div(1024)
                    ?.coerceAtLeast(1)
                    ?: 1
        }

    suspend fun load(
        context: Context,
        key: LocalVideoCacheKey,
    ): LocalVideoMetadata =
        cache[key] ?: withContext(Dispatchers.IO) {
            readMetadata(
                key.path,
                loadOrCreateVideoThumbnailFile(context, key.path)?.let { BitmapFactory.decodeFile(it.absolutePath) },
            )
        }.also { cache.put(key, it) }

    private fun readMetadata(
        path: String,
        thumbnail: Bitmap?,
    ): LocalVideoMetadata {
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
                thumbnail = thumbnail,
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

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int? = extractMetadata(key)?.toIntOrNull()?.takeIf { it > 0 }

    private fun MediaMetadataRetriever.longMetadata(key: Int): Long? = extractMetadata(key)?.toLongOrNull()?.takeIf { it > 0L }
}

private const val THUMBNAIL_CACHE_KB = 24 * 1024
