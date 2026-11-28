package com.comst19.dambom.feature.web

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.appScaffoldPadding
import com.comst19.dambom.feature.web.component.WebAddressBar
import com.comst19.dambom.feature.web.component.WebStartPage
import com.comst19.dambom.feature.web.component.WebTabsSheet
import com.comst19.dambom.feature.web.contract.WebHistoryState
import com.comst19.dambom.feature.web.contract.WebTab
import com.comst19.dambom.feature.web.contract.WebUiState
import com.comst19.dambom.feature.web.webview.WebContent
import com.comst19.dambom.feature.web.webview.WebContentCallbacks
import com.comst19.dambom.feature.web.webview.WebNavigationFailure
import com.comst19.dambom.feature.web.webview.WebViewStateHost
import com.comst19.dambom.feature.web.webview.historyState

@Composable
internal fun WebRoute(
    initialUrl: String?,
    sharedLink: Boolean = false,
    viewModel: WebViewModel = hiltViewModel(),
    stateHost: WebViewStateHost = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.web_link_copied)
    val actionFailureMessage = stringResource(R.string.web_action_failure)
    LaunchedEffect(initialUrl) { viewModel.applyInitialUrl(initialUrl, sharedLink) }
    WebScreen(
        uiState = uiState,
        callbacks =
            WebScreenCallbacks(
                onBack = viewModel::goBack,
                onNavigate = viewModel::navigateCurrentTab,
                onPageStarted = viewModel::onPageStarted,
                onPageFinished = viewModel::onPageFinished,
                onPageChanged = viewModel::updatePage,
                onMediaRequest = viewModel::onMediaRequest,
                onScan = viewModel::scanCurrentTab,
                onOpenDetectedMedia = viewModel::openDetectedMedia,
                onCreateTab = viewModel::createTab,
                onSelectTab = viewModel::selectTab,
                onCloseTab = { tabId ->
                    stateHost.removeWebState(tabId)
                    viewModel.closeTab(tabId)
                },
                savedWebState = stateHost::savedWebState,
                onSaveWebState = { tabId, state ->
                    val openTabs = viewModel.uiState.value.tabs
                    if (openTabs.any { it.id == tabId }) {
                        stateHost.saveWebState(tabId, state)
                    }
                },
                consumedNavigationRequestId = viewModel::consumedNavigationRequestId,
                onNavigationRequestConsumed = viewModel::markNavigationRequestConsumed,
                onOpenExternal = { url -> context.openExternal(url, actionFailureMessage) },
                onCopyLink = { url -> context.copyLink(url, copiedMessage) },
                onShareLink = { url -> context.shareLink(url, actionFailureMessage) },
            ),
    )
}

@Composable
internal fun WebScreen(
    uiState: WebUiState,
    callbacks: WebScreenCallbacks,
) {
    val currentTab = uiState.currentTab ?: return
    var showTabs by rememberSaveable { mutableStateOf(false) }
    val webViewState = remember(currentTab.id) { mutableStateOf<WebView?>(null) }
    val historyState = remember(currentTab.id) { mutableStateOf(WebHistoryState()) }
    val navigationFailureState = remember(currentTab.id) { mutableStateOf<WebNavigationFailure?>(null) }
    var address by rememberSaveable(currentTab.id) { mutableStateOf(currentTab.url.orEmpty()) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(currentTab.url) {
        if (currentTab.url != null) address = currentTab.url
    }
    val canGoBack = currentTab.url != null && webViewState.value != null && historyState.value.canGoBack
    BackHandler(enabled = canGoBack) { webViewState.value?.goBack() }

    Column(modifier = Modifier.fillMaxSize().appScaffoldPadding()) {
        WebAddressBar(
            address = address,
            tabCount = uiState.tabs.size,
            onAddressChange = { address = it },
            onSubmit = {
                focusManager.clearFocus()
                navigationFailureState.value = null
                callbacks.onNavigate(address)
            },
            onBack = callbacks.onBack,
            onOpenTabs = { showTabs = true },
            currentUrl = currentTab.url.takeIf { navigationFailureState.value?.canOpenExternal != false },
            onOpenExternal = callbacks.onOpenExternal,
            onCopyLink = callbacks.onCopyLink,
            onShareLink = callbacks.onShareLink,
        )
        if (currentTab.url == null) {
            WebStartPage(address = address, recentPages = uiState.recentPages, onNavigate = callbacks.onNavigate)
        } else {
            WebTabContent(currentTab, webViewState, historyState, navigationFailureState, callbacks)
        }
    }

    if (showTabs) {
        WebTabsSheet(
            state = uiState,
            onDismiss = { showTabs = false },
            onCreateTab = {
                callbacks.onCreateTab(null)
                showTabs = false
            },
            onSelectTab = {
                callbacks.onSelectTab(it)
                showTabs = false
            },
            onCloseTab = callbacks.onCloseTab,
        )
    }
}

@Composable
private fun ColumnScope.WebTabContent(
    tab: WebTab,
    webViewState: MutableState<WebView?>,
    historyState: MutableState<WebHistoryState>,
    navigationFailureState: MutableState<WebNavigationFailure?>,
    callbacks: WebScreenCallbacks,
) {
    key(tab.id) {
        WebContent(
            tab = tab,
            navigationFailureState = navigationFailureState,
            savedState = callbacks.savedWebState(tab.id),
            historyState = historyState.value,
            callbacks =
                WebContentCallbacks(
                    onWebViewReady = { readyWebView ->
                        webViewState.value = readyWebView
                        historyState.value = readyWebView?.historyState() ?: WebHistoryState()
                    },
                    onWebViewReleased = { releasedWebView ->
                        if (webViewState.value === releasedWebView) {
                            webViewState.value = null
                            historyState.value = WebHistoryState()
                        }
                    },
                    onHistoryChanged = { sourceWebView, updatedHistory ->
                        if (webViewState.value === sourceWebView) {
                            historyState.value = updatedHistory
                        }
                    },
                    consumedNavigationRequestId = callbacks.consumedNavigationRequestId,
                    onNavigationRequestConsumed = callbacks.onNavigationRequestConsumed,
                    onPageStarted = callbacks.onPageStarted,
                    onPageFinished = callbacks.onPageFinished,
                    onPageChanged = callbacks.onPageChanged,
                    onMediaRequest = callbacks.onMediaRequest,
                    onSaveWebState = callbacks.onSaveWebState,
                    onScan = callbacks.onScan,
                    onOpenDetectedMedia = callbacks.onOpenDetectedMedia,
                    onOpenExternal = callbacks.onOpenExternal,
                ),
        )
    }
}
