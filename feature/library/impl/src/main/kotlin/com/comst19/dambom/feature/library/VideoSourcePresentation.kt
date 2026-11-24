package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.DownloadTask
import java.net.URI

internal enum class VideoSourceKind {
    X,
    WEBSITE,
}

internal data class VideoSourcePresentation(
    val kind: VideoSourceKind,
    val host: String?,
)

internal fun videoSourcePresentation(sourcePageUrl: String): VideoSourcePresentation {
    val host =
        runCatching { URI(sourcePageUrl).host }
            .getOrNull()
            .orEmpty()
            .lowercase()
            .removePrefix("www.")
    val isX = host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com")
    return VideoSourcePresentation(
        kind = if (isX) VideoSourceKind.X else VideoSourceKind.WEBSITE,
        host = host.takeUnless { isX || it.isEmpty() },
    )
}

internal fun DownloadTask.libraryContentType(): VideoSourceKind = videoSourcePresentation(sourcePageUrl).kind
