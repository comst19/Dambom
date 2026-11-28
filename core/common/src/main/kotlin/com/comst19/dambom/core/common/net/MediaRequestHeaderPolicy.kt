package com.comst19.dambom.core.common.net

interface MediaHeaderPolicy {
    fun mediaHeadersForUrl(
        headers: Map<String, String>,
        sourceUrl: String,
        targetUrl: String,
    ): Map<String, String>

    fun resolveVariantHeaders(
        candidateUrl: String,
        candidateHeaders: Map<String, String>,
        variantUrl: String,
        variantHeaders: Map<String, String>,
    ): Map<String, String> {
        val hasOwnHeaders = variantHeaders.isNotEmpty()
        val sourceHeaders = if (hasOwnHeaders) variantHeaders else candidateHeaders
        val sourceUrl = if (hasOwnHeaders) variantUrl else candidateUrl
        return mediaHeadersForUrl(sourceHeaders, sourceUrl, variantUrl)
    }
}

object DefaultMediaHeaderPolicy : MediaHeaderPolicy {
    override fun mediaHeadersForUrl(
        headers: Map<String, String>,
        sourceUrl: String,
        targetUrl: String,
    ): Map<String, String> {
        val sameOrigin = hasSameHttpOrigin(sourceUrl, targetUrl)
        return sanitizeMediaHeaders(headers).filterKeys { sameOrigin || isSharedMediaHeader(it) }
    }
}

fun sanitizeMediaHeaders(headers: Map<String, String>): Map<String, String> =
    headers.entries
        .filter { (name, value) ->
            HEADER_NAME.matches(name) && name.lowercase() !in TRANSPORT_HEADERS &&
                value.none { it == '\r' || it == '\n' }
        }.associate { (name, value) -> name.lowercase() to value }

fun mediaHeadersForUrl(
    headers: Map<String, String>,
    sourceUrl: String,
    targetUrl: String,
): Map<String, String> = DefaultMediaHeaderPolicy.mediaHeadersForUrl(headers, sourceUrl, targetUrl)

fun resolveMediaVariantHeaders(
    candidateUrl: String,
    candidateHeaders: Map<String, String>,
    variantUrl: String,
    variantHeaders: Map<String, String>,
): Map<String, String> =
    DefaultMediaHeaderPolicy.resolveVariantHeaders(
        candidateUrl = candidateUrl,
        candidateHeaders = candidateHeaders,
        variantUrl = variantUrl,
        variantHeaders = variantHeaders,
    )

fun isSharedMediaHeader(name: String): Boolean = name.lowercase() in SHARED_HEADERS

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
