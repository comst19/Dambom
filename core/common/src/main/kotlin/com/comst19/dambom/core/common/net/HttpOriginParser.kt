package com.comst19.dambom.core.common.net

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress

internal fun hasSameHttpOrigin(
    sourceUrl: String,
    targetUrl: String,
): Boolean {
    val source = parseHttpOrigin(sourceUrl)
    val target = parseHttpOrigin(targetUrl)
    return source != null && source == target
}

@Suppress("ReturnCount")
private fun parseHttpOrigin(url: String): HttpOrigin? {
    val trimmed = url.trim()
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = trimmed.substring(0, schemeEnd).lowercase()
    val defaultPort =
        when (scheme) {
            "http" -> HTTP_DEFAULT_PORT
            "https" -> HTTPS_DEFAULT_PORT
            else -> return null
        }
    val authorityStart = schemeEnd + SCHEME_SEPARATOR_LENGTH
    val authorityEnd =
        trimmed.indexOfAny(charArrayOf('/', '?', '#'), startIndex = authorityStart).let {
            if (it == -1) trimmed.length else it
        }
    val authority = trimmed.substring(authorityStart, authorityEnd)
    val hostPort = authority.substringAfterLast('@')
    val parsedHostPort = splitHostAndPort(hostPort, defaultPort) ?: return null
    val canonicalHost = canonicalizeHost(parsedHostPort.first) ?: return null
    return HttpOrigin(scheme = scheme, host = canonicalHost, port = parsedHostPort.second)
}

@Suppress("ReturnCount")
private fun splitHostAndPort(
    hostPort: String,
    defaultPort: Int,
): Pair<String, Int>? {
    if (hostPort.isEmpty()) return null
    if (hostPort.startsWith('[')) {
        val closing = hostPort.indexOf(']')
        if (closing <= 1) return null
        val host = hostPort.substring(0, closing + 1)
        val remainder = hostPort.substring(closing + 1)
        return when {
            remainder.isEmpty() -> host to defaultPort
            remainder.startsWith(':') -> parsePort(remainder.substring(1), defaultPort)?.let { host to it }
            else -> null
        }
    }
    val colon = hostPort.lastIndexOf(':')
    if (colon == -1) return hostPort to defaultPort
    val port = parsePort(hostPort.substring(colon + 1), defaultPort) ?: return null
    return hostPort.substring(0, colon) to port
}

private fun parsePort(
    value: String,
    defaultPort: Int,
): Int? =
    if (value.isEmpty()) {
        defaultPort
    } else {
        value.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }
    }

@Suppress("ReturnCount")
private fun canonicalizeHost(rawHost: String): String? {
    if (rawHost.isEmpty()) return null
    if (rawHost.startsWith('[') && rawHost.endsWith(']')) {
        val literal = rawHost.substring(1, rawHost.length - 1)
        if (literal.isEmpty() || !IPV6_LITERAL_CHARS.matches(literal)) return null
        return runCatching {
            when (val address = InetAddress.getByName(rawHost)) {
                is Inet6Address -> formatCanonicalIpv6(address.address)
                else -> address.hostAddress?.lowercase()
            }
        }.getOrNull()
    }
    return runCatching {
        IDN
            .toASCII(rawHost.lowercase())
            .lowercase()
            .takeIf { it.isNotBlank() && it.none(Char::isWhitespace) }
    }.getOrNull()
}

private fun formatCanonicalIpv6(bytes: ByteArray): String {
    val groups =
        IntArray(IPV6_GROUP_COUNT) { index ->
            ((bytes[index * 2].toInt() and BYTE_MASK) shl BYTE_BITS) or
                (bytes[index * 2 + 1].toInt() and BYTE_MASK)
        }
    var bestStart = -1
    var bestLength = 0
    var index = 0
    while (index < groups.size) {
        val runStart = index
        while (index < groups.size && groups[index] == 0) index++
        val runLength = index - runStart
        if (runLength > bestLength && runLength >= MIN_IPV6_COMPRESS_RUN) {
            bestStart = runStart
            bestLength = runLength
        }
        index++
    }
    return buildIpv6String(groups, bestStart, bestLength)
}

private fun buildIpv6String(
    groups: IntArray,
    bestStart: Int,
    bestLength: Int,
): String =
    buildString {
        var i = 0
        while (i < groups.size) {
            if (i == bestStart) {
                append("::")
                i += bestLength
            } else {
                if (i > 0 && i != bestStart + bestLength) append(':')
                append(groups[i].toString(HEX_RADIX))
                i++
            }
        }
    }

private data class HttpOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
)

private const val SCHEME_SEPARATOR_LENGTH = 3
private const val HTTP_DEFAULT_PORT = 80
private const val HTTPS_DEFAULT_PORT = 443
private const val MIN_PORT = 1
private const val MAX_PORT = 65_535
private const val IPV6_GROUP_COUNT = 8
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8
private const val MIN_IPV6_COMPRESS_RUN = 2
private const val HEX_RADIX = 16

private val IPV6_LITERAL_CHARS = Regex("[0-9a-fA-F:.]+")
