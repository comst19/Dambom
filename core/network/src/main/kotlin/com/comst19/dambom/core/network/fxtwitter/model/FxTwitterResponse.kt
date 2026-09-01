package com.comst19.dambom.core.network.fxtwitter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FxTwitterResponse(
    val code: Int,
    val message: String,
    val tweet: FxTweet? = null,
)

@Serializable
internal data class FxTweet(
    val text: String = "",
    val author: FxAuthor,
    val media: FxMedia? = null,
)

@Serializable
internal data class FxAuthor(
    val name: String,
    @SerialName("screen_name") val screenName: String,
)

@Serializable
internal data class FxMedia(
    val videos: List<FxVideo> = emptyList(),
)

@Serializable
internal data class FxVideo(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val formats: List<FxVideoFormat> = emptyList(),
)

@Serializable
internal data class FxVideoFormat(
    val container: String? = null,
    val codec: String? = null,
    val bitrate: Long? = null,
    val url: String,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
)
