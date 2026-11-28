package com.comst19.dambom.core.network.header

import com.comst19.dambom.core.common.net.isSharedMediaHeader
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import com.comst19.dambom.core.common.net.sanitizeMediaHeaders as filterMediaHeaders

fun sanitizeMediaHeaders(headers: Map<String, String>): Map<String, String> = filterMediaHeaders(headers)

fun encodeMediaHeaders(headers: Map<String, String>): String {
    val sanitized = sanitizeMediaHeaders(headers)
    return Json.encodeToString(HEADER_SERIALIZER, sanitized)
}

fun decodeMediaHeaders(value: String): Map<String, String> {
    val decoded = Json.decodeFromString(HEADER_SERIALIZER, value)
    return sanitizeMediaHeaders(decoded)
}

fun mediaHeadersForUrl(
    headers: Map<String, String>,
    sourceUrl: String,
    targetUrl: String,
): Map<String, String> {
    val source = sourceUrl.toHttpUrlOrNull()
    val target = targetUrl.toHttpUrlOrNull()
    val sameOrigin = source != null && target != null && hasSameOrigin(source, target)
    return sanitizeMediaHeaders(headers).filterKeys { sameOrigin || isSharedMediaHeader(it) }
}

fun mediaRequestClient(
    client: OkHttpClient,
    mediaUrl: String,
    headers: Map<String, String>,
): OkHttpClient {
    val origin = mediaUrl.toHttpUrl()
    val sanitized =
        sanitizeMediaHeaders(headers).toMutableMap().apply {
            putIfAbsent("accept", "*/*")
            putIfAbsent("user-agent", "Dambom")
        }
    return client
        .newBuilder()
        .addNetworkInterceptor { chain ->
            val target = chain.request().url
            val sameOrigin = hasSameOrigin(origin, target)
            val request =
                chain
                    .request()
                    .newBuilder()
                    .url(inheritPlaylistQuery(origin, target))
                    .apply {
                        sanitized.forEach { (name, value) ->
                            removeHeader(name)
                            if (sameOrigin || isSharedMediaHeader(name)) header(name, value)
                        }
                    }.build()
            chain.proceed(request)
        }.build()
}

internal fun inheritPlaylistQuery(
    source: HttpUrl,
    target: HttpUrl,
): HttpUrl {
    val knownHost =
        source.host.endsWith(".pstatic.net") || source.host.endsWith(".daum.net") ||
            source.host.endsWith("-naver-vod.akamaized.net")
    val sameOrigin = hasSameOrigin(source, target)
    val needsQuery =
        source.encodedQuery != null && target.encodedQuery == null &&
            target.encodedPath.endsWith(".m3u8", ignoreCase = true)
    return if (knownHost && sameOrigin && needsQuery) {
        target.newBuilder().encodedQuery(source.encodedQuery).build()
    } else {
        target
    }
}

private fun hasSameOrigin(
    source: HttpUrl,
    target: HttpUrl,
): Boolean = source.scheme == target.scheme && source.host == target.host && source.port == target.port

private val HEADER_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
