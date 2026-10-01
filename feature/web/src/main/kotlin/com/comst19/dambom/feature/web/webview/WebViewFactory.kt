package com.comst19.dambom.feature.web.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.comst19.dambom.feature.web.contract.WebTab
import java.util.concurrent.atomic.AtomicLong

@SuppressLint("SetJavaScriptEnabled")
internal fun createWebView(
    context: Context,
    tab: WebTab,
    savedState: Bundle?,
    onPageStarted: (Long, String?, String?, Long) -> Unit,
    onPageFinished: (Long, String?, String?, Long) -> Unit,
    onPageChanged: (Long, String?, String?) -> Unit,
    onMediaRequest: (Long, Long, String) -> Unit,
    onProgress: (Int) -> Unit,
    onNavigationFailure: (WebNavigationFailure?) -> Unit,
    onRendererGone: (WebView) -> Unit,
): WebView =
    WebView(context).apply {
        val pageGeneration = AtomicLong()
        var pageStarted = false
        var failureBeforePageStart = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
        val mediaGuardInstalled = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (mediaGuardInstalled) {
            WebViewCompat.addDocumentStartJavaScript(this, WEB_MEDIA_GUARD_SCRIPT, setOf("*"))
        }
        webViewClient =
            object : WebViewClient() {
                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?,
                ) {
                    if (failureBeforePageStart) {
                        failureBeforePageStart = false
                    } else {
                        onNavigationFailure(null)
                    }
                    pageStarted = true
                    val generation = nextWebPageGeneration.incrementAndGet()
                    pageGeneration.set(generation)
                    onPageStarted(tab.id, url, view?.title, generation)
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?,
                ) {
                    pageStarted = false
                    onPageFinished(tab.id, url, view?.title, pageGeneration.get())
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    onMediaRequest(tab.id, pageGeneration.get(), url)
                    return if (shouldBlockWebVideo(url, mediaGuardInstalled)) blockedVideoResponse() else null
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    classifyWebNavigationFailure(
                        isForMainFrame = request?.isForMainFrame == true,
                        errorCode = error?.errorCode,
                    )?.let { failure ->
                        if (!pageStarted) failureBeforePageStart = true
                        onNavigationFailure(failure)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    classifyHttpNavigationFailure(
                        isForMainFrame = request?.isForMainFrame == true,
                        statusCode = errorResponse?.statusCode,
                    )?.let { failure ->
                        if (!pageStarted) failureBeforePageStart = true
                        onNavigationFailure(failure)
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?,
                ) {
                    handler?.cancel()
                    if (!pageStarted) failureBeforePageStart = true
                    onNavigationFailure(WebNavigationFailure.SECURITY)
                }

                override fun onSafeBrowsingHit(
                    view: WebView?,
                    request: WebResourceRequest?,
                    threatType: Int,
                    callback: SafeBrowsingResponse?,
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        callback?.backToSafety(true)
                    }
                    if (request?.isForMainFrame == true) {
                        if (!pageStarted) failureBeforePageStart = true
                        onNavigationFailure(WebNavigationFailure.SECURITY)
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?,
                ): Boolean {
                    view?.let { failedWebView ->
                        failedWebView.post { onRendererGone(failedWebView) }
                    }
                    return true
                }
            }
        webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(
                    view: WebView?,
                    newProgress: Int,
                ) {
                    onProgress(newProgress)
                }

                override fun onReceivedTitle(
                    view: WebView?,
                    title: String?,
                ) {
                    onPageChanged(tab.id, view?.url, title)
                }
            }
        if (savedState == null || restoreState(savedState) == null) {
            tab.url?.let(::loadUrl)
        }
    }

private val nextWebPageGeneration = AtomicLong()
