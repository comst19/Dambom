package com.comst19.dambom.feature.web

import com.comst19.dambom.core.common.net.hasStreamManifestExtension
import com.comst19.dambom.core.common.net.hasVideoFileExtension
import com.comst19.dambom.core.common.net.readableMediaTitle
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaResourceRequest

internal class WebMediaCandidates {
    private val attempted = mutableMapOf<String, Map<String, String>>()
    private val metadata = mutableMapOf<String, Metadata>()
    private val detected = linkedMapOf<String, MediaCandidate>()
    private val protectedKeys = mutableSetOf<String>()
    var hasProtectedMedia: Boolean = false
        private set
    val count: Int get() = detected.size
    val candidates: List<MediaCandidate> get() = detected.values.toList()

    fun claim(request: MediaResourceRequest): Boolean {
        updateMetadata(request)
        val knownMedia = request.url.hasVideoFileExtension() || request.url.hasStreamManifestExtension()
        val budgetExhausted = attempted.size >= MAX_MEDIA_PROBES && request.url !in attempted
        val claimed = attempted[request.url] != request.headers && (knownMedia || !budgetExhausted)
        if (claimed) attempted[request.url] = request.headers.toMap()
        return claimed
    }

    private fun updateMetadata(request: MediaResourceRequest) {
        val key = request.url.detectedVideoKey()
        if (request.thumbnailUrl != null || request.title != null) {
            val previous = metadata[key]
            val updated =
                Metadata(
                    thumbnailUrl = request.thumbnailUrl ?: previous?.thumbnailUrl,
                    title = request.title ?: previous?.title,
                )
            if (key in metadata || metadata.size < MAX_DETECTED_MEDIA) metadata[key] = updated
            detected[key]?.let {
                detected[key] =
                    it.copy(
                        thumbnailUrl = updated.thumbnailUrl ?: it.thumbnailUrl,
                        title = updated.title ?: it.title,
                    )
            }
        }
    }

    fun rejectProtected(url: String) {
        hasProtectedMedia = true
        val key = url.detectedVideoKey()
        if (protectedKeys.size < MAX_DETECTED_MEDIA) protectedKeys.add(key)
        detected.remove(key)
    }

    fun merge(
        incoming: List<MediaCandidate>,
        preserveExistingTitle: Boolean = false,
        pageTitle: String = "",
        containsProtectedMedia: Boolean = false,
    ): Int {
        hasProtectedMedia = hasProtectedMedia || containsProtectedMedia
        incoming.take(MAX_DETECTED_MEDIA).forEach { candidate ->
            val key = candidate.url.detectedVideoKey()
            if (key in protectedKeys) return@forEach
            val previous = detected[key]
            val incomingTitle = candidate.title.readableMediaTitle() ?: pageTitle.readableMediaTitle().orEmpty()
            val existingTitle = previous?.title?.readableMediaTitle()?.takeIf { preserveExistingTitle }
            if (previous != null || detected.size < MAX_DETECTED_MEDIA) {
                detected[key] =
                    candidate.copy(
                        title = metadata[key]?.title?.readableMediaTitle() ?: existingTitle ?: incomingTitle,
                        requestHeaders = candidate.requestHeaders.ifEmpty { previous?.requestHeaders.orEmpty() },
                        variants = candidate.variants.ifEmpty { previous?.variants.orEmpty() },
                        thumbnailUrl = metadata[key]?.thumbnailUrl ?: candidate.thumbnailUrl ?: previous?.thumbnailUrl,
                        mimeType = candidate.mimeType ?: previous?.mimeType,
                    )
            }
        }
        return count
    }

    private data class Metadata(
        val thumbnailUrl: String?,
        val title: String?,
    )
}

private const val MAX_DETECTED_MEDIA = 100

internal fun shouldProbeMedia(url: String): Boolean =
    runCatching {
        val uri = java.net.URI(url)
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return@runCatching false
        val path = uri.path.orEmpty()
        url.hasVideoFileExtension() || url.hasStreamManifestExtension() ||
            path.substringAfterLast('/').substringAfterLast('.', "").lowercase() !in NON_MEDIA_EXTENSIONS
    }.getOrDefault(false)

private const val MAX_MEDIA_PROBES = 128

private val NON_MEDIA_EXTENSIONS =
    setOf(
        "js",
        "css",
        "png",
        "jpg",
        "jpeg",
        "gif",
        "svg",
        "webp",
        "ico",
        "woff",
        "woff2",
        "ttf",
        "otf",
        "ts",
        "m4s",
        "aac",
        "mp3",
        "vtt",
        "srt",
        "map",
    )

internal fun directMediaResult(
    request: MediaResourceRequest,
    title: String,
): MediaDetectionResult.Success =
    MediaDetectionResult.Success(
        title,
        listOf(
            MediaCandidate(
                id = request.url.detectedVideoKey(),
                url = request.url,
                title =
                    request.title ?: title.ifBlank {
                        request.url
                            .substringBefore('?')
                            .substringAfterLast('/')
                            .substringBeforeLast('.')
                    },
                mimeType = null,
                contentLength = null,
                requestHeaders = request.headers,
                thumbnailUrl = request.thumbnailUrl,
            ),
        ),
    )
