package com.comst19.dambom.feature.library.trim.media

import android.net.Uri
import androidx.media3.common.MediaItem
import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import java.io.File

internal fun clippedMediaItem(
    path: String,
    selection: TrimSelection,
): MediaItem =
    MediaItem
        .Builder()
        .setUri(Uri.fromFile(File(path)))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration
                .Builder()
                .setStartPositionMs(selection.startMillis)
                .setEndPositionMs(selection.endMillis)
                .build(),
        ).build()
