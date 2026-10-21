package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.core.domain.model.ORIGINAL_QUALITY
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class PlayerMediaMetadata(
    val title: String?,
    val thumbnailUrl: String?,
    val sources: List<PlayerMediaSource>,
)

internal data class PlayerMediaSource(
    val url: String,
    val mimeType: String,
    val quality: String? = null,
    val height: Int? = null,
)

internal fun PlayerMediaMetadata.toDetectionResult(baseUrl: String): MediaDetectionResult.Success? {
    val variants =
        sources
            .mapNotNull { source ->
                val url = resolvePlayerMediaUrl(baseUrl, source.url) ?: return@mapNotNull null
                MediaVariant(
                    url = url,
                    mimeType = source.mimeType,
                    contentLength = null,
                    quality = source.quality?.takeIf { it.isNotBlank() } ?: ORIGINAL_QUALITY,
                    videoHeight = source.height?.takeIf { it > 0 },
                )
            }.distinctBy { it.url }
    val primary = variants.firstOrNull() ?: return null
    val displayTitle = title?.takeIf { it.isNotBlank() } ?: primary.url.fileTitle()
    val thumbnail = resolvePlayerMediaUrl(baseUrl, thumbnailUrl)
    val candidate = primary.url.toCandidate(displayTitle, primary.mimeType, null, thumbnail).copy(variants = variants)
    return MediaDetectionResult.Success(displayTitle, listOf(candidate))
}

internal fun resolvePlayerMediaUrl(
    baseUrl: String,
    value: String?,
): String? {
    val uri = value?.toHttpUrlOrNull() ?: value?.let { baseUrl.toHttpUrlOrNull()?.resolve(it) }
    return uri?.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()
}
