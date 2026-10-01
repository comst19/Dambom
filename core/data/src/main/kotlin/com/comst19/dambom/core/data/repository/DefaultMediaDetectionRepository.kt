package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.common.net.hasVideoFileExtension
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.data.mapper.toDomain
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
            return parseHtmlMedia(requestUrl, html)
        }
    }

private fun normalizeUrl(value: String): String? = runCatching { URI(value.trim()).toHttpUrlOrNull() }.getOrNull()

private fun buildRequest(url: String): Request =
    Request
        .Builder()
        .url(url)
        .header("Accept", "text/html,video/*;q=0.9,*/*;q=0.8")
        .build()

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

private fun unsupported(reason: UnsupportedReason) = MediaDetectionResult.Unsupported(reason)

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val MAX_HTML_BYTES = 2L * 1024L * 1024L
private const val HTML_READ_BUFFER_BYTES = 8L * 1024L
