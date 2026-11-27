package com.comst19.dambom.feature.web.webview

import java.net.URI
import java.util.Locale

internal enum class PageStartResolution {
    IGNORED,
    ACCEPTED_KEEP_FAILURE,
    ACCEPTED_CLEAR_FAILURE,
}

internal class WebNavigationErrorTracker {
    private val lock = Any()
    private var pendingMainFrameUrl: String? = null
    private var interceptedMainFrameUrl: String? = null
    private var supersededNavigationUrl: String? = null
    private var activePageUrl: String? = null
    private var pageStarted = false
    private var activeFailure: WebNavigationFailure? = null
    private var pendingFailureBeforePageStartUrl: String? = null
    private var recentSslFailureUrl: String? = null
    private var hasEverNavigated = false

    fun onLoadRequested(
        url: String?,
        allowBlankTarget: Boolean = false,
    ): Boolean {
        val canonical = canonicalNavigationUrl(url)
        val ignored =
            (!allowBlankTarget && canonical == null) ||
                canonical?.startsWith(JAVASCRIPT_SCHEME, ignoreCase = true) == true
        if (ignored) return false
        synchronized(lock) {
            beginMainFrameNavigationLocked(canonical)
        }
        return true
    }

    fun onReloadRequested(currentWebViewUrl: String?): Boolean {
        synchronized(lock) {
            val target =
                canonicalNavigationUrl(currentWebViewUrl)
                    ?: pendingMainFrameUrl
                    ?: activePageUrl
                    ?: interceptedMainFrameUrl
            beginMainFrameNavigationLocked(target)
        }
        return true
    }

    fun onRestoreState(restoredUrl: String?) {
        val canonical = canonicalNavigationUrl(restoredUrl) ?: return
        synchronized(lock) {
            hasEverNavigated = true
            activePageUrl = canonical
            pendingMainFrameUrl = canonical
            interceptedMainFrameUrl = null
            supersededNavigationUrl = null
        }
    }

    fun onMainFrameRequestIntercepted(url: String?) {
        val canonical = canonicalNavigationUrl(url) ?: return
        synchronized(lock) {
            hasEverNavigated = true
            val pending = pendingMainFrameUrl
            when {
                pending != null -> {
                    if (matchesPendingNavigationUrl(pending, canonical)) {
                        interceptedMainFrameUrl = canonical
                        pendingMainFrameUrl = canonical
                    }
                }

                pageStarted && canonical == supersededNavigationUrl -> {
                    Unit
                }

                else -> {
                    interceptedMainFrameUrl = canonical
                    pendingMainFrameUrl = canonical
                }
            }
        }
    }

    fun onPageStarted(url: String?): PageStartResolution {
        val canonical = canonicalNavigationUrl(url)
        return synchronized(lock) {
            hasEverNavigated = true
            val currentTargetUrl = pendingMainFrameUrl ?: if (pageStarted) activePageUrl else null
            if (isStaleSupersededPageStart(currentTargetUrl, supersededNavigationUrl, canonical)) {
                PageStartResolution.IGNORED
            } else {
                val matchesPendingFailure =
                    pendingFailureBeforePageStartUrl != null &&
                        (canonical == null || matchesPendingNavigationUrl(pendingFailureBeforePageStartUrl, canonical))
                pendingFailureBeforePageStartUrl = null
                if (!matchesPendingFailure) {
                    activeFailure = null
                }
                if (canonical != null && !matchesPendingNavigationUrl(recentSslFailureUrl, canonical)) {
                    recentSslFailureUrl = null
                }
                pageStarted = true
                if (canonical != null) {
                    activePageUrl = canonical
                }
                pendingMainFrameUrl = null
                interceptedMainFrameUrl = null
                if (matchesPendingFailure) {
                    PageStartResolution.ACCEPTED_KEEP_FAILURE
                } else {
                    PageStartResolution.ACCEPTED_CLEAR_FAILURE
                }
            }
        }
    }

