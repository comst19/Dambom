package com.comst19.dambom.feature.web.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
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
import com.comst19.dambom.core.domain.model.MediaResourceRequest
import com.comst19.dambom.feature.web.contract.WebHistoryState
import com.comst19.dambom.feature.web.contract.WebTab
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal fun WebView.historyState(): WebHistoryState =
    WebHistoryState(
        canGoBack = canGoBack(),
        canGoForward = canGoForward(),
    )

@SuppressLint("SetJavaScriptEnabled")
internal fun createWebView(
    context: Context,
    tab: WebTab,
    savedState: Bundle?,
    onPageStarted: (Long, String?, String?, Long) -> Unit,
    onPageFinished: (Long, String?, String?, Long) -> Unit,
    onPageChanged: (Long, String?, String?) -> Unit,
    onMediaRequest: (Long, Long, MediaResourceRequest) -> Unit,
    onProgress: (Int) -> Unit,
    onNavigationFailure: (WebNavigationFailure?) -> Unit,
    onRendererGone: (WebView) -> Unit,
): WebView =
    createWebView(
        context = context,
        tab = tab,
        savedState = savedState,
        callbacks =
            WebViewCallbacks(
                onPageStarted = onPageStarted,
                onPageFinished = onPageFinished,
                onPageChanged = onPageChanged,
                onMediaRequest = onMediaRequest,
                onProgress = onProgress,
                onNavigationFailure = onNavigationFailure,
                onRendererGone = onRendererGone,
            ),
    )

