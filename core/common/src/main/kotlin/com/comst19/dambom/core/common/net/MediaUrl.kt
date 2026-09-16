package com.comst19.dambom.core.common.net

import java.net.URI

fun String.isHttpUrl(): Boolean =
    runCatching {
        val uri = URI(trim())
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

fun String.hasVideoFileExtension(): Boolean =
    runCatching {
        val path = URI(this).path ?: return@runCatching false
        VIDEO_EXTENSIONS.any { extension -> path.endsWith(extension, ignoreCase = true) }
    }.getOrDefault(false)

private val VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".mov", ".m4v")
