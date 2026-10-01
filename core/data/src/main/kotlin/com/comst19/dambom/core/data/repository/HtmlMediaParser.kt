package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.common.net.hasVideoFileExtension
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import java.net.URI

internal fun parseHtmlMedia(
    requestUrl: String,
    html: String,
): MediaDetectionResult {
    val documentBaseUrl = html.documentBaseUrl(requestUrl)
    val pageTitle =
        TITLE_REGEX
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.stripHtml()
            .orEmpty()

    val candidates = linkedMapOf<String, MediaCandidate>()
    VIDEO_ELEMENT_REGEX.findAll(html).forEach { match ->
        val attributes = match.groupValues[1]
        val body = match.groupValues[2]
        val thumbnailUrl = attributes.attribute("poster")?.let { resolveUrl(documentBaseUrl, it) }
        val sources =
            sequenceOf(attributes.attribute("src")) +
                SOURCE_TAG_REGEX.findAll(body).map { it.groupValues[1] }
        sources
            .filterNotNull()
            .mapNotNull { resolveUrl(documentBaseUrl, it) }
            .filter(String::hasVideoFileExtension)
            .forEach { mediaUrl ->
                candidates.putIfAbsent(
                    mediaUrl,
                    mediaUrl.toCandidate(mediaUrl.fileTitle(), null, null, thumbnailUrl),
                )
            }
    }
    (
        MEDIA_TAG_REGEX.findAll(html).map { it.groupValues[1] } +
            DIRECT_VIDEO_REGEX.findAll(html).map { it.value }
    ).mapNotNull { source -> resolveUrl(documentBaseUrl, source) }
        .filter(String::hasVideoFileExtension)
        .forEach { mediaUrl ->
            candidates.putIfAbsent(mediaUrl, mediaUrl.toCandidate(mediaUrl.fileTitle(), null, null))
        }
    return if (candidates.isEmpty()) {
        unsupported(UnsupportedReason.NO_MEDIA)
    } else {
        MediaDetectionResult.Success(
            pageTitle,
            candidates.values.mapIndexed { index, candidate ->
                if (OPAQUE_MEDIA_TITLE_REGEX.matches(candidate.title)) {
                    candidate.copy(
                        title =
                            pageTitle
                                .takeIf(
                                    String::isNotBlank,
                                )?.let { "${it.candidateCollectionTitle()} · ${index + 1}" }
                                .orEmpty(),
                    )
                } else {
                    candidate
                }
            },
        )
    }
}

private fun resolveUrl(
    baseUrl: String,
    source: String,
): String? = runCatching { URI(baseUrl).resolve(source.decodeHtmlAttribute().trim()).toHttpUrlOrNull() }.getOrNull()

private fun String.documentBaseUrl(responseUrl: String): String =
    BASE_TAG_REGEX
        .findAll(this)
        .mapNotNull { match -> match.groupValues[1].attribute("href") }
        .mapNotNull { href -> resolveUrl(responseUrl, href) }
        .firstOrNull()
        ?: responseUrl

private fun String.decodeHtmlAttribute(): String =
    HTML_ENTITY_REGEX.replace(this) { match ->
        when (val entity = match.groupValues[1]) {
            "#39" -> {
                "'"
            }

            else -> {
                val namedEntity =
                    when (entity.lowercase()) {
                        "amp" -> "&"
                        "quot" -> "\""
                        "apos" -> "'"
                        "lt" -> "<"
                        "gt" -> ">"
                        else -> null
                    }
                namedEntity ?: run {
                    val codePoint =
                        if (entity.startsWith("#x", ignoreCase = true)) {
                            entity.drop(2).toIntOrNull(HTML_HEX_RADIX)
                        } else {
                            entity.removePrefix("#").toIntOrNull()
                        }
                    codePoint?.takeIf(Character::isValidCodePoint)?.let(Character::toChars)?.concatToString()
                        ?: match.value
                }
            }
        }
    }

private fun String.attribute(name: String): String? =
    Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.get(1)

private fun String.candidateCollectionTitle(): String =
    substringBefore('·')
        .trim()
        .replace(LEADING_RESULT_COUNT_REGEX, "")
        .take(MAX_CANDIDATE_PAGE_TITLE_LENGTH)
        .trim()

private fun String.stripHtml(): String = replace(HTML_TAG_REGEX, "").trim()

private fun unsupported(reason: UnsupportedReason) = MediaDetectionResult.Unsupported(reason)

private val TITLE_REGEX =
    Regex(
        "<title[^>]*>(.*?)</title>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val BASE_TAG_REGEX = Regex("<base\\b([^>]*)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_ENTITY_REGEX = Regex("&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|amp|quot|apos|lt|gt);", RegexOption.IGNORE_CASE)
private val MEDIA_TAG_REGEX =
    Regex(
        "<(?:video|source)[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val VIDEO_ELEMENT_REGEX =
    Regex(
        "<video\\b([^>]*)>(.*?)</video>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val SOURCE_TAG_REGEX =
    Regex(
        "<source[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val DIRECT_VIDEO_REGEX =
    Regex("https?://[^\\s\"'<>]+\\.(?:mp4|webm|mov|m4v)(?:[?#][^\\s\"'<>]*)?", RegexOption.IGNORE_CASE)
private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val OPAQUE_MEDIA_TITLE_REGEX = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
private val LEADING_RESULT_COUNT_REGEX = Regex("^[\\d,+]+개의\\s+(?:최고의\\s+)?")
private const val MAX_CANDIDATE_PAGE_TITLE_LENGTH = 48
private const val HTML_HEX_RADIX = 16