@SuppressLint("SetJavaScriptEnabled")
internal fun createWebView(
    context: Context,
    tab: WebTab,
    savedState: Bundle?,
    callbacks: WebViewCallbacks,
): WebView {
    val navigationTracker = WebNavigationErrorTracker()
    return TrackedWebView(context, navigationTracker, callbacks).apply {
        val pageGeneration = AtomicLong()
        val defaultUserAgent = settings.userAgentString
        val mediaUserAgent = AtomicReference(defaultUserAgent)
        var currentPageUrl = tab.url
        var pageStarted = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = true
        settings.safeBrowsingEnabled = true
        val mediaGuardInstalled =
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(this, WEB_MEDIA_GUARD_SCRIPT, setOf("*"))
                true
            } else {
                false
            }
        installWebMediaObserver(this, tab.id, pageGeneration) { tabId, generation, request ->
            if (!callbacks.isActive()) return@installWebMediaObserver
            val enriched =
                request.withBrowserContext(
                    mediaUserAgent.get(),
                    CookieManager.getInstance().getCookie(request.url),
                )
            callbacks.onMediaRequest(tabId, generation, enriched)
        }
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val isForMainFrame = request?.isForMainFrame != false
                    val requestUrl = request?.url?.toString()
                    val allowed = shouldAllowWebNavigationUrl(requestUrl, isForMainFrame)
                    if (allowed && isForMainFrame && callbacks.isActive()) {
                        if (navigationTracker.onLoadRequested(requestUrl)) {
                            callbacks.onNavigationFailure(null)
                        }
                    }
                    return !allowed
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?,
                ) {
                    if (!callbacks.isActive()) return
                    val resolution = navigationTracker.onPageStarted(url)
                    if (resolution == PageStartResolution.IGNORED) return
                    val desiredUserAgent = siteMediaUserAgent(url.orEmpty(), defaultUserAgent)
                    mediaUserAgent.set(desiredUserAgent)
                    if (view != null && url != null && view.settings.userAgentString != desiredUserAgent) {
                        view.settings.userAgentString = desiredUserAgent
                    }
                    if (resolution == PageStartResolution.ACCEPTED_CLEAR_FAILURE) {
                        callbacks.onNavigationFailure(null)
                    }
                    pageStarted = true
                    if (url != null) currentPageUrl = url
                    val generation = nextWebPageGeneration.incrementAndGet()
                    pageGeneration.set(generation)
                    callbacks.onPageStarted(tab.id, url, view?.title, generation)
                    view?.let { callbacks.onHistoryChanged(it, it.historyState()) }
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?,
                ) {
                    if (!callbacks.isActive()) return
                    if (!navigationTracker.onPageFinished(url)) return
                    pageStarted = false
                    if (url != null) currentPageUrl = url
                    callbacks.onPageFinished(tab.id, url, view?.title, pageGeneration.get())
                    view?.let { callbacks.onHistoryChanged(it, it.historyState()) }
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean,
                ) {
                    if (!callbacks.isActive()) return
                    view?.let { callbacks.onHistoryChanged(it, it.historyState()) }
                    val visitedUrl =
                        (url ?: view?.url)?.takeIf {
                            it.startsWith("http://") || it.startsWith("https://")
                        }
                    navigationTracker.onVisitedHistoryUpdated(visitedUrl)
                    if (!pageStarted && visitedUrl != null && visitedUrl != currentPageUrl) {
                        currentPageUrl = visitedUrl
                        val generation = nextWebPageGeneration.incrementAndGet()
                        pageGeneration.set(generation)
                        callbacks.onPageStarted(tab.id, visitedUrl, view?.title, generation)
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString()?.takeIf { callbacks.isActive() } ?: return null
                    if (request.isForMainFrame) {
                        navigationTracker.onMainFrameRequestIntercepted(url)
                    }
                    if (request.method.equals("GET", true)) {
                        val mediaRequest =
                            MediaResourceRequest(url, request.requestHeaders, request.method)
                                .withBrowserContext(mediaUserAgent.get(), CookieManager.getInstance().getCookie(url))
                        callbacks.onMediaRequest(tab.id, pageGeneration.get(), mediaRequest)
                    }
                    return if (shouldBlockWebVideo(url, mediaGuardInstalled)) blockedVideoResponse() else null
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (!callbacks.isActive()) return
                    classifyWebNavigationFailure(
                        isForMainFrame = request?.isForMainFrame == true,
                        errorCode = error?.errorCode,
                    )?.let { failure ->
                        navigationTracker
                            .resolveMainFrameFailure(request?.url?.toString(), failure)
                            ?.let(callbacks.onNavigationFailure)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (!callbacks.isActive()) return
                    classifyHttpNavigationFailure(
                        isForMainFrame = request?.isForMainFrame == true,
                        statusCode = errorResponse?.statusCode,
                    )?.let { failure ->
                        navigationTracker
                            .resolveMainFrameFailure(request?.url?.toString(), failure)
                            ?.let(callbacks.onNavigationFailure)
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?,
                ) {
                    handler?.cancel()
                    if (!callbacks.isActive()) return
                    navigationTracker
                        .resolveSslFailure(error?.url)
                        ?.let(callbacks.onNavigationFailure)
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
                    if (!callbacks.isActive()) return
                    if (request?.isForMainFrame == true) {
                        navigationTracker
                            .resolveMainFrameFailure(request.url?.toString(), WebNavigationFailure.SECURITY)
                            ?.let(callbacks.onNavigationFailure)
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?,
                ): Boolean {
                    if (!callbacks.isActive()) return true
                    view?.let { failedWebView ->
                        failedWebView.post {
                            if (callbacks.isActive()) callbacks.onRendererGone(failedWebView)
                        }
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
                    if (!callbacks.isActive()) return
                    callbacks.onProgress(newProgress)
                }

                override fun onReceivedTitle(
                    view: WebView?,
                    title: String?,
                ) {
                    if (!callbacks.isActive()) return
                    callbacks.onPageChanged(tab.id, view?.url, title)
                }
            }
        val restored = savedState != null && restoreState(savedState) != null
        val initialUrl =
            if (restored) {
                url?.let {
                    currentPageUrl = it
                    navigationTracker.onRestoreState(it)
                }
                callbacks.pendingNavigationUrl
            } else {
                callbacks.pendingNavigationUrl ?: tab.url
            }
        initialUrl?.let { urlToLoad ->
            val userAgent = siteMediaUserAgent(urlToLoad, defaultUserAgent)
            mediaUserAgent.set(userAgent)
            settings.userAgentString = userAgent
            loadUrl(urlToLoad)
        }
    }
}

private class TrackedWebView(
    context: Context,
    private val navigationTracker: WebNavigationErrorTracker,
    private val callbacks: WebViewCallbacks,
) : WebView(context) {
    override fun loadUrl(url: String) {
        if (navigationTracker.onLoadRequested(url) && callbacks.isActive()) {
            callbacks.onNavigationFailure(null)
        }
        super.loadUrl(url)
    }

    override fun loadUrl(
        url: String,
        additionalHttpHeaders: MutableMap<String, String>,
    ) {
        if (navigationTracker.onLoadRequested(url) && callbacks.isActive()) {
            callbacks.onNavigationFailure(null)
        }
        super.loadUrl(url, additionalHttpHeaders)
    }

    override fun reload() {
        if (navigationTracker.onReloadRequested(url) && callbacks.isActive()) {
            callbacks.onNavigationFailure(null)
        }
        super.reload()
    }

    override fun goBack() {
        if (canGoBack()) {
            notifyHistoryNavigation(steps = -1)
        }
        super.goBack()
    }

    override fun goForward() {
        if (canGoForward()) {
            notifyHistoryNavigation(steps = 1)
        }
        super.goForward()
    }

    override fun goBackOrForward(steps: Int) {
        if (steps != 0 && canGoBackOrForward(steps)) {
            notifyHistoryNavigation(steps = steps)
        }
        super.goBackOrForward(steps)
    }

    private fun notifyHistoryNavigation(steps: Int) {
        val targetUrl =
            runCatching {
                val list = copyBackForwardList()
                val targetIndex = list.currentIndex + steps
                if (targetIndex in 0 until list.size) list.getItemAtIndex(targetIndex)?.url else null
            }.getOrNull()
        if (navigationTracker.onLoadRequested(targetUrl, allowBlankTarget = true) && callbacks.isActive()) {
            callbacks.onNavigationFailure(null)
        }
    }
}

private val nextWebPageGeneration = AtomicLong()
