package com.comst19.dambom.core.network.fxtwitter

import com.comst19.dambom.core.network.fxtwitter.model.FxAuthor
import com.comst19.dambom.core.network.fxtwitter.model.FxTweet
import com.comst19.dambom.core.network.fxtwitter.model.FxTwitterResponse
import com.comst19.dambom.core.network.fxtwitter.model.FxVideoFormat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import javax.inject.Inject

class FxTwitterNetworkDataSource
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val json: Json,
    ) {
        fun detect(url: String): FxTwitterNetworkResult? {
            val statusId = url.xStatusId() ?: return null
            return try {
                fetch(statusId)
            } catch (_: IOException) {
                unsupported(FxTwitterNetworkFailure.NETWORK_ERROR)
            } catch (_: SerializationException) {
                unsupported(FxTwitterNetworkFailure.UNSUPPORTED_FORMAT)
            }
        }

        private fun fetch(statusId: String): FxTwitterNetworkResult =
            client.newCall(buildRequest(statusId)).execute().use { response ->
                when {
                    response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN -> {
                        unsupported(FxTwitterNetworkFailure.ACCESS_RESTRICTED)
                    }

                    response.code == HTTP_NOT_FOUND -> {
                        unsupported(FxTwitterNetworkFailure.NO_MEDIA)
                    }

                    !response.isSuccessful -> {
                        unsupported(FxTwitterNetworkFailure.NETWORK_ERROR)
                    }

                    else -> {
                        json.decodeFromString<FxTwitterResponse>(response.body.string()).toNetworkResult()
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

private fun FxTwitterResponse.toNetworkResult(): FxTwitterNetworkResult =
    when (message) {
        FX_PRIVATE_TWEET -> unsupported(FxTwitterNetworkFailure.ACCESS_RESTRICTED)
        FX_NOT_FOUND -> unsupported(FxTwitterNetworkFailure.NO_MEDIA)
        else -> tweet?.toNetworkResult() ?: unsupported(FxTwitterNetworkFailure.NO_MEDIA)
    }

private fun FxTweet.toNetworkResult(): FxTwitterNetworkResult {
    val videos = toNetworkVideos()
    return if (videos.isEmpty()) {
        unsupported(FxTwitterNetworkFailure.NO_MEDIA)
    } else {
        FxTwitterNetworkResult.Success(
            pageTitle = author.displayName(),
            videos = videos,
        )
    }
}

private fun FxTweet.toNetworkVideos(): List<FxTwitterNetworkVideo> {
    val videoTitle =
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
                    }.mapNotNull(FxVideoFormat::toNetworkVariant)
                    .distinctBy(FxTwitterNetworkVariant::url)
                    .sortedWith(
                        compareByDescending<FxTwitterNetworkVariant> { variant -> variant.pixelCount }
                            .thenByDescending { variant -> variant.bitrate ?: 0L },
                    )
            if (variants.isEmpty()) {
                null
            } else {
                FxTwitterNetworkVideo(
                    sourceUrl = video.url,
                    title = videoTitle,
                    thumbnailUrl = video.thumbnailUrl,
                    variants = variants,
                )
            }
        }
}

private fun FxVideoFormat.toNetworkVariant(): FxTwitterNetworkVariant? {
    val uri = runCatching { URI(url) }.getOrNull()
    val isAllowed =
        uri?.scheme.equals("https", true) &&
            uri?.host.equals(X_MEDIA_HOST, true) &&
            uri?.path?.endsWith(".mp4", ignoreCase = true) == true
    return if (isAllowed) {
        val dimensions = VIDEO_DIMENSIONS_REGEX.find(uri.path)
        FxTwitterNetworkVariant(
            url = url,
            width = width ?: dimensions?.groupValues?.get(1)?.toIntOrNull(),
            height = height ?: dimensions?.groupValues?.get(2)?.toIntOrNull(),
            bitrate = bitrate,
            size = size,
        )
    } else {
        null
    }
}

private val FxTwitterNetworkVariant.pixelCount: Long
    get() = (width ?: 0).toLong() * (height ?: 0).toLong()

private fun FxAuthor.displayName(): String = "$name (@$screenName)"

private fun unsupported(reason: FxTwitterNetworkFailure) = FxTwitterNetworkResult.Unsupported(reason)

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
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
