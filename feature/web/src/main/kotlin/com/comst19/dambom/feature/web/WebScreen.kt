package com.comst19.dambom.feature.web

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.comst19.dambom.feature.web.contract.WebUiState
import com.comst19.dambom.feature.web.webview.WebContent

@Composable
internal fun WebRoute(
    initialUrl: String?,
    viewModel: WebViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.web_link_copied)
    val actionFailureMessage = stringResource(R.string.web_action_failure)
    LaunchedEffect(initialUrl) { viewModel.applyInitialUrl(initialUrl) }
    WebScreen(
        uiState = uiState,
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
        onCloseTab = viewModel::closeTab,
        savedWebState = viewModel::savedWebState,
        onSaveWebState = viewModel::saveWebState,
        onOpenExternal = { url -> context.openExternal(url, actionFailureMessage) },
        onCopyLink = { url -> context.copyLink(url, copiedMessage) },
        onShareLink = { url -> context.shareLink(url, actionFailureMessage) },
    )
}

@Composable
internal fun WebScreen(
    uiState: WebUiState,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onPageStarted: (Long, String?, String?, Long) -> Unit,
    onPageFinished: (Long, String?, String?, Long) -> Unit,
    onPageChanged: (Long, String?, String?) -> Unit,
    onMediaRequest: (Long, Long, String) -> Unit,
    onScan: () -> Unit,
    onOpenDetectedMedia: () -> Unit,
    onCreateTab: (String?) -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    savedWebState: (Long) -> Bundle?,
    onSaveWebState: (Long, Bundle) -> Unit,
    onOpenExternal: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
) {
    val currentTab = uiState.currentTab ?: return
    var showTabs by rememberSaveable { mutableStateOf(false) }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    var address by rememberSaveable(currentTab.id) { mutableStateOf(currentTab.url.orEmpty()) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(currentTab.url) {
        if (currentTab.url != null) address = currentTab.url
    }
    BackHandler {
        if (currentWebView?.canGoBack() == true) {
            currentWebView?.goBack()
        } else {
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize().appScaffoldPadding()) {
        WebAddressBar(
            address = address,
            tabCount = uiState.tabs.size,
            onAddressChange = { address = it },
            onSubmit = {
                focusManager.clearFocus()
                onNavigate(address)
            },
            onBack = onBack,
            onOpenTabs = { showTabs = true },
            currentUrl = currentTab.url,
            onOpenExternal = onOpenExternal,
            onCopyLink = onCopyLink,
            onShareLink = onShareLink,
        )
        if (currentTab.url == null) {
            WebStartPage(
                address = address,
                recentPages = uiState.recentPages,
                onNavigate = onNavigate,
            )
        } else {
            androidx.compose.runtime.key(currentTab.id) {
                WebContent(
                    tab = currentTab,
                    savedState = savedWebState(currentTab.id),
                    onWebViewReady = { currentWebView = it },
                    onPageStarted = onPageStarted,
                    onPageFinished = onPageFinished,
                    onPageChanged = onPageChanged,
                    onMediaRequest = onMediaRequest,
                    onSaveWebState = onSaveWebState,
                    onScan = onScan,
                    onOpenDetectedMedia = onOpenDetectedMedia,
                    onOpenExternal = onOpenExternal,
                )
            }
        }
    }

    if (showTabs) {
        WebTabsSheet(
            state = uiState,
            onDismiss = { showTabs = false },
            onCreateTab = {
                onCreateTab(null)
                showTabs = false
            },
            onSelectTab = {
                onSelectTab(it)
                showTabs = false
            },
            onCloseTab = onCloseTab,
        )
    }
}
