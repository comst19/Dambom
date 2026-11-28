package com.comst19.dambom.feature.library.external

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.comst19.dambom.feature.library.file.ShareableVideo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun shareVideo(
    context: Context,
    video: ShareableVideo,
    chooserTitle: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): Boolean {
    val intent = withContext(ioDispatcher) { videoShareIntent(context, video, chooserTitle) }
    return context.startActivitySafely(intent)
}

internal fun videoShareIntent(
    context: Context,
    video: ShareableVideo,
    chooserTitle: String,
): Intent {
    val uri = video.contentUri.toUri()
    val sendIntent =
        Intent(Intent.ACTION_SEND)
            .setType(video.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply {
                clipData = ClipData.newUri(context.contentResolver, video.title, uri)
            }
    return Intent
        .createChooser(sendIntent, chooserTitle)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
