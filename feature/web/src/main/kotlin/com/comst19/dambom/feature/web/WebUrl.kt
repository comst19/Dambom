package com.comst19.dambom.feature.web

import java.net.URI

internal fun String.normalizeAddress(): String? =
    runCatching {
        val value = trim()
        val candidate = if (value.contains("://")) value else "https://$value"
        val uri = URI(candidate)
        if ((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) candidate else null
    }.getOrNull()

internal fun String.hostLabel(): String = runCatching { URI(this).host.removePrefix("www.") }.getOrDefault(this)

internal fun String.hasVideoExtension(): Boolean =
    VIDEO_EXTENSIONS.any { extension -> substringBefore('?').endsWith(extension, ignoreCase = true) }

internal fun String.detectedVideoKey(): String =
    runCatching {
        val uri = URI(this)
        if (uri.host.equals(X_MEDIA_HOST, ignoreCase = true)) {
            X_MEDIA_ID_REGEX
                .find(uri.path)
                ?.groupValues
                ?.get(1)
                ?.let { return "x:$it" }
        }
        substringBefore('#')
    }.getOrDefault(this)

private val VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".mov", ".m4v")
private val X_MEDIA_ID_REGEX = Regex("/(?:ext_tw_video|amplify_video)/(\\d+)/")
private const val X_MEDIA_HOST = "video.twimg.com"
