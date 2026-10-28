package com.comst19.dambom.core.common.net

import java.net.URI

fun String.isUnsupportedVideoService(): Boolean =
    runCatching {
        val host =
            URI(this)
                .host
                ?.lowercase()
                ?.trimEnd('.')
                .orEmpty()
        host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
    }.getOrDefault(false)
