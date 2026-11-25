package com.comst19.dambom.feature.web.webview

import android.os.Bundle
import android.webkit.WebView
import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.MediaResourceRequest
import com.comst19.dambom.feature.web.contract.WebHistoryState

internal data class WebViewCallbacks(
    val onPageStarted: (Long, String?, String?, Long) -> Unit,
    val onPageFinished: (Long, String?, String?, Long) -> Unit,
    val onPageChanged: (Long, String?, String?) -> Unit,
    val onMediaRequest: (Long, Long, MediaResourceRequest) -> Unit,
    val onProgress: (Int) -> Unit,
    val onNavigationFailure: (WebNavigationFailure?) -> Unit,
    val onRendererGone: (WebView) -> Unit,
    val onHistoryChanged: (WebView, WebHistoryState) -> Unit = { _, _ -> },
    val isActive: () -> Boolean = { true },
    val pendingNavigationUrl: String? = null,
)

@Immutable
internal data class WebContentCallbacks(
    val onWebViewReady: (WebView?) -> Unit,
    val onWebViewReleased: (WebView) -> Unit = { onWebViewReady(null) },
    val onHistoryChanged: (WebView, WebHistoryState) -> Unit = { _, _ -> },
    val consumedNavigationRequestId: (Long) -> Long = { 0L },
    val onNavigationRequestConsumed: (Long, Long) -> Unit = { _, _ -> },
    val onPageStarted: (Long, String?, String?, Long) -> Unit,
    val onPageFinished: (Long, String?, String?, Long) -> Unit,
    val onPageChanged: (Long, String?, String?) -> Unit,
    val onMediaRequest: (Long, Long, MediaResourceRequest) -> Unit,
    val onSaveWebState: (Long, Bundle) -> Unit,
    val onScan: () -> Unit,
    val onOpenDetectedMedia: () -> Unit,
    val onOpenExternal: (String) -> Unit,
)
