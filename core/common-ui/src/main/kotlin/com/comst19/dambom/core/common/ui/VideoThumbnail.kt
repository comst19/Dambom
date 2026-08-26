package com.comst19.dambom.core.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.io.File

@Composable
fun VideoThumbnail(
    data: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val localVideoPath = data.takeIf(::isLocalFilePath)
    val localThumbnail by produceState<File?>(initialValue = null, key1 = context, key2 = localVideoPath) {
        value = localVideoPath?.let { loadOrCreateVideoThumbnailFile(context, it) }
    }
    AsyncImage(
        model = localThumbnail ?: data.takeIf { localVideoPath == null },
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
fun PreloadVideoThumbnails(data: List<String>) {
    val context = LocalContext.current
    val imageLoader = SingletonImageLoader.get(context)
    DisposableEffect(data, imageLoader) {
        val requests =
            data.map { source ->
                imageLoader.enqueue(
                    ImageRequest
                        .Builder(context)
                        .data(source)
                        .size(PRELOAD_WIDTH, PRELOAD_HEIGHT)
                        .build(),
                )
            }
        onDispose { requests.forEach { it.dispose() } }
    }
}

private fun isLocalFilePath(data: String): Boolean = data.startsWith(File.separator)

private const val PRELOAD_WIDTH = 640
private const val PRELOAD_HEIGHT = 360
