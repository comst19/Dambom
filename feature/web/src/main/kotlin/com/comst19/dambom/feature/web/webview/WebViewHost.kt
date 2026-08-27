package com.comst19.dambom.feature.web.webview

import android.os.Bundle
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
    var webView by remember(tab.id) { mutableStateOf<WebView?>(null) }
    var loadingProgress by remember(tab.id) { mutableStateOf(0) }
    var navigationFailure by remember(tab.id) { mutableStateOf<WebNavigationFailure?>(null) }
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
                        onNavigationFailure = { navigationFailure = it },
                        onRendererGone = {
                            rendererGone.set(true)
                            onWebViewReady(null)
                            webViewGeneration += 1
                        },
                    ).also {
                        webView = it
                        onWebViewReady(it)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    val targetUrl = tab.url
                    if (targetUrl != null && view.url != targetUrl) view.loadUrl(targetUrl)
                },
            )
            navigationFailure?.let {
                WebNavigationErrorContent(
                    onRetry = {
                        navigationFailure = null
                        webView?.reload()
                    },
                    onOpenExternal = { tab.url?.let(onOpenExternal) },
                )
            }
            if (navigationFailure == null && tab.detectionState !is WebDetectionState.Scanning) {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    WebRescanButton(detectionState = tab.detectionState, onScan = onScan)
                }
            }
        }

        DisposableEffect(tab.id, webViewGeneration) {
            onDispose {
                webView?.let { view ->
                    if (!rendererGone.get()) {
                        val state = Bundle()
                        view.saveState(state)
                        onSaveWebState(tab.id, state)
                    }
                    view.stopLoading()
                    view.destroy()
                }
                onWebViewReady(null)
            }
        }
    }
    WebToolbar(
        webView = webView,
        detectionState = tab.detectionState,
        onOpenDetectedMedia = onOpenDetectedMedia,
    )
}
