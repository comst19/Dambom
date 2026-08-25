package com.comst19.dambom.feature.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.comst19.dambom.core.common.ui.appScaffoldPadding
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.feature.web.contract.RecentPage
import com.comst19.dambom.feature.web.contract.WebDetectionState
import com.comst19.dambom.feature.web.contract.WebTab
import com.comst19.dambom.feature.web.contract.WebUiState
import kotlinx.collections.immutable.PersistentList
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

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
        onPageChanged = viewModel::updatePage,
        onMediaRequest = viewModel::onMediaRequest,
        onDetect = viewModel::detectCurrentTab,
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
@OptIn(ExperimentalMaterial3Api::class)
internal fun WebScreen(
    uiState: WebUiState,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onPageChanged: (Long, String?, String?) -> Unit,
    onMediaRequest: (Long, String) -> Unit,
    onDetect: () -> Unit,
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .appScaffoldPadding(),
    ) {
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
            key(currentTab.id) {
                WebContent(
                    tab = currentTab,
                    savedState = savedWebState(currentTab.id),
                    onWebViewReady = { currentWebView = it },
                    onPageChanged = onPageChanged,
                    onMediaRequest = onMediaRequest,
                    onSaveWebState = onSaveWebState,
                    onDetect = onDetect,
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

@Composable
private fun WebAddressBar(
    address: String,
    tabCount: Int,
    onAddressChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onOpenTabs: () -> Unit,
    currentUrl: String?,
    onOpenExternal: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.web_back),
            )
        }
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.web_address_placeholder)) },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            shape = RoundedCornerShape(14.dp),
        )
        Surface(
            onClick = onOpenTabs,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(tabCount.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                enabled = currentUrl != null,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.web_tab_actions),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                WebActionMenuItem(
                    label = stringResource(R.string.web_open_external),
                    icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        currentUrl?.let(onOpenExternal)
                    },
                )
                WebActionMenuItem(
                    label = stringResource(R.string.web_copy_link),
                    icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        currentUrl?.let(onCopyLink)
                    },
                )
                WebActionMenuItem(
                    label = stringResource(R.string.web_share_link),
                    icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        currentUrl?.let(onShareLink)
                    },
                )
            }
        }
    }
}

@Composable
private fun WebActionMenuItem(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = icon,
    )
}

@Composable
private fun WebStartPage(
    address: String,
    recentPages: PersistentList<RecentPage>,
    onNavigate: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.web_start_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.web_start_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onNavigate(address) },
            enabled = address.isNotBlank(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.web_open))
        }
        Spacer(Modifier.height(34.dp))
        Text(
            text = stringResource(R.string.web_recent),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(10.dp))
        if (recentPages.isEmpty()) {
            Text(
                stringResource(R.string.web_recent_empty),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            recentPages.forEach { page ->
                RecentPageRow(page = page, onClick = { onNavigate(page.url) })
            }
        }
    }
}

@Composable
private fun RecentPageRow(
    page: RecentPage,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
    ) {
        Text(page.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            page.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ColumnScope.WebContent(
    tab: WebTab,
    savedState: Bundle?,
    onWebViewReady: (WebView?) -> Unit,
    onPageChanged: (Long, String?, String?) -> Unit,
    onMediaRequest: (Long, String) -> Unit,
    onSaveWebState: (Long, Bundle) -> Unit,
    onDetect: () -> Unit,
) {
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
        AndroidView(
            factory = { context ->
                createWebView(
                    context = context,
                    tab = tab,
                    savedState = savedState,
                    onPageChanged = onPageChanged,
                    onMediaRequest = onMediaRequest,
                    onProgress = { loadingProgress = it },
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds(),
            update = { view ->
                val targetUrl = tab.url
                if (targetUrl != null && view.url != targetUrl) view.loadUrl(targetUrl)
            },
        )

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
        onDetect = onDetect,
    )
}

@Composable
private fun WebToolbar(
    webView: WebView?,
    detectionState: WebDetectionState,
    onDetect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.web_go_back))
        }
        IconButton(onClick = { webView?.goForward() }, enabled = webView?.canGoForward() == true) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.web_go_forward))
        }
        IconButton(onClick = { webView?.reload() }) {
            Icon(Icons.Outlined.Refresh, stringResource(R.string.web_refresh))
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onDetect,
            enabled = detectionState !is WebDetectionState.Scanning,
        ) {
            Text(detectionState.label())
        }
    }
}

@Composable
private fun WebDetectionState.label(): String =
    when (this) {
        WebDetectionState.Idle -> stringResource(R.string.web_detect)
        WebDetectionState.Scanning -> stringResource(R.string.web_detecting)
        is WebDetectionState.Found -> stringResource(R.string.web_detected_count, count)
        is WebDetectionState.NotFound -> reason.label()
    }

