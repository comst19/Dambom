package com.comst19.dambom.feature.web.webview

import com.comst19.dambom.core.common.net.isHttpUrl
import com.comst19.dambom.core.domain.model.MediaResourceRequest
import org.json.JSONObject
import java.net.URI

internal fun parseWebMediaMessage(
    message: String?,
    currentPageUrl: String?,
    isMainFrame: Boolean,
): MediaResourceRequest? {
    val parsed =
        message
            ?.takeIf { it.length <= MAX_MEDIA_MESSAGE_LENGTH }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return null
    val belongsToPage =
        !isMainFrame || parsed.optString("pageUrl").substringBefore('#') == currentPageUrl?.substringBefore('#')
    val url = (parsed.opt("url") as? String)?.validatedMediaUrl()
    return if (belongsToPage && url != null) {
        val poster = (parsed.opt("poster") as? String)?.validatedMediaUrl()
        val title = (parsed.opt("title") as? String)?.trim()?.take(MAX_MEDIA_TITLE_LENGTH)?.takeIf(String::isNotBlank)
        MediaResourceRequest(url, thumbnailUrl = poster, title = title, isProtected = parsed.opt("protected") == true)
    } else {
        null
    }
}

private fun String.validatedMediaUrl(): String? =
    takeIf { value ->
        value.length <= MAX_MEDIA_URL_LENGTH && value.isHttpUrl() &&
            runCatching { URI(value).rawUserInfo == null }.getOrDefault(false)
    }

private const val MAX_MEDIA_TITLE_LENGTH = 512
private const val MAX_MEDIA_URL_LENGTH = 8_192
private const val MAX_MEDIA_MESSAGE_LENGTH = 16_384