    fun onPageFinished(url: String?): Boolean {
        val canonical = canonicalNavigationUrl(url)
        return synchronized(lock) {
            hasEverNavigated = true
            val expectedUrl = pendingMainFrameUrl ?: interceptedMainFrameUrl ?: activePageUrl
            if (expectedUrl != null && canonical != null && !matchesPendingNavigationUrl(expectedUrl, canonical)) {
                false
            } else {
                pageStarted = false
                if (canonical != null) {
                    activePageUrl = canonical
                }
                pendingMainFrameUrl = null
                interceptedMainFrameUrl = null
                supersededNavigationUrl = null
                pendingFailureBeforePageStartUrl = null
                recentSslFailureUrl = null
                true
            }
        }
    }

    fun onVisitedHistoryUpdated(url: String?) {
        val canonical = canonicalNavigationUrl(url) ?: return
        synchronized(lock) {
            if (!pageStarted) {
                if (pendingMainFrameUrl != null && matchesPendingNavigationUrl(pendingMainFrameUrl, canonical)) {
                    pendingMainFrameUrl = null
                    interceptedMainFrameUrl = null
                }
                if (pendingMainFrameUrl == null) {
                    activePageUrl = canonical
                }
            } else if (
                pendingMainFrameUrl == null &&
                hasSameOrigin(activePageUrl, canonical)
            ) {
                activePageUrl = canonical
                if (canonical == supersededNavigationUrl) {
                    supersededNavigationUrl = null
                }
            }
        }
    }

    fun resolveMainFrameFailure(
        requestUrl: String?,
        failure: WebNavigationFailure,
    ): WebNavigationFailure? {
        val canonical = canonicalNavigationUrl(requestUrl)
        return synchronized(lock) {
            val matches =
                !hasEverNavigated ||
                    matchesTrackedMainFrame(
                        pendingUrl = pendingMainFrameUrl,
                        interceptedUrl = interceptedMainFrameUrl,
                        activePageUrl = activePageUrl,
                        pageStarted = pageStarted,
                        canonical = canonical,
                    )
            if (!matches) {
                null
            } else {
                val effectiveFailure = resolveEffectiveFailure(activeFailure, recentSslFailureUrl, canonical, failure)
                activeFailure = effectiveFailure
                if (!pageStarted) {
                    pendingFailureBeforePageStartUrl = canonical ?: pendingMainFrameUrl ?: interceptedMainFrameUrl
                }
                effectiveFailure
            }
        }
    }

    fun resolveSslFailure(errorUrl: String?): WebNavigationFailure? {
        val canonical = canonicalNavigationUrl(errorUrl) ?: return null
        return synchronized(lock) {
            val hasPending = pendingMainFrameUrl != null || interceptedMainFrameUrl != null
            val matches =
                when {
                    hasPending -> matchesPendingOrIntercepted(pendingMainFrameUrl, interceptedMainFrameUrl, canonical)
                    pageStarted -> activePageUrl == canonical
                    else -> false
                }
            if (!matches) {
                null
            } else {
                activeFailure = WebNavigationFailure.SECURITY
                recentSslFailureUrl = canonical
                WebNavigationFailure.SECURITY
            }
        }
    }

    private fun beginMainFrameNavigationLocked(targetUrl: String?) {
        val inFlightUrl =
            pendingMainFrameUrl
                ?: interceptedMainFrameUrl
                ?: if (pageStarted) activePageUrl else null
        supersededNavigationUrl =
            inFlightUrl?.takeIf { targetUrl != null && !matchesPendingNavigationUrl(targetUrl, it) }
        hasEverNavigated = true
        pendingMainFrameUrl = targetUrl
        interceptedMainFrameUrl = null
        pageStarted = false
        pendingFailureBeforePageStartUrl = null
        activeFailure = null
        recentSslFailureUrl = null
    }
}

