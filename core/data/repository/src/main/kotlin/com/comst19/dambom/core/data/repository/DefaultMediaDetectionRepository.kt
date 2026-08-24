package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

internal class DefaultMediaDetectionRepository
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val fxTwitterDetector: FxTwitterMediaDetector,
    ) : MediaDetectionRepository {
        override suspend fun detect(url: String): MediaDetectionResult =
            withContext(Dispatchers.IO) {
                val normalizedUrl = normalizeUrl(url) ?: return@withContext unsupported(UnsupportedReason.INVALID_URL)
                try {
                    fxTwitterDetector.detect(normalizedUrl)?.let { result ->
                        return@withContext result
                    }
                    client.newCall(buildRequest(normalizedUrl)).execute().use { response ->
                        when (response.code) {
                            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> unsupported(UnsupportedReason.ACCESS_RESTRICTED)
                            else -> detectResponse(normalizedUrl, response)
                        }
                    }
                } catch (_: IOException) {
                    unsupported(UnsupportedReason.NETWORK_ERROR)
                } catch (_: IllegalArgumentException) {
                    unsupported(UnsupportedReason.INVALID_URL)
                }
            }

        private fun detectResponse(
            requestUrl: String,
            response: okhttp3.Response,
        ): MediaDetectionResult {
            if (!response.isSuccessful) return unsupported(UnsupportedReason.NETWORK_ERROR)
            val body = response.body
            val contentType = body.contentType()?.toString()
            if (contentType?.startsWith("video/") == true || requestUrl.hasVideoExtension()) {
                return MediaDetectionResult.Success(
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
            if (contentType?.contains("html") != true) {
                return unsupported(UnsupportedReason.UNSUPPORTED_FORMAT)
            }
            val html = body.string()
            val pageTitle =
                TITLE_REGEX
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.stripHtml()
                    .orEmpty()
                    .ifBlank { "웹 영상" }
            val candidates =
                (
                    MEDIA_TAG_REGEX.findAll(html).map { it.groupValues[1] } +
                        DIRECT_VIDEO_REGEX.findAll(html).map { it.value }
                ).mapNotNull { source -> resolveUrl(requestUrl, source) }
                    .filter(String::hasVideoExtension)
                    .distinct()
                    .map { mediaUrl -> mediaUrl.toCandidate(mediaUrl.fileTitle(), null, null) }
                    .toList()
            return if (candidates.isEmpty()) {
                unsupported(UnsupportedReason.NO_MEDIA)
            } else {
                MediaDetectionResult.Success(pageTitle, candidates)
            }
        }
    }

private fun normalizeUrl(value: String): String? =
    runCatching {
        val uri = URI(value.trim())
        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) uri.toString() else null
    }.getOrNull()

private fun buildRequest(url: String): Request =
    Request
        .Builder()
        .url(url)
        .header("Accept", "text/html,video/*;q=0.9,*/*;q=0.8")
        .build()

private fun resolveUrl(
    baseUrl: String,
    source: String,
): String? = runCatching { URI(baseUrl).resolve(source.trim()).toString() }.getOrNull()

private fun String.hasVideoExtension(): Boolean =
    VIDEO_EXTENSIONS.any { extension -> substringBefore('?').endsWith(extension, ignoreCase = true) }

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
): MediaCandidate =
    MediaCandidate(
        id = MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) },
        url = this,
        title = title,
        mimeType = mimeType,
        contentLength = contentLength,
    )

private fun String.stripHtml(): String = replace(HTML_TAG_REGEX, "").trim()

private fun unsupported(reason: UnsupportedReason) = MediaDetectionResult.Unsupported(reason)

private val TITLE_REGEX = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val MEDIA_TAG_REGEX =
    Regex(
        "<(?:video|source)[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
private val DIRECT_VIDEO_REGEX =
    Regex("https?://[^\\s\"'<>]+\\.(?:mp4|webm|mov|m4v)(?:\\?[^\\s\"'<>]*)?", RegexOption.IGNORE_CASE)
private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".mov", ".m4v")
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
