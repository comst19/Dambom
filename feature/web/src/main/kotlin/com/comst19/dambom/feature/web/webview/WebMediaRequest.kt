package com.comst19.dambom.feature.web.webview

import com.comst19.dambom.core.domain.model.MediaResourceRequest

internal fun MediaResourceRequest.withBrowserContext(
    userAgent: String,
    cookie: String?,
): MediaResourceRequest {
    val enriched = headers.toMutableMap()
    if (enriched.keys.none { it.equals("User-Agent", true) }) enriched["User-Agent"] = userAgent
    if (cookie != null) {
        enriched.keys.removeAll { it.equals("Cookie", true) }
        enriched["Cookie"] = cookie
    }
    return copy(headers = enriched)
}
