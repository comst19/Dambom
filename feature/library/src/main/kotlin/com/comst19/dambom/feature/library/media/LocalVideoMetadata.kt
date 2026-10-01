package com.comst19.dambom.feature.library.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.FileObserver
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.comst19.dambom.core.common.ui.loadOrCreateVideoThumbnailFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal data class LocalVideoMetadata(
    val thumbnail: Bitmap?,
    val durationMillis: Long?,
    val width: Int?,
    val height: Int?,
)

internal data class LocalVideoCacheKey(
    val path: String,
    val lastModifiedMillis: Long,
    val sizeBytes: Long,
)

internal fun localVideoCacheKey(path: String): LocalVideoCacheKey =
    File(path).let { file ->
        LocalVideoCacheKey(
            path = path,
            lastModifiedMillis = file.lastModified(),
            sizeBytes = file.length(),
        )
    }

@Composable
internal fun rememberLocalVideoMetadata(path: String?): State<LocalVideoMetadata?> =
    LocalContext.current.let { context ->
        produceState<LocalVideoMetadata?>(initialValue = null, key1 = context, key2 = path) {
            value = null
            if (path != null) {
                observeLocalVideoCacheKey(path).collect { key ->
                    value = LocalVideoMetadataLoader.load(context.applicationContext, key)
                }
            }
        }
    }

internal fun observeLocalVideoCacheKey(path: String) =
    callbackFlow {
        val file = File(path)
        val parent = checkNotNull(file.absoluteFile.parentFile)

        @Suppress("DEPRECATION")
        val observer =
            object : FileObserver(
                parent.path,
                DELETE or MOVED_FROM or CREATE or MOVED_TO or CLOSE_WRITE or MODIFY or ATTRIB,
            ) {
                override fun onEvent(
                    event: Int,
                    changedPath: String?,
                ) {
                    if (changedPath == file.name) trySend(Unit)
                }
            }
        observer.startWatching()
        trySend(Unit)
        awaitClose { observer.stopWatching() }
    }.conflate().map { localVideoCacheKey(path) }.distinctUntilChanged().flowOn(Dispatchers.IO)

internal object LocalVideoMetadataLoader {
    private val readMutex = Mutex()
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
        cache[key] ?: readMutex.withLock {
            cache[key] ?: withContext(Dispatchers.IO) {
                readMetadata(
                    key.path,
                    loadOrCreateVideoThumbnailFile(context, key.path)?.let {
                        decodeVideoThumbnail(it.absolutePath)
                    },
                ).also { cache.put(key, it) }
            }
        }

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