internal fun canonicalNavigationUrl(rawUrl: String?): String? {
    val trimmed = rawUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val withoutFragment = trimmed.substringBefore('#')
    val parsed = runCatching { URI(withoutFragment.replace(" ", "%20")) }.getOrNull()
    val scheme = parsed?.scheme?.lowercase(Locale.ROOT)
    val host = parsed?.host?.lowercase(Locale.ROOT)
    val isHttpScheme = scheme == HTTP_SCHEME || scheme == HTTPS_SCHEME
    return if (parsed == null || !isHttpScheme || host.isNullOrEmpty()) {
        withoutFragment
    } else {
        val userInfo = parsed.rawUserInfo?.let { "$it@" }.orEmpty()
        val port = normalizedPort(scheme, parsed.port)
        val path = parsed.rawPath.orEmpty().ifEmpty { "/" }
        val query = parsed.rawQuery?.let { "?$it" }.orEmpty()
        "$scheme://$userInfo$host$port$path$query"
    }
}

private fun normalizedPort(
    scheme: String,
    port: Int,
): String =
    when {
        port == -1 -> ""
        scheme == HTTP_SCHEME && port == DEFAULT_HTTP_PORT -> ""
        scheme == HTTPS_SCHEME && port == DEFAULT_HTTPS_PORT -> ""
        else -> ":$port"
    }

private fun matchesTrackedMainFrame(
    pendingUrl: String?,
    interceptedUrl: String?,
    activePageUrl: String?,
    pageStarted: Boolean,
    canonical: String?,
): Boolean {
    val hasPending = pendingUrl != null || interceptedUrl != null
    return when {
        canonical == null -> hasPending || pageStarted
        hasPending -> matchesPendingOrIntercepted(pendingUrl, interceptedUrl, canonical)
        pageStarted -> activePageUrl == null || activePageUrl == canonical
        else -> false
    }
}

private fun resolveEffectiveFailure(
    activeFailure: WebNavigationFailure?,
    recentSslFailureUrl: String?,
    canonical: String?,
    incoming: WebNavigationFailure,
): WebNavigationFailure {
    val preserveSecurity =
        activeFailure == WebNavigationFailure.SECURITY ||
            (canonical != null && matchesPendingNavigationUrl(recentSslFailureUrl, canonical))
    return if (preserveSecurity) WebNavigationFailure.SECURITY else incoming
}

private fun isStaleSupersededPageStart(
    currentTargetUrl: String?,
    supersededUrl: String?,
    actualUrl: String?,
): Boolean {
    if (currentTargetUrl == null || actualUrl == null) return false
    return actualUrl == supersededUrl && !matchesPendingNavigationUrl(currentTargetUrl, actualUrl)
}

private fun hasSameOrigin(
    firstUrl: String?,
    secondUrl: String?,
): Boolean {
    if (firstUrl == null || secondUrl == null) return false
    return originOf(firstUrl)?.let { it == originOf(secondUrl) } == true
}

private fun originOf(canonicalUrl: String): String? {
    val parsed = runCatching { URI(canonicalUrl) }.getOrNull() ?: return null
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    val host = parsed.host?.lowercase(Locale.ROOT)
    val isHttp = scheme == HTTP_SCHEME || scheme == HTTPS_SCHEME
    return if (!isHttp || host.isNullOrEmpty()) {
        null
    } else {
        "$scheme://$host${normalizedPort(scheme, parsed.port)}"
    }
}

private fun matchesPendingOrIntercepted(
    pendingUrl: String?,
    interceptedUrl: String?,
    actualUrl: String,
): Boolean = matchesPendingNavigationUrl(pendingUrl, actualUrl) || interceptedUrl == actualUrl

private fun matchesPendingNavigationUrl(
    expectedUrl: String?,
    actualUrl: String,
): Boolean =
    expectedUrl != null &&
        (
            expectedUrl == actualUrl ||
                (
                    expectedUrl.startsWith(HTTP_PREFIX) &&
                        actualUrl.startsWith(HTTPS_PREFIX) &&
                        expectedUrl.removePrefix(HTTP_PREFIX) == actualUrl.removePrefix(HTTPS_PREFIX)
                )
        )

private const val HTTP_SCHEME = "http"
private const val HTTPS_SCHEME = "https"
private const val HTTP_PREFIX = "http://"
private const val HTTPS_PREFIX = "https://"
private const val JAVASCRIPT_SCHEME = "javascript:"
private const val DEFAULT_HTTP_PORT = 80
private const val DEFAULT_HTTPS_PORT = 443
