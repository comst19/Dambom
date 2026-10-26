package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun siteDetectionUrl(url: String): String {
    val parsed = url.toHttpUrlOrNull() ?: return url
    return if (parsed.host in setOf("facebook.com", "www.facebook.com")) {
        parsed
            .newBuilder()
            .host("m.facebook.com")
            .build()
            .toString()
    } else {
        url
    }
}

internal fun playerFollowUpUrls(
    requestUrl: String,
    html: String,
): List<String> {
    val url = requestUrl.toHttpUrlOrNull()
    if (url?.host == "player.vimeo.com" && VIMEO_EMBED_PATH.matches(url.encodedPath)) {
        return listOf(
            url
                .newBuilder()
                .encodedPath(url.encodedPath.trimEnd('/') + "/config")
                .build()
                .toString(),
        )
    }
    val base = html.documentBaseUrl(requestUrl)
    return IFRAME_TAG
        .findAll(html)
        .mapNotNull { SRC_ATTRIBUTE.find(it.value)?.groupValues?.get(1) }
        .mapNotNull { resolvePlayerMediaUrl(base, it.decodeHtmlAttribute())?.toHttpUrlOrNull() }
        .filter { embed ->
            when (embed.host) {
                "player.vimeo.com" -> VIMEO_EMBED_PATH.matches(embed.encodedPath)
                "videos.sproutvideo.com" -> SPROUT_EMBED_PATH.matches(embed.encodedPath)
                else -> false
            }
        }.map { it.toString() }
        .filter { it != requestUrl }
        .distinct()
        .take(MAX_PLAYER_FOLLOW_UPS)
        .toList()
}

internal fun parseSproutDownloads(
    requestUrl: String,
    html: String,
): MediaDetectionResult.Success? {
    val page = requestUrl.toHttpUrlOrNull()
    if (page?.host != "videos.sproutvideo.com" || !SPROUT_EMBED_PATH.matches(page.encodedPath)) return null
    val videoId = page.pathSegments[1]
    val sources =
        ANCHOR_TAG
            .findAll(html)
            .mapNotNull { tag ->
                val href =
                    HREF_ATTRIBUTE
                        .find(tag.value)
                        ?.groupValues
                        ?.get(1)
                        ?.decodeHtmlAttribute()
                val target = resolvePlayerMediaUrl(requestUrl, href)?.toHttpUrlOrNull() ?: return@mapNotNull null
                if (target.scheme != "https" || target.host != "sproutvideo.com" ||
                    target.encodedPath != "/videos/$videoId/player_download"
                ) {
                    return@mapNotNull null
                }
                val quality = target.queryParameter("type")?.takeIf { it in SPROUT_QUALITIES } ?: return@mapNotNull null
                PlayerMediaSource(target.toString(), "video/mp4", quality.uppercase())
            }.distinctBy { it.url }
            .take(MAX_SPROUT_DOWNLOADS)
            .toList()
    val title =
        TITLE
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.decodeHtmlAttribute()
            ?.trim()
    return PlayerMediaMetadata(title, null, sources).toDetectionResult(requestUrl)
}

private val VIMEO_EMBED_PATH = Regex("/video/\\d+/?")
private val SPROUT_EMBED_PATH = Regex("/embed/[a-f0-9]+/[a-f0-9]+/?")
private val IFRAME_TAG = Regex("<iframe\\b[^<>]*>", RegexOption.IGNORE_CASE)
private val ANCHOR_TAG = Regex("<a\\b[^<>]*>", RegexOption.IGNORE_CASE)
private val SRC_ATTRIBUTE = Regex("""(?:^|\s)src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val HREF_ATTRIBUTE = Regex("""(?:^|\s)href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val TITLE = Regex("<title\\b[^<>]*>([^<>]*)</title>", RegexOption.IGNORE_CASE)
private val SPROUT_QUALITIES = setOf("sd", "hd", "uhd", "source")
private const val MAX_PLAYER_FOLLOW_UPS = 4
private const val MAX_SPROUT_DOWNLOADS = 4
