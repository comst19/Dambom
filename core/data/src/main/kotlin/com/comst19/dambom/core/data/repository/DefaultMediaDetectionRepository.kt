package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.common.net.hasVideoFileExtension
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.data.mapper.toDomain
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkDataSource
import com.comst19.dambom.core.network.okhttp.executeCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

internal class DefaultMediaDetectionRepository
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val fxTwitterNetworkDataSource: FxTwitterNetworkDataSource,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : MediaDetectionRepository {
        override suspend fun detect(url: String): MediaDetectionResult =
            withContext(ioDispatcher) {
                val normalizedUrl = normalizeUrl(url) ?: return@withContext unsupported(UnsupportedReason.INVALID_URL)
                try {
                    fxTwitterNetworkDataSource.detect(normalizedUrl)?.let { result ->
                        return@withContext result.toDomain()
                    }
                    client.newCall(buildRequest(normalizedUrl)).executeCancellable { response ->
                        when (response.code) {
                            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> unsupported(UnsupportedReason.ACCESS_RESTRICTED)
                            else -> detectResponse(response)
                        }
                    }
                } catch (_: IOException) {
                    unsupported(UnsupportedReason.NETWORK_ERROR)
                } catch (_: IllegalArgumentException) {
                    unsupported(UnsupportedReason.INVALID_URL)
                }
            }

        private fun detectResponse(response: okhttp3.Response): MediaDetectionResult {
            if (!response.isSuccessful) return unsupported(UnsupportedReason.NETWORK_ERROR)
            val requestUrl = response.request.url.toString()
            val body = response.body
            val contentType = body.contentType()?.toString()
            return when {
                contentType?.startsWith("video/") == true || requestUrl.hasVideoFileExtension() -> {
                    MediaDetectionResult.Success(
                        pageTitle = requestUrl.fileTitle(),
                        candidates =
                            listOf(
                                requestUrl.toCandidate(
                                    title = requestUrl.fileTitle(),
                                    mimeType = contentType,
                                    contentLength = body.contentLength().takeIf { it >= 0L },
                                ),
                            ),
                    )
                }

                contentType?.contains("html") == true -> {
                    detectHtmlResponse(requestUrl, body)
                }

                else -> {
                    unsupported(UnsupportedReason.UNSUPPORTED_FORMAT)
                }
            }
        }

        private fun detectHtmlResponse(
            requestUrl: String,
            body: ResponseBody,
        ): MediaDetectionResult {
            val html = body.readUtf8UpTo(MAX_HTML_BYTES) ?: return unsupported(UnsupportedReason.UNSUPPORTED_FORMAT)
            val documentBaseUrl = html.documentBaseUrl(requestUrl)
            val pageTitle =
                TITLE_REGEX
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.stripHtml()
                    .orEmpty()
                    .ifBlank { "웹 영상" }
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
                            candidate.copy(title = "${pageTitle.candidateCollectionTitle()} · ${index + 1}")
                        } else {
                            candidate
                        }
                    },
                )
            }
        }
    }

private fun normalizeUrl(value: String): String? = runCatching { URI(value.trim()).toHttpUrlOrNull() }.getOrNull()

private fun buildRequest(url: String): Request =
    Request
        .Builder()
        .url(url)
        .header("Accept", "text/html,video/*;q=0.9,*/*;q=0.8")
        .build()

private fun resolveUrl(
    baseUrl: String,
    source: String,
): String? = runCatching { URI(baseUrl).resolve(source.decodeHtmlAttribute().trim()).toHttpUrlOrNull() }.getOrNull()

private fun URI.toHttpUrlOrNull(): String? =
    if (scheme.equals("http", true) || scheme.equals("https", true)) {
        toString()
    } else {
        null
    }

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

private fun String.fileTitle(): String =
    runCatching {
        URI(this)
            .path
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .ifBlank { "웹 영상" }
    }.getOrDefault("웹 영상")

private fun String.toCandidate(
    title: String,
    mimeType: String?,
    contentLength: Long?,
    thumbnailUrl: String? = null,
): MediaCandidate =
    MediaCandidate(
        id = MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) },
        url = this,
        title = title,
        mimeType = mimeType,
        contentLength = contentLength,
        thumbnailUrl = thumbnailUrl,
    )

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

private fun ResponseBody.readUtf8UpTo(limit: Long): String? {
    if (contentLength() > limit) return null
    val buffer = Buffer()
    val source = source()
    while (buffer.size <= limit) {
        val read = source.read(buffer, minOf(HTML_READ_BUFFER_BYTES, limit + 1L - buffer.size))
        if (read == -1L) return buffer.readUtf8()
        if (buffer.size > limit) return null
    }
    return null
}

private val TITLE_REGEX = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
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
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val MAX_CANDIDATE_PAGE_TITLE_LENGTH = 48
private const val MAX_HTML_BYTES = 2L * 1024L * 1024L
private const val HTML_READ_BUFFER_BYTES = 8L * 1024L
private const val HTML_HEX_RADIX = 16