@Composable
private fun UnsupportedReason.label(): String =
    when (this) {
        UnsupportedReason.ACCESS_RESTRICTED -> stringResource(R.string.web_access_restricted)
        UnsupportedReason.NETWORK_ERROR -> stringResource(R.string.web_network_error)
        else -> stringResource(R.string.web_not_detected)
    }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WebTabsSheet(
    state: WebUiState,
    onDismiss: () -> Unit,
    onCreateTab: () -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.web_tabs_title, state.tabs.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onCreateTab) {
                Icon(Icons.Outlined.Add, stringResource(R.string.web_new_tab))
            }
        }
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
        ) {
            items(state.tabs, key = WebTab::id) { tab ->
                val selected = tab.id == state.currentTabId
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onSelectTab(tab.id) },
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                        ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                tab.url ?: stringResource(R.string.web_empty_tab),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onCloseTab(tab.id) }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.web_close_tab))
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: android.content.Context,
    tab: WebTab,
    savedState: Bundle?,
    onPageChanged: (Long, String?, String?) -> Unit,
    onMediaRequest: (Long, String) -> Unit,
    onProgress: (Int) -> Unit,
    onRendererGone: () -> Unit,
): WebView =
    WebView(context).apply {
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
                    onPageChanged(tab.id, url, view?.title)
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?,
                ) {
                    onPageChanged(tab.id, url, view?.title)
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    onMediaRequest(tab.id, url)
                    return if (shouldBlockWebVideo(url, mediaGuardInstalled)) blockedVideoResponse() else null
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

internal fun shouldBlockWebVideo(
    url: String,
    mediaGuardInstalled: Boolean,
): Boolean =
    !mediaGuardInstalled &&
        WEB_VIDEO_EXTENSIONS.any { extension -> url.substringBefore('?').endsWith(extension, ignoreCase = true) }

private fun blockedVideoResponse() =
    WebResourceResponse(
        "video/mp4",
        null,
        204,
        "No Content",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0)),
    )

private val WEB_VIDEO_EXTENSIONS = setOf(".mp4", ".webm", ".mov", ".m4v")

internal const val WEB_MEDIA_GUARD_SCRIPT =
    """
    (() => {
      if (window.__dambomMediaGuard) return;
      window.__dambomMediaGuard = true;

      const seekToFirstFrame = (video) => {
        const seek = Number.isFinite(video.duration) ? Math.min(0.1, video.duration / 100) : 0.05;
        try { video.currentTime = seek; } catch (_) {}
      };

      HTMLMediaElement.prototype.play = function() {
        this.autoplay = false;
        this.removeAttribute('autoplay');
        this.preload = 'metadata';
        if (this.readyState >= 1) {
          seekToFirstFrame(this);
        } else {
          this.addEventListener('loadedmetadata', () => seekToFirstFrame(this), { once: true });
        }
        return Promise.resolve();
      };

      const visibility = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          const video = entry.target;
          if (entry.isIntersecting) {
            video.preload = 'metadata';
            if (video.readyState >= 1) {
              seekToFirstFrame(video);
            } else {
              video.addEventListener('loadedmetadata', () => seekToFirstFrame(video), { once: true });
              video.load();
            }
          } else {
            video.pause();
            video.preload = 'none';
          }
        });
      }, { rootMargin: '320px 0px' });

      const prepare = (video) => {
        if (video.dataset.dambomGuarded) return;
        video.dataset.dambomGuarded = 'true';
        video.autoplay = false;
        video.removeAttribute('autoplay');
        video.preload = 'none';
        visibility.observe(video);
      };

      const scan = (root) => {
        if (root instanceof HTMLVideoElement) prepare(root);
        if (root.querySelectorAll) root.querySelectorAll('video').forEach(prepare);
      };

      document.addEventListener('DOMContentLoaded', () => scan(document), { once: true });
      new MutationObserver((mutations) => {
        mutations.forEach((mutation) => mutation.addedNodes.forEach(scan));
      }).observe(document, { childList: true, subtree: true });
    })();
    """

private fun Context.openExternal(
    url: String,
    failureMessage: String,
) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

private fun Context.copyLink(
    url: String,
    copiedMessage: String,
) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("URL", url))
    Toast.makeText(this, copiedMessage, Toast.LENGTH_SHORT).show()
}

private fun Context.shareLink(
    url: String,
    failureMessage: String,
) {
    val intent =
        Intent
            .createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, url),
                getString(R.string.web_share_chooser),
            )
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}
