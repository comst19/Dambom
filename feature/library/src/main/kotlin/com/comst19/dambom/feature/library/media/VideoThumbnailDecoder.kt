package com.comst19.dambom.feature.library.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal fun decodeVideoThumbnail(path: String): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    var sampleSize = 1
    while (
        (options.outWidth.toLong() + sampleSize - 1) / sampleSize > MAX_THUMBNAIL_EDGE ||
        (options.outHeight.toLong() + sampleSize - 1) / sampleSize > MAX_THUMBNAIL_EDGE
    ) {
        sampleSize *= 2
    }
    options.inSampleSize = sampleSize
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeFile(path, options)
}

private const val MAX_THUMBNAIL_EDGE = 640
