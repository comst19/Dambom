package com.comst19.dambom.feature.web.webview

import android.content.Context
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.webkit.FakeWebResourceError
import android.webkit.RecordingSslErrorHandler
import android.webkit.SafeBrowsingResponse
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.feature.web.contract.WebTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class WebNavigationErrorAttributionTest {
    @Test
    fun `canonicalNavigationUrl normalizes scheme host default ports root slash and fragments`() {
        assertEquals(
            "https://expired.badssl.com/",
            canonicalNavigationUrl("HTTPS://Expired.BadSSL.com:443"),
        )
        assertEquals(
            "https://expired.badssl.com/",
            canonicalNavigationUrl("https://expired.badssl.com/#section"),
        )
        assertEquals(
            "http://example.com/watch?v=1",
            canonicalNavigationUrl("http://EXAMPLE.COM:80/watch?v=1#t=10"),
        )
        assertEquals(
            "https://example.com:8443/a%20b?q=1",
            canonicalNavigationUrl("https://example.com:8443/a b?q=1"),
        )
        assertEquals(
            "https://example.com/a/b",
            canonicalNavigationUrl("https://example.com/a/b"),
        )
        assertEquals(
            "https://example.com/a/b/",
            canonicalNavigationUrl("https://example.com/a/b/"),
        )
    }

    @Test
    fun `late network http ssl and safe browsing errors from previous navigation do not overwrite current page`() {
        val harness = createWebViewHarness(initialUrl = "https://first.example/page")
        val secondUrl = "https://second.example/page"
        try {
            harness.webView.loadUrl(secondUrl)
            harness.webView.webViewClient.onPageStarted(harness.webView, secondUrl, null)

            harness.webView.webViewClient.onReceivedError(
                harness.webView,
                FakeWebResourceRequest("https://first.example/page", isForMainFrame = true),
                FakeWebResourceError(WebViewClient.ERROR_CONNECT),
            )
            harness.webView.webViewClient.onReceivedHttpError(
                harness.webView,
                FakeWebResourceRequest("https://first.example/page", isForMainFrame = true),
                httpResponse(500),
            )
            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                sslHandler,
                sslError("https://first.example/page"),
            )
            val safeBrowsing = RecordingSafeBrowsingResponse()
            harness.webView.webViewClient.onSafeBrowsingHit(
                harness.webView,
                FakeWebResourceRequest("https://first.example/page", isForMainFrame = true),
                0,
                safeBrowsing,
            )

            assertNull(harness.currentFailure)
            assertEquals(1, sslHandler.cancelCount)
            assertEquals(0, sslHandler.proceedCount)
            assertEquals(listOf(true), safeBrowsing.backToSafetyCalls)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `pre page start failure for previous url clears when a different page starts`() {
        val harness = createWebViewHarness(initialUrl = "https://first.example/page")
        try {
            harness.webView.webViewClient.onReceivedHttpError(
                harness.webView,
                FakeWebResourceRequest("https://first.example/page", isForMainFrame = true),
                httpResponse(500),
            )
            assertEquals(WebNavigationFailure.HTTP_SERVER, harness.currentFailure)

            harness.webView.webViewClient.onPageStarted(harness.webView, "https://second.example/page", null)

            assertNull(harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `stale page finished from previous navigation does not drop pending failure attribution for new page`() {
        val harness = createWebViewHarness(initialUrl = "https://first.example/page")
        try {
            harness.webView.webViewClient.onPageStarted(harness.webView, "https://first.example/page", null)
            harness.webView.loadUrl("https://second.example/page")
            harness.webView.webViewClient.onPageFinished(harness.webView, "https://first.example/page")

            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                sslHandler,
                sslError("https://second.example/page"),
            )

            assertEquals(1, sslHandler.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `redirect target errors are attributed while stale pre redirect errors are ignored`() {
        val harness = createWebViewHarness(initialUrl = "http://start.example/go")
        val targetUrl = "https://redirected.example/final"
        try {
            harness.webView.webViewClient.shouldOverrideUrlLoading(
                harness.webView,
                FakeWebResourceRequest(targetUrl, isForMainFrame = true, isRedirect = true),
            )
            val staleSsl = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                staleSsl,
                sslError("http://start.example/go"),
            )
            assertEquals(1, staleSsl.cancelCount)
            assertNull(harness.currentFailure)

            val targetSsl = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                targetSsl,
                sslError(targetUrl),
            )
            assertEquals(1, targetSsl.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `post navigation from committed page observed via shouldInterceptRequest attributes ssl failure`() {
        val harness = createWebViewHarness(initialUrl = "http://start.example/form")
        val targetUrl = "https://bad-target.example/submit"
        try {
            harness.webView.webViewClient.onPageStarted(harness.webView, "http://start.example/form", null)
            harness.webView.webViewClient.onPageFinished(harness.webView, "http://start.example/form")
            harness.webView.webViewClient.shouldInterceptRequest(
                harness.webView,
                FakeWebResourceRequest(targetUrl, isForMainFrame = true, method = "POST"),
            )

            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                sslHandler,
                sslError(targetUrl),
            )

            assertEquals(1, sslHandler.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `hsts upgrade from http to https before commit attributes main frame ssl error`() {
        val harness = createWebViewHarness(initialUrl = "http://secure.example/watch")
        try {
            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                sslHandler,
                sslError("https://secure.example/watch"),
            )

            assertEquals(1, sslHandler.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `subresource ssl error cancels connection without covering active or loaded page`() {
        val harness = createWebViewHarness(initialUrl = "https://good.example/page")
        try {
            harness.webView.webViewClient.onPageStarted(harness.webView, "https://good.example/page", null)
            val loadingSubresourceHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                loadingSubresourceHandler,
                sslError("https://bad-cdn.example/image.png"),
            )
            assertEquals(1, loadingSubresourceHandler.cancelCount)
            assertNull(harness.currentFailure)

            harness.webView.webViewClient.onPageFinished(harness.webView, "https://good.example/page")
            val postLoadSubresourceHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                postLoadSubresourceHandler,
                sslError("https://bad-iframe.example/embed"),
            )
            assertEquals(1, postLoadSubresourceHandler.cancelCount)
            assertNull(harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `subresource https ssl error on committed http page with same host and path does not cover page`() {
        val harness = createWebViewHarness(initialUrl = "http://mixed.example/page")
        try {
            harness.webView.webViewClient.onPageStarted(harness.webView, "http://mixed.example/page", null)
            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                sslHandler,
                sslError("https://mixed.example/page"),
            )

            assertEquals(1, sslHandler.cancelCount)
            assertNull(harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `subframe navigation request does not replace main frame target for ssl attribution`() {
        val harness = createWebViewHarness(initialUrl = "https://good.example/page")
        try {
            harness.webView.webViewClient.onPageStarted(harness.webView, "https://good.example/page", null)
            harness.webView.webViewClient.shouldOverrideUrlLoading(
                harness.webView,
                FakeWebResourceRequest("https://bad-frame.example/frame", isForMainFrame = false),
            )
            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                sslHandler,
                sslError("https://bad-frame.example/frame"),
            )

            assertEquals(1, sslHandler.cancelCount)
            assertNull(harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `main document ssl error shows security failure and resists downgrade`() {
        val harness = createWebViewHarness(initialUrl = "https://expired.badssl.com")
        try {
            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                sslHandler,
                sslError("https://expired.badssl.com/"),
            )
            assertEquals(1, sslHandler.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)

            harness.webView.webViewClient.onPageStarted(harness.webView, "https://expired.badssl.com/", null)
            harness.webView.webViewClient.onReceivedError(
                harness.webView,
                FakeWebResourceRequest("https://expired.badssl.com/", isForMainFrame = true),
                FakeWebResourceError(WebViewClient.ERROR_UNKNOWN),
            )
            harness.webView.webViewClient.onPageFinished(harness.webView, "https://expired.badssl.com/")

            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `inactive webview still cancels ssl handler without updating failure state`() {
        var active = false
        val harness = createWebViewHarness(initialUrl = "https://expired.badssl.com", isActive = { active })
        try {
            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                sslHandler,
                sslError("https://expired.badssl.com/"),
            )

            assertEquals(1, sslHandler.cancelCount)
            assertNull(harness.currentFailure)
        } finally {
            active = true
            harness.webView.destroy()
        }
    }

    @Test
    fun `same url reload clears prior http failure when reload succeeds`() {
        val url = "https://retry.example/video"
        val harness = createWebViewHarness(initialUrl = url)
        try {
            harness.webView.webViewClient.onReceivedHttpError(
                harness.webView,
                FakeWebResourceRequest(url, isForMainFrame = true),
                httpResponse(500),
            )
            harness.webView.webViewClient.onPageStarted(harness.webView, url, null)
            harness.webView.webViewClient.onPageFinished(harness.webView, url)
            assertEquals(WebNavigationFailure.HTTP_SERVER, harness.currentFailure)

            harness.webView.reload()
            assertNull(harness.currentFailure)

            harness.webView.webViewClient.onPageStarted(harness.webView, url, null)
            harness.webView.webViewClient.onPageFinished(harness.webView, url)
            assertNull(harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `same url resubmit after pre page start ssl failure clears security error when reload succeeds`() {
        val url = "https://retry-ssl.example/page"
        val harness = createWebViewHarness(initialUrl = url)
        try {
            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(harness.webView, sslHandler, sslError(url))
            assertEquals(1, sslHandler.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)

            harness.webView.loadUrl(url)
            harness.webView.webViewClient.onPageStarted(harness.webView, url, null)
            harness.webView.webViewClient.onPageFinished(harness.webView, url)

            assertNull(harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `same url reload reports new failure when retry fails again`() {
        val url = "https://retry-fail.example/page"
        val harness = createWebViewHarness(initialUrl = url)
        try {
            harness.webView.webViewClient.onReceivedHttpError(
                harness.webView,
                FakeWebResourceRequest(url, isForMainFrame = true),
                httpResponse(500),
            )
            harness.webView.webViewClient.onPageStarted(harness.webView, url, null)
            harness.webView.webViewClient.onPageFinished(harness.webView, url)
            assertEquals(WebNavigationFailure.HTTP_SERVER, harness.currentFailure)

            harness.webView.reload()
            harness.webView.webViewClient.onReceivedHttpError(
                harness.webView,
                FakeWebResourceRequest(url, isForMainFrame = true),
                httpResponse(503),
            )
            harness.webView.webViewClient.onPageStarted(harness.webView, url, null)
            harness.webView.webViewClient.onPageFinished(harness.webView, url)

            assertEquals(WebNavigationFailure.HTTP_SERVER, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `same url reload after successful load reports ssl failure and ignores stale post finish error`() {
        val url = "https://stable.example/page"
        val harness = createWebViewHarness(initialUrl = url)
        try {
            harness.webView.webViewClient.onPageStarted(harness.webView, url, null)
            harness.webView.webViewClient.onPageFinished(harness.webView, url)

            harness.webView.webViewClient.onReceivedHttpError(
                harness.webView,
                FakeWebResourceRequest(url, isForMainFrame = true),
                httpResponse(500),
            )
            assertNull(harness.currentFailure)

            harness.webView.reload()
            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(harness.webView, sslHandler, sslError(url))

            assertEquals(1, sslHandler.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `javascript url execution does not clear failure or replace tracked navigation url`() {
        val url = "https://target.example/page"
        val harness = createWebViewHarness(initialUrl = url)
        try {
            harness.webView.loadUrl("javascript:void(0)")
            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(harness.webView, sslHandler, sslError(url))

            assertEquals(1, sslHandler.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)

            harness.webView.loadUrl("javascript:window.scrollTo(0,0)")
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `stale io thread shouldInterceptRequest from previous navigation does not hijack ssl attribution`() {
        val harness = createWebViewHarness(initialUrl = "https://first.example/page")
        try {
            harness.webView.loadUrl("https://second.example/page")
            harness.webView.webViewClient.shouldInterceptRequest(
                harness.webView,
                FakeWebResourceRequest("https://first.example/page", isForMainFrame = true),
            )

            val staleSsl = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                staleSsl,
                sslError("https://first.example/page"),
            )
            assertEquals(1, staleSsl.cancelCount)
            assertNull(harness.currentFailure)

            val currentSsl = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                currentSsl,
                sslError("https://second.example/page"),
            )
            assertEquals(1, currentSsl.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `late shouldInterceptRequest from previous page after new intercept does not hijack attribution`() {
        val firstUrl = "https://first.example/page"
        val secondUrl = "https://second.example/page"
        val harness = createWebViewHarness(initialUrl = firstUrl)
        try {
            harness.webView.loadUrl(secondUrl)
            harness.webView.webViewClient.shouldInterceptRequest(
                harness.webView,
                FakeWebResourceRequest(secondUrl, isForMainFrame = true),
            )
            harness.webView.webViewClient.shouldInterceptRequest(
                harness.webView,
                FakeWebResourceRequest(firstUrl, isForMainFrame = true),
            )

            val staleSsl = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                staleSsl,
                sslError(firstUrl),
            )
            assertEquals(1, staleSsl.cancelCount)
            assertNull(harness.currentFailure)

            harness.webView.webViewClient.onReceivedHttpError(
                harness.webView,
                FakeWebResourceRequest(secondUrl, isForMainFrame = true),
                httpResponse(500),
            )
            assertEquals(WebNavigationFailure.HTTP_SERVER, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `late onPageStarted from previous navigation after new load request does not clear pending target`() {
        val firstUrl = "https://first.example/page"
        val secondUrl = "https://second.example/page"
        val harness = createWebViewHarness(initialUrl = firstUrl)
        try {
            harness.webView.loadUrl(secondUrl)
            harness.webView.webViewClient.onPageStarted(harness.webView, firstUrl, null)

            val staleSsl = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                staleSsl,
                sslError(firstUrl),
            )
            assertEquals(1, staleSsl.cancelCount)
            assertNull(harness.currentFailure)

            val currentSsl = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                currentSsl,
                sslError(secondUrl),
            )
            assertEquals(1, currentSsl.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `same url reload clears stale ssl failure once new navigation commits and finishes`() {
        val url = "https://retry-same.example/page"
        val harness = createWebViewHarness(initialUrl = url)
        try {
            harness.webView.webViewClient.onPageStarted(harness.webView, url, null)
            harness.webView.webViewClient.onPageFinished(harness.webView, url)

            harness.webView.reload()
            val staleSsl = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(harness.webView, staleSsl, sslError(url))
            assertEquals(1, staleSsl.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)

            harness.webView.webViewClient.onPageStarted(harness.webView, url, null)
            assertNull(harness.currentFailure)

            harness.webView.webViewClient.onPageFinished(harness.webView, url)
            assertNull(harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `main document ssl error without onPageStarted stays security through onPageFinished`() {
        val url = "https://expired.badssl.com/"
        val harness = createWebViewHarness(initialUrl = url)
        try {
            harness.webView.webViewClient.shouldInterceptRequest(
                harness.webView,
                FakeWebResourceRequest(url, isForMainFrame = true),
            )
            val sslHandler = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(harness.webView, sslHandler, sslError(url))
            harness.webView.webViewClient.onPageFinished(harness.webView, url)

            assertEquals(1, sslHandler.cancelCount)
            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }
}

internal fun createWebViewHarness(
    initialUrl: String? = null,
    isActive: () -> Boolean = { true },
): WebViewHarness {
    var currentFailure: WebNavigationFailure? = null
    val startedEvents = mutableListOf<Pair<String?, Long>>()
    val finishedEvents = mutableListOf<Pair<String?, Long>>()
    var historyChangeCount = 0
    val context = ApplicationProvider.getApplicationContext<Context>()
    val webView =
        createWebView(
            context = context,
            tab = WebTab(id = 1L, url = initialUrl),
            savedState = null,
            callbacks =
                WebViewCallbacks(
                    onPageStarted = { _, url, _, generation -> startedEvents.add(url to generation) },
                    onPageFinished = { _, url, _, generation -> finishedEvents.add(url to generation) },
                    onPageChanged = { _, _, _ -> },
                    onMediaRequest = { _, _, _ -> },
                    onProgress = {},
                    onNavigationFailure = { currentFailure = it },
                    onRendererGone = {},
                    onHistoryChanged = { _, _ -> historyChangeCount += 1 },
                    isActive = isActive,
                ),
        )
    return WebViewHarness(
        webView = webView,
        readFailure = { currentFailure },
        startedEvents = startedEvents,
        finishedEvents = finishedEvents,
        readHistoryChangeCount = { historyChangeCount },
    )
}

@Suppress("DEPRECATION")
internal fun sslError(url: String): SslError =
    SslError(
        SslError.SSL_EXPIRED,
        SslCertificate("CN=test.example", "CN=test.example", "", ""),
        url,
    )

internal fun httpResponse(statusCode: Int): WebResourceResponse =
    WebResourceResponse(
        "text/html",
        "UTF-8",
        statusCode,
        "Error",
        emptyMap(),
        ByteArrayInputStream(byteArrayOf()),
    )

internal class WebViewHarness(
    val webView: WebView,
    private val readFailure: () -> WebNavigationFailure?,
    val startedEvents: List<Pair<String?, Long>>,
    val finishedEvents: List<Pair<String?, Long>>,
    private val readHistoryChangeCount: () -> Int,
) {
    val currentFailure: WebNavigationFailure?
        get() = readFailure()

    val historyChangeCount: Int
        get() = readHistoryChangeCount()
}

internal class FakeWebResourceRequest(
    rawUrl: String,
    private val isForMainFrame: Boolean,
    private val isRedirect: Boolean = false,
    private val method: String = "GET",
) : WebResourceRequest {
    private val parsedUri: Uri = Uri.parse(rawUrl)

    override fun getUrl(): Uri = parsedUri

    override fun isForMainFrame(): Boolean = isForMainFrame

    override fun isRedirect(): Boolean = isRedirect

    override fun hasGesture(): Boolean = false

    override fun getMethod(): String = method

    override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
}

@Suppress("DEPRECATION")
private class RecordingSafeBrowsingResponse : SafeBrowsingResponse() {
    val backToSafetyCalls = mutableListOf<Boolean>()

    override fun showInterstitial(allowReporting: Boolean) = Unit

    override fun proceed(report: Boolean) = Unit

    override fun backToSafety(report: Boolean) {
        backToSafetyCalls.add(report)
    }
}
