package com.comst19.dambom.feature.web.webview

import java.net.URI

internal fun siteMediaUserAgent(
    pageUrl: String,
    defaultUserAgent: String,
): String {
    val host = runCatching { URI(pageUrl).host?.lowercase() }.getOrNull()
    val desktop =
        host in DESKTOP_EXACT_HOSTS ||
            DESKTOP_SITE_HOSTS.any { host == it || host?.endsWith(".$it") == true }
    return if (desktop) {
        PLATFORM
            .replaceFirst(defaultUserAgent, "(X11; Linux x86_64)")
            .replace(" Version/4.0", "")
            .replace(" Mobile", "")
    } else {
        defaultUserAgent
    }
}

private val DESKTOP_EXACT_HOSTS = setOf("m.vk.com", "m.facebook.com", "zee5.onelink.me")
private val DESKTOP_SITE_HOSTS = setOf("viki.com", "fullhdfilmizlesene.net", "tver.jp")
private val PLATFORM = Regex("\\([^)]*\\)")
