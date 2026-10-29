package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.data.media.mediaCandidateId
import com.comst19.dambom.core.domain.model.MediaCandidate
import java.net.URI

internal fun URI.toHttpUrlOrNull(): String? =
    if (scheme.equals("http", true) || scheme.equals("https", true)) {
        toString()
    } else {
        null
    }

internal fun String.fileTitle(): String =
    runCatching {
        URI(this)
            .path
            .substringAfterLast('/')
            .substringBeforeLast('.')
    }.getOrDefault("")

internal fun String.toCandidate(
    title: String,
    mimeType: String?,
    contentLength: Long?,
    thumbnailUrl: String? = null,
): MediaCandidate =
    MediaCandidate(
        id = mediaCandidateId(),
        url = this,
        title = title,
        mimeType = mimeType,
        contentLength = contentLength,
        thumbnailUrl = thumbnailUrl,
    )
