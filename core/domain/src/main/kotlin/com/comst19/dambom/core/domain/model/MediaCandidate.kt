package com.comst19.dambom.core.domain.model

data class MediaCandidate(
    val id: String,
    val url: String,
    val title: String,
    val mimeType: String?,
    val contentLength: Long?,
    val quality: String = ORIGINAL_QUALITY,
)

sealed interface MediaDetectionResult {
    data class Success(
        val pageTitle: String,
        val candidates: List<MediaCandidate>,
    ) : MediaDetectionResult

    data class Unsupported(
        val reason: UnsupportedReason,
    ) : MediaDetectionResult
}

enum class UnsupportedReason {
    INVALID_URL,
    ACCESS_RESTRICTED,
    NO_MEDIA,
    NETWORK_ERROR,
    UNSUPPORTED_FORMAT,
}
