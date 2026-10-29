package com.comst19.dambom.core.common.net

import java.net.URLDecoder

fun String.readableMediaTitle(): String? {
    val decoded = runCatching { URLDecoder.decode(replace("+", "%2B"), "UTF-8") }.getOrDefault(this)
    val label = decoded.replace(TITLE_WHITESPACE, " ").trim().replace(MEDIA_TITLE_EXTENSION, "")
    return label.takeUnless {
        it.isBlank() || TITLE_URL.containsMatchIn(it) || TECHNICAL_MEDIA_TITLE.matches(it) ||
            it.lowercase() in LOADING_PAGE_TITLES
    }
}

private val TITLE_WHITESPACE = Regex("\\s+")
private val MEDIA_TITLE_EXTENSION = Regex("\\.(?:mp4|webm|mov|m4v|m3u8|mpd)$", RegexOption.IGNORE_CASE)
private val TITLE_URL = Regex("^(?:https?://|blob:|data:)", RegexOption.IGNORE_CASE)
private val TECHNICAL_MEDIA_TITLE =
    Regex(
        "[0-9a-f]{16,}|[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}|" +
            "(?:master|playlist|index|manifest|items|chunklist|videoplayback)(?:[-_ ]?[0-9]+)?|[0-9]{10,}",
        RegexOption.IGNORE_CASE,
    )
private val LOADING_PAGE_TITLES = setOf("확인 중", "잠시만 기다려주세요", "just a moment...", "checking your browser")
