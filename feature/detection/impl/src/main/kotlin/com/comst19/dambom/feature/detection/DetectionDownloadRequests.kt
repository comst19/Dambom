package com.comst19.dambom.feature.detection

import com.comst19.dambom.core.common.net.DefaultMediaHeaderPolicy
import com.comst19.dambom.core.common.net.MediaHeaderPolicy
import com.comst19.dambom.core.common.net.hasVideoFileExtension
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.feature.detection.contract.DetectionUiState
import java.util.UUID

internal suspend fun MediaDetectionRepository.selectedDownloadRequests(
    state: DetectionUiState.Content,
    sourcePageUrl: String,
    fallbackTitle: String,
    headerPolicy: MediaHeaderPolicy = DefaultMediaHeaderPolicy,
): List<DownloadRequest> =
    state.candidates.mapIndexedNotNull { index, candidate ->
        if (candidate.id !in state.selectedIds) return@mapIndexedNotNull null
        val variants = candidate.downloadVariants
        val selected =
            variants.firstOrNull { it.selectionId == state.selectedVariantUrls[candidate.id] } ?: variants.first()
        val headers =
            headerPolicy.resolveVariantHeaders(
                candidateUrl = candidate.url,
                candidateHeaders = candidate.requestHeaders,
                variantUrl = selected.url,
                variantHeaders = selected.requestHeaders,
            )
        val variant = resolveDownloadVariant(selected.copy(requestHeaders = headers), headerPolicy)
        DownloadRequest(
            id = UUID.randomUUID().toString(),
            url = variant.url,
            sourcePageUrl = sourcePageUrl,
            title = candidate.displayTitle(fallbackTitle, index, state.candidates.size, state.pageTitle),
            mimeType = variant.mimeType,
            expectedBytes = variant.contentLength,
            quality = variant.quality,
            videoHeight = variant.videoHeight,
            requestHeaders = variant.requestHeaders,
        )
    }

private suspend fun MediaDetectionRepository.resolveDownloadVariant(
    variant: MediaVariant,
    headerPolicy: MediaHeaderPolicy,
): MediaVariant {
    if (variant.mimeType != null || variant.url.hasVideoFileExtension()) return variant
    val result = detectRequest(variant.url, variant.requestHeaders)
    if (result is MediaDetectionResult.Unsupported) throw MediaDownloadUnavailable(result.reason)
    val resolved =
        (result as? MediaDetectionResult.Success)
            ?.candidates
            ?.firstOrNull()
            ?.downloadVariants
            ?.firstOrNull()
    val headers =
        resolved?.let {
            it.requestHeaders.ifEmpty { headerPolicy.mediaHeadersForUrl(variant.requestHeaders, variant.url, it.url) }
        }
    return resolved?.copy(
        quality = variant.quality,
        videoHeight = variant.videoHeight ?: resolved.videoHeight,
        requestHeaders = headers.orEmpty(),
    ) ?: variant
}

internal class MediaDownloadUnavailable(
    val reason: UnsupportedReason,
) : Exception()
