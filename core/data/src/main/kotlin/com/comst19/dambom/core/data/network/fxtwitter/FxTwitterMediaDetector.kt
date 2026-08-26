package com.comst19.dambom.core.data.network.fxtwitter

import com.comst19.dambom.core.data.network.fxtwitter.model.FxAuthor
import com.comst19.dambom.core.data.network.fxtwitter.model.FxTweet
import com.comst19.dambom.core.data.network.fxtwitter.model.FxTwitterResponse
import com.comst19.dambom.core.data.network.fxtwitter.model.FxVideoFormat
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.core.domain.model.UnsupportedReason
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

internal class FxTwitterMediaDetector
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val json: Json,
    ) {
        fun detect(url: String): MediaDetectionResult? {
            val statusId = url.xStatusId() ?: return null
            return try {
                fetch(statusId)
            } catch (_: IOException) {
                unsupported(UnsupportedReason.NETWORK_ERROR)
            } catch (_: SerializationException) {
                unsupported(UnsupportedReason.UNSUPPORTED_FORMAT)
            }
        }

        private fun fetch(statusId: String): MediaDetectionResult =
            client.newCall(buildRequest(statusId)).execute().use { response ->
                when {
                    response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN -> {
                        unsupported(UnsupportedReason.ACCESS_RESTRICTED)
                    }

                    response.code == HTTP_NOT_FOUND -> {
                        unsupported(UnsupportedReason.NO_MEDIA)
                    }

                    !response.isSuccessful -> {
                        unsupported(UnsupportedReason.NETWORK_ERROR)
                    }

                    else -> {
                        json.decodeFromString<FxTwitterResponse>(response.body.string()).toDetectionResult()
                    }
                }
            }
    }

private fun buildRequest(statusId: String): Request =
    Request
        .Builder()
        .url("$FXTWITTER_STATUS_URL$statusId")
        .header("Accept", "application/json")
        .header("User-Agent", FXTWITTER_USER_AGENT)
        .build()

private fun String.xStatusId(): String? {
    val uri = runCatching { URI(this) }.getOrNull()
    return uri
        ?.takeIf { parsed -> parsed.host?.lowercase() in X_HOSTS }
        ?.let { parsed -> X_STATUS_PATH_REGEX.matchEntire(parsed.path)?.groupValues?.get(1) }
}

private fun FxTwitterResponse.toDetectionResult(): MediaDetectionResult =
    when (message) {
        FX_PRIVATE_TWEET -> unsupported(UnsupportedReason.ACCESS_RESTRICTED)
        FX_NOT_FOUND -> unsupported(UnsupportedReason.NO_MEDIA)
        else -> tweet?.toDetectionResult() ?: unsupported(UnsupportedReason.NO_MEDIA)
    }

private fun FxTweet.toDetectionResult(): MediaDetectionResult {
    val candidates = toCandidates()
    return if (candidates.isEmpty()) {
        unsupported(UnsupportedReason.NO_MEDIA)
    } else {
        MediaDetectionResult.Success(
            pageTitle = author.displayName(),
            candidates = candidates,
        )
    }
}

private fun FxTweet.toCandidates(): List<MediaCandidate> {
    val candidateTitle =
        text
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .take(MAX_X_TITLE_LENGTH)
            .ifBlank(author::displayName)
    return media
        ?.videos
        .orEmpty()
        .mapNotNull { video ->
            val variants =
                video.formats
                    .filter { format -> format.container.equals("mp4", ignoreCase = true) }
                    .ifEmpty {
                        listOf(
                            FxVideoFormat(
                                url = video.url,
                                width = video.width,
                                height = video.height,
                            ),
                        )
                    }.mapNotNull(FxVideoFormat::toVariant)
                    .distinctBy(XVideoVariant::url)
                    .sortedWith(
                        compareByDescending<XVideoVariant> { variant -> variant.pixelCount }
                            .thenByDescending { variant -> variant.bitrate ?: 0L },
                    )
            val primary = variants.firstOrNull() ?: return@mapNotNull null
            primary.toCandidate(
                idSource = video.url,
                title = candidateTitle,
                thumbnailUrl = video.thumbnailUrl,
                variants = variants.map(XVideoVariant::toMediaVariant),
            )
        }
}

private fun FxVideoFormat.toVariant(): XVideoVariant? {
    val uri = runCatching { URI(url) }.getOrNull()
    val isAllowed =
        uri?.scheme.equals("https", true) &&
            uri?.host.equals(X_MEDIA_HOST, true) &&
            uri?.path?.endsWith(".mp4", ignoreCase = true) == true
    return if (isAllowed) {
        val dimensions = VIDEO_DIMENSIONS_REGEX.find(uri.path)
        val resolvedWidth = width ?: dimensions?.groupValues?.get(1)?.toIntOrNull()
        val resolvedHeight = height ?: dimensions?.groupValues?.get(2)?.toIntOrNull()
        XVideoVariant(
            url = url,
            width = resolvedWidth,
            height = resolvedHeight,
            bitrate = bitrate,
            size = size,
        )
    } else {
        null
    }
}

private fun XVideoVariant.toCandidate(
    idSource: String,
    title: String,
    thumbnailUrl: String?,
    variants: List<MediaVariant>,
): MediaCandidate =
    MediaCandidate(
        id = MessageDigest.getInstance("SHA-256").digest(idSource.toByteArray()).joinToString("") { "%02x".format(it) },
        url = url,
        title = title,
        mimeType = "video/mp4",
        contentLength = size,
        quality = quality,
        thumbnailUrl = thumbnailUrl,
        variants = variants,
    )

private fun XVideoVariant.toMediaVariant(): MediaVariant =
    MediaVariant(
        url = url,
        mimeType = "video/mp4",
        contentLength = size,
        quality = quality,
    )

private fun FxAuthor.displayName(): String = "$name (@$screenName)"

private fun unsupported(reason: UnsupportedReason) = MediaDetectionResult.Unsupported(reason)

private data class XVideoVariant(
    val url: String,
    val width: Int?,
    val height: Int?,
    val bitrate: Long?,
    val size: Long?,
) {
    val pixelCount: Long = (width ?: 0).toLong() * (height ?: 0).toLong()
    val quality: String =
        listOfNotNull(
            if (width != null && height != null) "$width×$height" else "MP4",
            bitrate?.let { value -> "${value / BITS_PER_KILOBIT} kbps" },
        ).joinToString(" · ")
}

private val WHITESPACE_REGEX = Regex("\\s+")
private val X_STATUS_PATH_REGEX = Regex("^/(?:[^/]+|i)/status/(\\d+)(?:/.*)?$")
private val VIDEO_DIMENSIONS_REGEX = Regex("/vid/(\\d+)x(\\d+)/")
private val X_HOSTS =
    setOf(
        "x.com",
        "www.x.com",
        "mobile.x.com",
        "twitter.com",
        "www.twitter.com",
        "mobile.twitter.com",
    )
private const val X_MEDIA_HOST = "video.twimg.com"
private const val FXTWITTER_STATUS_URL = "https://api.fxtwitter.com/i/status/"
private const val FXTWITTER_USER_AGENT = "Dambom/1.0 (public-video-metadata)"
private const val FX_PRIVATE_TWEET = "PRIVATE_TWEET"
private const val FX_NOT_FOUND = "NOT_FOUND"
private const val MAX_X_TITLE_LENGTH = 80
private const val BITS_PER_KILOBIT = 1_000
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
