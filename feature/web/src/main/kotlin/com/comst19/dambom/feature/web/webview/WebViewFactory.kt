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
import com.comst19.dambom.feature.web.R
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

internal enum class WebNavigationFailure(
    val titleRes: Int,
    val descriptionRes: Int,
    val retryable: Boolean,
    val canOpenExternal: Boolean = true,
) {
    CONNECTION(
        R.string.web_connection_error_title,
        R.string.web_connection_error_description,
        retryable = true,
    ),
    TIMEOUT(
        R.string.web_timeout_error_title,
        R.string.web_timeout_error_description,
        retryable = true,
    ),
    UNSUPPORTED_URL(
        R.string.web_unsupported_url_error_title,
        R.string.web_unsupported_url_error_description,
        retryable = false,
    ),
    HTTP_CLIENT(
        R.string.web_access_error_title,
        R.string.web_access_error_description,
        retryable = false,
    ),
    HTTP_SERVER(
        R.string.web_server_error_title,
        R.string.web_server_error_description,
        retryable = true,
    ),
    SECURITY(
        R.string.web_security_error_title,
        R.string.web_security_error_description,
        retryable = false,
        canOpenExternal = false,
    ),
    UNKNOWN(
        R.string.web_unknown_error_title,
        R.string.web_unknown_error_description,
        retryable = false,
    ),
}

internal fun classifyWebNavigationFailure(
    isForMainFrame: Boolean,
    errorCode: Int?,
): WebNavigationFailure? {
    if (!isForMainFrame || errorCode == null) return null
    return when (errorCode) {
        WebViewClient.ERROR_HOST_LOOKUP,
        WebViewClient.ERROR_CONNECT,
        WebViewClient.ERROR_IO,
        -> WebNavigationFailure.CONNECTION

        WebViewClient.ERROR_TIMEOUT -> WebNavigationFailure.TIMEOUT

        WebViewClient.ERROR_UNSUPPORTED_SCHEME,
        WebViewClient.ERROR_BAD_URL,
        WebViewClient.ERROR_REDIRECT_LOOP,
        WebViewClient.ERROR_FILE,
        WebViewClient.ERROR_FILE_NOT_FOUND,
        -> WebNavigationFailure.UNSUPPORTED_URL

        WebViewClient.ERROR_AUTHENTICATION,
        WebViewClient.ERROR_PROXY_AUTHENTICATION,
        WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME,
        WebViewClient.ERROR_TOO_MANY_REQUESTS,
        -> WebNavigationFailure.HTTP_CLIENT

        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
        WebViewClient.ERROR_UNSAFE_RESOURCE,
        -> WebNavigationFailure.SECURITY

        else -> WebNavigationFailure.UNKNOWN
    }
}

internal fun classifyHttpNavigationFailure(
    isForMainFrame: Boolean,
    statusCode: Int?,
): WebNavigationFailure? {
    if (!isForMainFrame || statusCode == null) return null
    return when {
        statusCode == HTTP_REQUEST_TIMEOUT -> WebNavigationFailure.TIMEOUT
        statusCode in HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END -> WebNavigationFailure.HTTP_SERVER
        statusCode in HTTP_CLIENT_ERROR_START..HTTP_CLIENT_ERROR_END -> WebNavigationFailure.HTTP_CLIENT
        else -> null
    }
}

private val nextWebPageGeneration = AtomicLong()

private const val HTTP_CLIENT_ERROR_START = 400
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_CLIENT_ERROR_END = 499
private const val HTTP_SERVER_ERROR_START = 500
private const val HTTP_SERVER_ERROR_END = 599
