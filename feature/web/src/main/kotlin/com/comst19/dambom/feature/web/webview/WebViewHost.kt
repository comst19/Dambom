package com.comst19.dambom.feature.web.webview

import android.os.Bundle
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.comst19.dambom.feature.web.component.WebNavigationErrorContent
import com.comst19.dambom.feature.web.component.WebRescanButton
import com.comst19.dambom.feature.web.component.WebToolbar
import com.comst19.dambom.feature.web.contract.WebDetectionState
import com.comst19.dambom.feature.web.contract.WebTab
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun ColumnScope.WebContent(
    tab: WebTab,
    navigationFailureState: MutableState<WebNavigationFailure?>,
    savedState: Bundle?,
    onWebViewReady: (WebView?) -> Unit,
    onPageStarted: (Long, String?, String?, Long) -> Unit,
    onPageFinished: (Long, String?, String?, Long) -> Unit,
    onPageChanged: (Long, String?, String?) -> Unit,
    onMediaRequest: (Long, Long, String) -> Unit,
    onSaveWebState: (Long, Bundle) -> Unit,
    onScan: () -> Unit,
    onOpenDetectedMedia: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    var webView by remember(tab.id) { mutableStateOf<WebView?>(null) }
    var loadingProgress by remember(tab.id) { mutableStateOf(0) }
    var webViewGeneration by remember(tab.id) { androidx.compose.runtime.mutableIntStateOf(0) }

    if (loadingProgress in 1..99) {
        LinearProgressIndicator(
            progress = { loadingProgress / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    key(webViewGeneration) {
        val rendererGone = remember { AtomicBoolean(false) }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
        ) {
            AndroidView(
                factory = { context ->
                    createWebView(
                        context = context,
                        tab = tab,
                        savedState = savedState,
                        onPageStarted = onPageStarted,
                        onPageFinished = onPageFinished,
                        onPageChanged = onPageChanged,
                        onMediaRequest = onMediaRequest,
                        onProgress = { loadingProgress = it },
                        onNavigationFailure = { navigationFailureState.value = it },
                        onRendererGone = { failedWebView ->
                            rendererGone.set(true)
                            if (webView === failedWebView) {
                                webView = null
                                onWebViewReady(null)
                                webViewGeneration += 1
                            }
                        },
                    ).also {
                        it.setBackgroundColor(backgroundColor)
                        webView = it
                        onWebViewReady(it)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { releasedWebView ->
                    if (!rendererGone.get()) {
                        val state = Bundle()
                        releasedWebView.saveState(state)
                        onSaveWebState(tab.id, state)
                    }
                    if (webView === releasedWebView) {
                        webView = null
                        onWebViewReady(null)
                    }
                    releasedWebView.stopLoading()
                    releasedWebView.destroy()
                },
                update = { view ->
                    view.setBackgroundColor(backgroundColor)
                    val targetUrl = tab.url
                    if (targetUrl != null && view.url != targetUrl) view.loadUrl(targetUrl)
                },
            )
            navigationFailureState.value?.let {
                WebNavigationErrorContent(
                    failure = it,
                    onRetry = {
                        navigationFailureState.value = null
                        webView?.reload()
                    },
                    onOpenExternal = { tab.url?.let(onOpenExternal) },
                )
            }
            if (navigationFailureState.value == null && tab.detectionState !is WebDetectionState.Scanning) {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    WebRescanButton(detectionState = tab.detectionState, onScan = onScan)
                }
            }
        }
    }
    WebToolbar(
        webView = webView,
        canRefresh = navigationFailureState.value?.retryable != false,
        detectionState = tab.detectionState,
        onOpenDetectedMedia = onOpenDetectedMedia,
    )
}
