package com.comst19.dambom.feature.web

import com.comst19.dambom.core.common.net.hasVideoFileExtension
import com.comst19.dambom.core.common.net.isHttpUrl
import java.net.URI

internal fun String.normalizeAddress(): String? =
    runCatching {
        val value = trim()
        val candidate = if (value.contains("://")) value else "https://$value"
        if (candidate.isHttpUrl()) candidate else null
    }.getOrNull()

internal fun String.hostLabel(): String = runCatching { URI(this).host.removePrefix("www.") }.getOrDefault(this)

internal fun String.hasVideoExtension(): Boolean = hasVideoFileExtension()

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

private val X_MEDIA_ID_REGEX = Regex("/(?:ext_tw_video|amplify_video)/(\\d+)/")
private const val X_MEDIA_HOST = "video.twimg.com"
