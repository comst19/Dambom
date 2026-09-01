package com.comst19.dambom.core.network.fxtwitter

sealed interface FxTwitterNetworkResult {
    data class Success(
        val pageTitle: String,
        val videos: List<FxTwitterNetworkVideo>,
    ) : FxTwitterNetworkResult

    data class Unsupported(
        val reason: FxTwitterNetworkFailure,
    ) : FxTwitterNetworkResult
}

enum class FxTwitterNetworkFailure {
    ACCESS_RESTRICTED,
    NO_MEDIA,
    NETWORK_ERROR,
    UNSUPPORTED_FORMAT,
}

data class FxTwitterNetworkVideo(
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val variants: List<FxTwitterNetworkVariant>,
)

data class FxTwitterNetworkVariant(
    val url: String,
    val width: Int?,
    val height: Int?,
    val bitrate: Long?,
    val size: Long?,
)
