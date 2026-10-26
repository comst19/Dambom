package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.common.net.hasVideoFileExtension
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.data.mapper.toDomain
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.network.fxtwitter.FxTwitterNetworkDataSource
import com.comst19.dambom.core.network.header.mediaHeadersForUrl
import com.comst19.dambom.core.network.header.mediaRequestClient
import com.comst19.dambom.core.network.header.sanitizeMediaHeaders
import com.comst19.dambom.core.network.okhttp.executeCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException
import java.net.URI
import javax.inject.Inject

internal class DefaultMediaDetectionRepository
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val fxTwitterNetworkDataSource: FxTwitterNetworkDataSource,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : MediaDetectionRepository {
        override suspend fun detect(url: String): MediaDetectionResult =
            detectMedia(
                url = url,
                headers = emptyMap(),
                useSiteMetadata = true,
            )

        override suspend fun detectRequest(
            url: String,
            headers: Map<String, String>,
        ): MediaDetectionResult = detectMedia(url, sanitizeMediaHeaders(headers), useSiteMetadata = false)

        private suspend fun detectMedia(
            url: String,
            headers: Map<String, String>,
            useSiteMetadata: Boolean,
        ): MediaDetectionResult =
            withContext(ioDispatcher) {
                val inputUrl = normalizeUrl(url) ?: return@withContext unsupported(UnsupportedReason.INVALID_URL)
                val normalizedUrl = if (useSiteMetadata) siteDetectionUrl(inputUrl) else inputUrl
                try {
                    if (useSiteMetadata) {
                        fxTwitterNetworkDataSource.detect(normalizedUrl)?.let { result ->
                            return@withContext result.toDomain()
                        }
                    }
                    detectWithPlayers(normalizedUrl, headers)
                } catch (_: IOException) {
                    unsupported(UnsupportedReason.NETWORK_ERROR)
                } catch (_: IllegalArgumentException) {
                    unsupported(UnsupportedReason.INVALID_URL)
                }
            }

        private suspend fun detectWithPlayers(
            url: String,
            headers: Map<String, String>,
        ): MediaDetectionResult {
            val page = fetchPage(url, headers)
            var result = page.result
            val pending = ArrayDeque(page.followUpUrls)
            val visited = mutableSetOf(url)
            var attempts = 0
            while (result !is MediaDetectionResult.Success && pending.isNotEmpty() && attempts < MAX_SITE_REQUESTS) {
                val target = pending.removeFirst()
                if (visited.add(target)) {
                    attempts++
                    val targetHeaders = mediaHeadersForUrl(headers, url, target)
                    val next =
                        try {
                            fetchPage(target, targetHeaders)
                        } catch (_: IOException) {
                            MediaPageDetection(unsupported(UnsupportedReason.NETWORK_ERROR))
                        }
                    if (next.result is MediaDetectionResult.Success) result = next.result
                    next.followUpUrls.asReversed().forEach(pending::addFirst)
                }
            }
            return result
        }

        private suspend fun fetchPage(
            url: String,
            headers: Map<String, String>,
        ): MediaPageDetection =
            mediaRequestClient(client, url, headers).newCall(buildRequest(url)).executeCancellable { response ->
                val page =
                    when (response.code) {
                        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> {
                            MediaPageDetection(unsupported(UnsupportedReason.ACCESS_RESTRICTED))
                        }

                        else -> {
                            detectResponse(response)
                        }
                    }
                page.copy(result = page.result.withHeaders(headers, url))
            }

        private fun detectResponse(response: okhttp3.Response): MediaPageDetection {
            if (!response.isSuccessful) return MediaPageDetection(unsupported(UnsupportedReason.NETWORK_ERROR))
            val requestUrl = response.request.url.toString()
            val body = response.body
            val contentType = body.contentType()?.toString()
            val prefix = response.peekBody(MEDIA_PREFIX_BYTES).bytes()
            val streamMime = streamManifestMime(contentType, prefix)
            val result =
                when {
                    streamMime != null -> {
                        MediaPageDetection(detectStreamManifest(requestUrl, streamMime, body))
                    }

                    contentType?.startsWith("video/") == true ||
                        (requestUrl.hasVideoFileExtension() && contentType?.contains("html") != true) ||
                        isMp4Header(prefix) -> {
                        MediaPageDetection(
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
                            ),
                        )
                    }

                    contentType?.contains("html") == true || contentType?.contains("json") == true -> {
                        detectHtmlResponse(requestUrl, body)
                    }

                    else -> {
                        MediaPageDetection(unsupported(UnsupportedReason.UNSUPPORTED_FORMAT))
                    }
                }
            return result
        }

        private fun detectHtmlResponse(
            requestUrl: String,
            body: ResponseBody,
        ): MediaPageDetection {
            val html =
                body.readUtf8UpTo(MAX_HTML_BYTES)
                    ?: return MediaPageDetection(unsupported(UnsupportedReason.UNSUPPORTED_FORMAT))
            val result =
                parsePlayerConfig(requestUrl, html)
                    ?: parseSproutDownloads(requestUrl, html)
                    ?: parseHtmlMedia(requestUrl, html)
            return MediaPageDetection(result, playerFollowUpUrls(requestUrl, html))
        }
    }

private fun normalizeUrl(value: String): String? = runCatching { URI(value.trim()).toHttpUrlOrNull() }.getOrNull()

private fun buildRequest(url: String): Request =
    Request
        .Builder()
        .url(url)
        .header("Accept", "text/html,video/*;q=0.9,*/*;q=0.8")
        .build()

internal fun ResponseBody.readUtf8UpTo(limit: Long): String? {
    if (contentLength() > limit) return null
    val buffer = Buffer()
    val source = source()
    var exhausted = false
    while (buffer.size <= limit && !exhausted) {
        val read = source.read(buffer, minOf(HTML_READ_BUFFER_BYTES, limit + 1L - buffer.size))
        exhausted = read == -1L
    }
    return if (buffer.size <= limit) buffer.readUtf8() else null
}

private fun unsupported(reason: UnsupportedReason) = MediaDetectionResult.Unsupported(reason)

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val MAX_HTML_BYTES = 2L * 1024L * 1024L
private const val HTML_READ_BUFFER_BYTES = 8L * 1024L

private fun MediaDetectionResult.withHeaders(
    headers: Map<String, String>,
    sourceUrl: String,
): MediaDetectionResult =
    when (this) {
        is MediaDetectionResult.Success -> {
            copy(
                candidates =
                    candidates.map { candidate ->
                        candidate.copy(
                            requestHeaders = mediaHeadersForUrl(headers, sourceUrl, candidate.url),
                            variants =
                                candidate.variants.map { variant ->
                                    variant.copy(requestHeaders = mediaHeadersForUrl(headers, sourceUrl, variant.url))
                                },
                        )
                    },
            )
        }

        is MediaDetectionResult.Unsupported -> {
            this
        }
    }

private fun isMp4Header(bytes: ByteArray): Boolean =
    bytes.size >= MP4_HEADER_MIN_BYTES &&
        bytes.copyOfRange(MP4_BRAND_OFFSET, MP4_BRAND_END).toString(Charsets.US_ASCII) == "ftyp"

private const val MEDIA_PREFIX_BYTES = 512L
private const val MP4_HEADER_MIN_BYTES = 12
private const val MP4_BRAND_OFFSET = 4
private const val MP4_BRAND_END = 8

private data class MediaPageDetection(
    val result: MediaDetectionResult,
    val followUpUrls: List<String> = emptyList(),
)

private const val MAX_SITE_REQUESTS = 4
