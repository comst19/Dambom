package com.comst19.dambom.feature.web.webview

import android.os.Bundle
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.comst19.dambom.core.designsystem.DambomLinearProgressIndicator
import com.comst19.dambom.feature.web.component.WebNavigationErrorContent
import com.comst19.dambom.feature.web.component.WebRescanButton
import com.comst19.dambom.feature.web.component.WebToolbar
import com.comst19.dambom.feature.web.contract.WebDetectionState
import com.comst19.dambom.feature.web.contract.WebHistoryState
import com.comst19.dambom.feature.web.contract.WebTab
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Composable
internal fun ColumnScope.WebContent(
    tab: WebTab,
    navigationFailureState: MutableState<WebNavigationFailure?>,
    savedState: Bundle?,
    callbacks: WebContentCallbacks,
    historyState: WebHistoryState = WebHistoryState(),
) {
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    var webView by remember(tab.id) { mutableStateOf<WebView?>(null) }
    val activeWebView = remember(tab.id) { AtomicReference<WebView?>(null) }
    val lastConsumedNavigationRequestId =
        remember(tab.id) { AtomicLong(callbacks.consumedNavigationRequestId(tab.id)) }
    val loadingProgress = remember(tab.id) { mutableIntStateOf(0) }
    var webViewGeneration by remember(tab.id) { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { webView?.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { webView?.onPause() }

    WebLoadingProgress(loadingProgress, navigationFailureState)
    key(webViewGeneration) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
        ) {
            WebViewSurface(
                tab = tab,
                savedState = savedState,
                backgroundColor = backgroundColor,
                runtimeState =
                    WebViewRuntimeState(
                        activeWebView = activeWebView,
                        lastConsumedNavigationRequestId = lastConsumedNavigationRequestId,
                        loadingProgress = loadingProgress,
                        navigationFailureState = navigationFailureState,
                        onWebViewChanged = { webView = it },
                        onReplaceWebView = { webViewGeneration += 1 },
                    ),
                callbacks = callbacks,
            )
            navigationFailureState.value?.let {
                WebNavigationErrorContent(
                    failure = it,
                    onRetry = {
                        navigationFailureState.value = null
                        webView?.reload()
                    },
                    onOpenExternal = { tab.url?.let(callbacks.onOpenExternal) },
                )
            }
            if (navigationFailureState.value == null && tab.detectionState !is WebDetectionState.Scanning) {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    WebRescanButton(detectionState = tab.detectionState, onScan = callbacks.onScan)
                }
            }
        }
    }
    WebToolbar(
        webView = webView,
        canRefresh = navigationFailureState.value?.retryable != false,
        detectionState = tab.detectionState,
        onOpenDetectedMedia = callbacks.onOpenDetectedMedia,
        historyState = historyState,
    )
}

private data class WebViewRuntimeState(
    val activeWebView: AtomicReference<WebView?>,
    val lastConsumedNavigationRequestId: AtomicLong,
    val loadingProgress: MutableIntState,
    val navigationFailureState: MutableState<WebNavigationFailure?>,
    val onWebViewChanged: (WebView?) -> Unit,
    val onReplaceWebView: () -> Unit,
)

private data class WebViewSurfaceFlags(
    val released: AtomicBoolean = AtomicBoolean(false),
    val rendererGone: AtomicBoolean = AtomicBoolean(false),
)

@Composable
private fun WebViewSurface(
    tab: WebTab,
    savedState: Bundle?,
    backgroundColor: Int,
    runtimeState: WebViewRuntimeState,
    callbacks: WebContentCallbacks,
) {
    val flags = remember { WebViewSurfaceFlags() }
    AndroidView(
        factory = { context ->
            val createdRef = AtomicReference<WebView?>(null)
            val pendingRequest =
                tab.navigationRequest?.takeIf {
                    it.id != runtimeState.lastConsumedNavigationRequestId.get()
                }
            createWebView(
                context = context,
                tab = tab,
                savedState = savedState,
                callbacks =
                    buildWebViewCallbacks(
                        createdRef = createdRef,
                        flags = flags,
                        runtimeState = runtimeState,
                        callbacks = callbacks,
                        pendingNavigationUrl = pendingRequest?.url,
                    ),
            ).also {
                if (pendingRequest != null) {
                    runtimeState.lastConsumedNavigationRequestId.set(pendingRequest.id)
                    callbacks.onNavigationRequestConsumed(tab.id, pendingRequest.id)
                }
                createdRef.set(it)
                runtimeState.activeWebView.set(it)
                it.setBackgroundColor(backgroundColor)
                runtimeState.onWebViewChanged(it)
                callbacks.onWebViewReady(it)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { releasedWebView ->
            releaseWebView(tab.id, releasedWebView, flags, runtimeState, callbacks)
        },
        update = { view ->
            view.setBackgroundColor(backgroundColor)
            val request = tab.navigationRequest
            if (
                request != null &&
                runtimeState.lastConsumedNavigationRequestId.getAndSet(request.id) != request.id
            ) {
                callbacks.onNavigationRequestConsumed(tab.id, request.id)
                view.loadUrl(request.url)
            }
        },
    )
}

private fun buildWebViewCallbacks(
    createdRef: AtomicReference<WebView?>,
    flags: WebViewSurfaceFlags,
    runtimeState: WebViewRuntimeState,
    callbacks: WebContentCallbacks,
    pendingNavigationUrl: String?,
): WebViewCallbacks =
    WebViewCallbacks(
        onPageStarted = { id, url, title, generation ->
            runtimeState.loadingProgress.intValue = 0
            callbacks.onPageStarted(id, url, title, generation)
        },
        onPageFinished = callbacks.onPageFinished,
        onPageChanged = callbacks.onPageChanged,
        onMediaRequest = callbacks.onMediaRequest,
        onProgress = { runtimeState.loadingProgress.intValue = it },
        onNavigationFailure = { runtimeState.navigationFailureState.value = it },
        onRendererGone = { failedWebView ->
            flags.rendererGone.set(true)
            flags.released.set(true)
            if (runtimeState.activeWebView.compareAndSet(failedWebView, null)) {
                runtimeState.onWebViewChanged(null)
                callbacks.onWebViewReleased(failedWebView)
                runtimeState.onReplaceWebView()
            }
        },
        onHistoryChanged = callbacks.onHistoryChanged,
        isActive = {
            val created = createdRef.get()
            !flags.released.get() &&
                !flags.rendererGone.get() &&
                (created == null || runtimeState.activeWebView.get() === created)
        },
        pendingNavigationUrl = pendingNavigationUrl,
    )

private fun releaseWebView(
    tabId: Long,
    releasedWebView: WebView,
    flags: WebViewSurfaceFlags,
    runtimeState: WebViewRuntimeState,
    callbacks: WebContentCallbacks,
) {
    flags.released.set(true)
    if (!flags.rendererGone.get()) {
        val state = Bundle()
        releasedWebView.saveState(state)
        callbacks.onSaveWebState(tabId, state)
    }
    if (runtimeState.activeWebView.compareAndSet(releasedWebView, null)) {
        runtimeState.onWebViewChanged(null)
        callbacks.onWebViewReleased(releasedWebView)
    }
    releasedWebView.stopLoading()
    releasedWebView.destroy()
}

@Composable
private fun WebLoadingProgress(
    progress: MutableIntState,
    navigationFailure: State<WebNavigationFailure?>,
) {
    if (navigationFailure.value == null && progress.intValue in 1..99) {
        DambomLinearProgressIndicator(
            progress = progress.intValue / WEB_PROGRESS_SCALE,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val WEB_PROGRESS_SCALE = 100f
