package com.comst19.dambom.feature.web.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
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
    onRendererGone: () -> Unit,
): WebView =
    WebView(context).apply {
        val pageGeneration = AtomicLong()
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
                    onNavigationFailure(null)
                    val generation = nextWebPageGeneration.incrementAndGet()
                    pageGeneration.set(generation)
                    onPageStarted(tab.id, url, view?.title, generation)
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?,
                ) {
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
                    )?.let(onNavigationFailure)
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?,
                ): Boolean {
                    view?.post(onRendererGone)
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

internal enum class WebNavigationFailure {
    CONNECTION,
}

internal fun classifyWebNavigationFailure(
    isForMainFrame: Boolean,
    errorCode: Int?,
): WebNavigationFailure? = if (isForMainFrame && errorCode != null) WebNavigationFailure.CONNECTION else null

private val nextWebPageGeneration = AtomicLong()
