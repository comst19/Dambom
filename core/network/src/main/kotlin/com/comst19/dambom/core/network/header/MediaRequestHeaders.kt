package com.comst19.dambom.core.network.header

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

fun sanitizeMediaHeaders(headers: Map<String, String>): Map<String, String> =
    headers.entries
        .filter { (name, value) ->
            HEADER_NAME.matches(name) && name.lowercase() !in TRANSPORT_HEADERS &&
                value.none { it == '\r' || it == '\n' }
        }.associate { (name, value) -> name.lowercase() to value }

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
    val source = sourceUrl.toHttpUrl()
    val target = targetUrl.toHttpUrl()
    val sameOrigin = source.scheme == target.scheme && source.host == target.host && source.port == target.port
    return sanitizeMediaHeaders(headers).filterKeys { sameOrigin || it in SHARED_HEADERS }
}

fun mediaRequestClient(
    client: OkHttpClient,
    mediaUrl: String,
    headers: Map<String, String>,
): OkHttpClient {
    val origin = mediaUrl.toHttpUrl()
    val sanitized = sanitizeMediaHeaders(headers).toMutableMap().apply { putIfAbsent("accept", "*/*") }
    return client
        .newBuilder()
        .addNetworkInterceptor { chain ->
            val target = chain.request().url
            val sameOrigin = target.scheme == origin.scheme && target.host == origin.host && target.port == origin.port
            val request =
                chain
                    .request()
                    .newBuilder()
                    .apply {
                        sanitized.forEach { (name, value) ->
                            removeHeader(name)
                            if (sameOrigin || name in SHARED_HEADERS) header(name, value)
                        }
                    }.build()
            chain.proceed(request)
        }.build()
}

private val SHARED_HEADERS = setOf("user-agent", "referer", "origin", "accept", "accept-language")
private val TRANSPORT_HEADERS =
    setOf(
        "host",
        "connection",
        "content-length",
        "transfer-encoding",
        "accept-encoding",
        "range",
        "if-range",
        "proxy-authorization",
        "te",
        "trailer",
        "upgrade",
    )
private val HEADER_NAME = Regex("[!#$%&'*+.^_`|~0-9a-zA-Z-]+")
private val HEADER_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
