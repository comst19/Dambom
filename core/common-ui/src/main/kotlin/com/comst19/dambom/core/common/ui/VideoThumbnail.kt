package com.comst19.dambom.core.common.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder

@Composable
fun VideoThumbnail(
    data: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model = data,
        contentDescription = contentDescription,
        imageLoader = VideoThumbnailImageLoader.get(LocalContext.current),
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
fun PreloadVideoThumbnails(data: List<String>) {
    val context = LocalContext.current
    val imageLoader = VideoThumbnailImageLoader.get(context)
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

private object VideoThumbnailImageLoader {
    @Volatile private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: ImageLoader
                .Builder(context.applicationContext)
                .components { add(VideoFrameDecoder.Factory()) }
                .build()
                .also { instance = it }
        }
}

private const val PRELOAD_WIDTH = 640
private const val PRELOAD_HEIGHT = 360
