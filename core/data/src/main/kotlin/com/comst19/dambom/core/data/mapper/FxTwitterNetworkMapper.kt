package com.comst19.dambom.core.data.mapper

import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkFailure
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkResult
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkVariant
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkVideo
import java.security.MessageDigest

internal fun FxTwitterNetworkResult.toDomain(): MediaDetectionResult =
    when (this) {
        is FxTwitterNetworkResult.Success -> {
            MediaDetectionResult.Success(
                pageTitle = pageTitle,
                candidates = videos.map(FxTwitterNetworkVideo::toDomain),
            )
        }

        is FxTwitterNetworkResult.Unsupported -> {
            MediaDetectionResult.Unsupported(reason.toDomain())
        }
    }

private fun FxTwitterNetworkVideo.toDomain(): MediaCandidate {
    val primary = variants.first()
    return MediaCandidate(
        id = MessageDigest.getInstance("SHA-256").digest(sourceUrl.toByteArray()).joinToString("") { "%02x".format(it) },
        url = primary.url,
        title = title,
        mimeType = "video/mp4",
        contentLength = primary.size,
        quality = primary.quality,
        thumbnailUrl = thumbnailUrl,
        variants = variants.map(FxTwitterNetworkVariant::toDomain),
    )
}

private fun FxTwitterNetworkVariant.toDomain(): MediaVariant =
    MediaVariant(
        url = url,
        mimeType = "video/mp4",
        contentLength = size,
        quality = quality,
    )

private val FxTwitterNetworkVariant.quality: String
    get() =
        listOfNotNull(
            if (width != null && height != null) "$width×$height" else "MP4",
            bitrate?.let { value -> "${value / BITS_PER_KILOBIT} kbps" },
        ).joinToString(" · ")

private fun FxTwitterNetworkFailure.toDomain(): UnsupportedReason =
    when (this) {
        FxTwitterNetworkFailure.ACCESS_RESTRICTED -> UnsupportedReason.ACCESS_RESTRICTED
        FxTwitterNetworkFailure.NO_MEDIA -> UnsupportedReason.NO_MEDIA
        FxTwitterNetworkFailure.NETWORK_ERROR -> UnsupportedReason.NETWORK_ERROR
        FxTwitterNetworkFailure.UNSUPPORTED_FORMAT -> UnsupportedReason.UNSUPPORTED_FORMAT
    }

private const val BITS_PER_KILOBIT = 1_000
