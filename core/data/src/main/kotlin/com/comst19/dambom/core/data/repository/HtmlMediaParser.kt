package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.common.net.hasMediaExtension
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
        TITLE_OPEN_TAG_REGEX
            .find(html)
            ?.let { opening ->
                TITLE_CLOSE_TAG_REGEX.find(html, opening.range.last + 1)?.let { closing ->
                    html.substring(opening.range.last + 1, closing.range.first)
                }
            }?.stripHtml()
            .orEmpty()

    val candidates = linkedMapOf<String, MediaCandidate>()
    videoElements(html).forEach { (attributes, body) ->
        val thumbnailUrl = attributes.attribute(POSTER_ATTRIBUTE_REGEX)?.let { resolveUrl(documentBaseUrl, it) }
        val sources =
            sequenceOf(attributes.attribute(SRC_ATTRIBUTE_REGEX)) +
                SOURCE_TAG_REGEX.findAll(body).map { it.groupValues[1] }
        sources
            .filterNotNull()
            .mapNotNull { resolveUrl(documentBaseUrl, it) }
            .forEach { mediaUrl ->
                candidates.putIfAbsent(
                    mediaUrl,
                    mediaUrl.toCandidate(mediaUrl.fileTitle(), null, null, thumbnailUrl),
                )
            }
    }
    val embeddedText = html.replace("\\/", "/").replace("\\u002F", "/", ignoreCase = true)
    (
        MEDIA_TAG_REGEX.findAll(html).map { it.groupValues[1] } +
            DIRECT_VIDEO_REGEX.findAll(embeddedText).map { it.value }
    ).mapNotNull { source -> resolveUrl(documentBaseUrl, source) }
        .filter(String::hasMediaExtension)
        .forEach { mediaUrl ->
            candidates.putIfAbsent(mediaUrl, mediaUrl.toCandidate(mediaUrl.fileTitle(), null, null))
        }
    metadataCandidates(documentBaseUrl, html).forEach { candidate -> candidates.putIfAbsent(candidate.url, candidate) }
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

private fun videoElements(html: String): Sequence<Pair<String, String>> =
    sequence {
        var opening: MatchResult? = null
        VIDEO_TAG_REGEX.findAll(html).forEach { tag ->
            val previous = opening
            if (previous != null) {
                yield(previous.groupValues[2] to html.substring(previous.range.last + 1, tag.range.first))
            }
            opening = tag.takeIf { it.groupValues[1].isEmpty() }
        }
        opening?.let { tag -> yield(tag.groupValues[2] to html.substring(tag.range.last + 1)) }
    }

private fun resolveUrl(
    baseUrl: String,
    source: String,
): String? = runCatching { URI(baseUrl).resolve(source.decodeHtmlAttribute().trim()).toHttpUrlOrNull() }.getOrNull()

private fun String.documentBaseUrl(responseUrl: String): String =
    BASE_TAG_REGEX
        .findAll(this)
        .mapNotNull { match -> match.groupValues[1].attribute(HREF_ATTRIBUTE_REGEX) }
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

private fun String.attribute(pattern: Regex): String? = pattern.find(this)?.groupValues?.get(1)

private fun String.candidateCollectionTitle(): String =
    substringBefore('·')
        .trim()
        .replace(LEADING_RESULT_COUNT_REGEX, "")
        .take(MAX_CANDIDATE_PAGE_TITLE_LENGTH)
        .trim()

private fun String.stripHtml(): String = replace(HTML_TAG_REGEX, "").trim()

private fun unsupported(reason: UnsupportedReason) = MediaDetectionResult.Unsupported(reason)

private val POSTER_ATTRIBUTE_REGEX = Regex("""\bposter\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SRC_ATTRIBUTE_REGEX = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val HREF_ATTRIBUTE_REGEX = Regex("""\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val TITLE_OPEN_TAG_REGEX = Regex("<title\\b[^<>]*>", RegexOption.IGNORE_CASE)
private val TITLE_CLOSE_TAG_REGEX = Regex("</title>", RegexOption.IGNORE_CASE)
private val BASE_TAG_REGEX = Regex("<base\\b([^<>]*)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_ENTITY_REGEX = Regex("&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|amp|quot|apos|lt|gt);", RegexOption.IGNORE_CASE)
private val MEDIA_TAG_REGEX =
    Regex(
        "<(?:video|source)[^<>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val VIDEO_TAG_REGEX = Regex("<(/?)video\\b([^<>]*)>", RegexOption.IGNORE_CASE)
private val SOURCE_TAG_REGEX =
    Regex(
        "<source[^<>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val DIRECT_VIDEO_REGEX =
    Regex("https?://[^\\s\"'<>]+\\.(?:mp4|webm|mov|m4v|m3u8|mpd)(?:[?#][^\\s\"'<>]*)?", RegexOption.IGNORE_CASE)
private val HTML_TAG_REGEX = Regex("<[^<>]+>")
private val OPAQUE_MEDIA_TITLE_REGEX = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
private val LEADING_RESULT_COUNT_REGEX = Regex("^[\\d,+]+개의\\s+(?:최고의\\s+)?")
private const val MAX_CANDIDATE_PAGE_TITLE_LENGTH = 48
private const val HTML_HEX_RADIX = 16

private fun metadataCandidates(
    baseUrl: String,
    html: String,
): List<MediaCandidate> =
    META_TAG_REGEX
        .findAll(html)
        .mapNotNull { match ->
            val attributes = match.groupValues[1]
            val property = attributes.attribute(META_PROPERTY_REGEX)?.lowercase()
            val content = attributes.attribute(META_CONTENT_REGEX)
            if (property in VIDEO_META_PROPERTIES && content != null) {
                resolveUrl(baseUrl, content)?.let { it.toCandidate(it.fileTitle(), null, null) }
            } else {
                null
            }
        }.toList()

private val META_TAG_REGEX = Regex("<meta\\b([^<>]*)>", RegexOption.IGNORE_CASE)
private val META_PROPERTY_REGEX = Regex("""\b(?:property|name)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val META_CONTENT_REGEX = Regex("""\bcontent\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val VIDEO_META_PROPERTIES = setOf("og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream")
