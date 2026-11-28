package com.comst19.dambom.feature.web

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.domain.repository.MediaDetectionSnapshots
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.detection.api.DetectionResultKey
import com.comst19.dambom.feature.web.contract.WebDetectionState
import com.comst19.dambom.feature.web.contract.WebHistoryState
import com.comst19.dambom.feature.web.contract.WebNavigationRequest
import com.comst19.dambom.feature.web.contract.WebTab
import com.comst19.dambom.feature.web.contract.WebUiState
import com.comst19.dambom.feature.web.webview.WebContent
import com.comst19.dambom.feature.web.webview.WebContentCallbacks
import com.comst19.dambom.feature.web.webview.WebNavigationFailure
import com.comst19.dambom.feature.web.webview.WebViewStateHost
import com.comst19.dambom.feature.web.webview.createWebView
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class WebBackNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @SuppressLint("VisibleForTests")
    fun `history state drives toolbar buttons and conditional predictive back`() {
        val parentBack = RecordingPredictiveBackCallback()
        composeRule.activity.onBackPressedDispatcher.addCallback(composeRule.activity, parentBack)
        val uiState =
            mutableStateOf(
                WebUiState(
                    tabs = persistentListOf(WebTab(id = 1L, url = "https://example.com/a")),
                    currentTabId = 1L,
                ),
            )
        setWebScreenContent(uiState)
        val webView = requireWebView()
        backButton().assertIsNotEnabled()
        forwardButton().assertIsNotEnabled()

        simulateSpaNavigation(webView, "https://example.com/a", "https://example.com/b")
        assertEquals("https://example.com/b", uiState.value.currentTab?.url)
        backButton().assertIsEnabled()
        forwardButton().assertIsNotEnabled()

        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            webView.webViewClient.doUpdateVisitedHistory(webView, "https://example.com/a", false)
        }
        composeRule.waitForIdle()

        assertEquals(1, shadowOf(webView).goBackInvocations)
        assertEquals(0, parentBack.startedCount)
        assertEquals(0, parentBack.pressedCount)
        assertEquals("https://example.com/a", uiState.value.currentTab?.url)
        backButton().assertIsNotEnabled()
        forwardButton().assertIsEnabled()

        dispatchPredictiveBack(cancel = true)
        assertEquals(1, parentBack.startedCount)
        assertTrue(parentBack.progressedCount >= 1)
        assertEquals(1, parentBack.cancelledCount)
        assertEquals(0, parentBack.pressedCount)
        assertEquals("https://example.com/a", uiState.value.currentTab?.url)
        forwardButton().assertIsEnabled()

        dispatchPredictiveBack(cancel = false)
        assertEquals(1, parentBack.pressedCount)
    }

    @Test
    fun `late callbacks from released tab WebView do not overwrite active tab history or url`() {
        val parentBack = RecordingPredictiveBackCallback()
        composeRule.activity.onBackPressedDispatcher.addCallback(composeRule.activity, parentBack)
        val pageUpdates = mutableListOf<Pair<Long, String?>>()
        val uiState =
            mutableStateOf(
                WebUiState(
                    tabs =
                        persistentListOf(
                            WebTab(id = 1L, url = "https://example.com/tab1-a"),
                            WebTab(id = 2L, url = "https://example.com/tab2-a"),
                        ),
                    currentTabId = 1L,
                ),
            )
        setWebScreenContent(uiState) { tabId, url -> pageUpdates.add(tabId to url) }
        val tab1WebView = requireWebView()
        simulateSpaNavigation(tab1WebView, "https://example.com/tab1-a", "https://example.com/tab1-b")
        backButton().assertIsEnabled()

        composeRule.runOnIdle { uiState.value = uiState.value.copy(currentTabId = 2L) }
        composeRule.waitForIdle()
        val tab2WebView = requireWebView()
        assertNotSame(tab1WebView, tab2WebView)
        backButton().assertIsNotEnabled()

        pageUpdates.clear()
        composeRule.runOnIdle {
            tab1WebView.webViewClient.doUpdateVisitedHistory(tab1WebView, "https://example.com/tab1-late", false)
            tab1WebView.webViewClient.onPageFinished(tab1WebView, "https://example.com/tab1-late")
        }
        composeRule.waitForIdle()

        assertTrue(pageUpdates.isEmpty())
        backButton().assertIsNotEnabled()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, parentBack.pressedCount)
    }

    @Test
    fun `renderer loss resets history state and ignores late callbacks from failed WebView`() {
        val uiState =
            mutableStateOf(
                WebUiState(
                    tabs = persistentListOf(WebTab(id = 1L, url = "https://example.com/a")),
                    currentTabId = 1L,
                ),
            )
        setWebScreenContent(uiState)
        val failedWebView = requireWebView()
        simulateSpaNavigation(failedWebView, "https://example.com/a", "https://example.com/b")
        backButton().assertIsEnabled()

        composeRule.runOnIdle {
            failedWebView.webViewClient.onRenderProcessGone(failedWebView, null)
        }
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            findWebView(composeRule.activity.window.decorView) !== failedWebView
        }
        backButton().assertIsNotEnabled()

        composeRule.runOnIdle {
            failedWebView.webViewClient.doUpdateVisitedHistory(failedWebView, "https://example.com/stale", false)
        }
        composeRule.waitForIdle()
        backButton().assertIsNotEnabled()
    }

    @Test
    fun `recomposition before delayed spa url reaches tab does not reload old url`() {
        var tab by mutableStateOf(WebTab(id = 1L, title = "Page A", url = "https://example.com/a"))
        var historyState by mutableStateOf(WebHistoryState())
        var webView: WebView? = null

        composeRule.setContent {
            DambomTheme {
                val failureState = remember { mutableStateOf<WebNavigationFailure?>(null) }
                Column {
                    WebContent(
                        tab = tab,
                        navigationFailureState = failureState,
                        savedState = null,
                        historyState = historyState,
                        callbacks =
                            WebContentCallbacks(
                                onWebViewReady = { webView = it },
                                onHistoryChanged = { _, updated -> historyState = updated },
                                onPageStarted = { _, _, _, _ -> },
                                onPageFinished = { _, _, _, _ -> },
                                onPageChanged = { _, _, _ -> },
                                onMediaRequest = { _, _, _ -> },
                                onSaveWebState = { _, _ -> },
                                onScan = {},
                                onOpenDetectedMedia = {},
                                onOpenExternal = {},
                            ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val view = requireNotNull(webView)
        assertEquals("https://example.com/a", shadowOf(view).lastLoadedUrl)

        // Advance WebView history via SPA pushState while tab.url still holds /a.
        simulateSpaNavigation(view, "https://example.com/a", "https://example.com/b")

        // Force AndroidView.update recomposition via a title update before tab.url catches up.
        composeRule.runOnIdle { tab = tab.copy(title = "Page B") }
        composeRule.waitForIdle()
        assertEquals("https://example.com/b", view.url)
        assertEquals("https://example.com/a", shadowOf(view).lastLoadedUrl)
        assertTrue(historyState.canGoBack)

        // Delayed propagation of the observed /b URL must also not trigger loadUrl.
        composeRule.runOnIdle { tab = tab.copy(url = "https://example.com/b") }
        composeRule.waitForIdle()
        assertEquals("https://example.com/b", view.url)
        assertEquals("https://example.com/a", shadowOf(view).lastLoadedUrl)

        // Explicit external navigation (WebNavigationRequest) still triggers loadUrl().
        composeRule.runOnIdle {
            tab =
                tab.copy(
                    url = "https://example.com/c",
                    navigationRequest = WebNavigationRequest(id = 1L, url = "https://example.com/c"),
                )
        }
        composeRule.waitForIdle()
        assertEquals("https://example.com/c", shadowOf(view).lastLoadedUrl)
        assertEquals("https://example.com/c", view.url)
    }

    @Test
    fun `explicit navigation loads requested url even when prior history callback runs before update`() {
        val viewModel =
            WebViewModel(
                mediaDetectionRepository = NoOpMediaDetectionRepository,
                navigation = SpyNavigationDispatcher(),
                savedStateHandle = SavedStateHandle(),
            )
        viewModel.navigateCurrentTab("https://example.com/a")
        setRealViewModelContent(viewModel)
        val webView = requireWebView()
        assertEquals("https://example.com/a", shadowOf(webView).lastLoadedUrl)

        // Submit /c and fire a queued /b visited-history callback in the same UI turn before update runs.
        composeRule.runOnIdle {
            viewModel.navigateCurrentTab("https://example.com/c")
            shadowOf(webView).pushEntryToHistory("https://example.com/b")
            webView.webViewClient.doUpdateVisitedHistory(webView, "https://example.com/b", false)
        }
        composeRule.waitForIdle()

        assertEquals("https://example.com/c", shadowOf(webView).lastLoadedUrl)
        assertEquals("https://example.com/c", webView.url)
    }

    @Test
    fun `unconsumed explicit navigation is loaded by replacement WebView after renderer loss`() {
        val viewModel =
            WebViewModel(
                mediaDetectionRepository = NoOpMediaDetectionRepository,
                navigation = SpyNavigationDispatcher(),
                savedStateHandle = SavedStateHandle(),
            )
        viewModel.navigateCurrentTab("https://example.com/a")
        setRealViewModelContent(viewModel)
        val failedWebView = requireWebView()
        assertEquals("https://example.com/a", shadowOf(failedWebView).lastLoadedUrl)

        composeRule.runOnIdle {
            viewModel.navigateCurrentTab("https://example.com/c")
            failedWebView.webViewClient.onRenderProcessGone(failedWebView, null)
        }
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            findWebView(composeRule.activity.window.decorView) !== failedWebView
        }
        composeRule.waitForIdle()

        val replacementWebView = requireWebView()
        assertNotSame(failedWebView, replacementWebView)
        assertEquals("https://example.com/c", shadowOf(replacementWebView).lastLoadedUrl)
        assertEquals("https://example.com/c", replacementWebView.url)
    }

    @Test
    fun `same-document history change advances generation for real ViewModel media detection`() {
        val navigation = SpyNavigationDispatcher()
        val snapshots = MediaDetectionSnapshots()
        val viewModel = WebViewModel(NoOpMediaDetectionRepository, navigation, SavedStateHandle(), snapshots)
        viewModel.navigateCurrentTab("https://example.com/a")
        val tab = requireNotNull(viewModel.uiState.value.currentTab)
        val startedGenerations = mutableListOf<Long>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView =
            createWebView(
                context = context,
                tab = tab,
                savedState = null,
                onPageStarted = { tabId, url, title, generation ->
                    startedGenerations.add(generation)
                    viewModel.onPageStarted(tabId, url, title, generation)
                },
                onPageFinished = viewModel::onPageFinished,
                onPageChanged = viewModel::updatePage,
                onMediaRequest = viewModel::onMediaRequest,
                onProgress = {},
                onNavigationFailure = {},
                onRendererGone = {},
            )
        try {
            webView.webViewClient.onPageStarted(webView, "https://example.com/a", null)
            webView.webViewClient.onPageFinished(webView, "https://example.com/a")
            webView.webViewClient.shouldInterceptRequest(webView, GetResourceRequest("https://cdn.example.com/a.mp4"))
            shadowOf(Looper.getMainLooper()).idle()
            val tabOnA = requireNotNull(viewModel.uiState.value.currentTab)
            assertEquals(WebDetectionState.Found(1), tabOnA.detectionState)
            val generationOnA = startedGenerations.single()

            // Move /a -> /b in the same document via pushState (doUpdateVisitedHistory).
            shadowOf(webView).pushEntryToHistory("https://example.com/a")
            shadowOf(webView).pushEntryToHistory("https://example.com/b")
            webView.webViewClient.doUpdateVisitedHistory(webView, "https://example.com/b", false)
            shadowOf(Looper.getMainLooper()).idle()

            val tabOnB = requireNotNull(viewModel.uiState.value.currentTab)
            assertEquals("https://example.com/b", tabOnB.url)
            assertEquals(WebDetectionState.Idle, tabOnB.detectionState)

            // Late media event from /a's previous generation must be ignored.
            viewModel.onMediaRequest(tab.id, generationOnA, "https://cdn.example.com/stale-a.mp4")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(WebDetectionState.Idle, requireNotNull(viewModel.uiState.value.currentTab).detectionState)

            // New MP4 intercepted on /b must be detected under the new generation.
            webView.webViewClient.shouldInterceptRequest(webView, GetResourceRequest("https://cdn.example.com/b.mp4"))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(WebDetectionState.Found(1), requireNotNull(viewModel.uiState.value.currentTab).detectionState)

            viewModel.openDetectedMedia()
            shadowOf(Looper.getMainLooper()).idle()
            val key = (navigation.dispatched.single() as NavigationEvent.Navigate).key as DetectionResultKey
            val snapshot = snapshots.get(requireNotNull(key.snapshotId), key.url)
            assertEquals(listOf("https://cdn.example.com/b.mp4"), snapshot?.candidates?.map { it.url })
        } finally {
            webView.destroy()
        }
    }

    @Test
    fun `process death restoration does not skip first explicit navigation`() {
        val restorationTester = StateRestorationTester(composeRule)
        val initialHandle = SavedStateHandle()
        val initialViewModel =
            WebViewModel(
                mediaDetectionRepository = NoOpMediaDetectionRepository,
                navigation = SpyNavigationDispatcher(),
                savedStateHandle = initialHandle,
            )
        initialViewModel.navigateCurrentTab("https://example.com/a")
        var activeViewModel by mutableStateOf(initialViewModel)

        restorationTester.setContent {
            DambomTheme {
                val viewModel = activeViewModel
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                WebScreen(uiState = uiState, callbacks = webViewModelCallbacks(viewModel))
            }
        }
        composeRule.waitForIdle()
        val initialWebView = requireWebView()
        assertEquals("https://example.com/a", shadowOf(initialWebView).lastLoadedUrl)
        assertEquals(1L, initialViewModel.consumedNavigationRequestId(1L))

        val restoredHandle = SavedStateHandle(initialHandle.keys().associateWith { initialHandle.get<Any?>(it) })
        val restoredViewModel =
            WebViewModel(
                mediaDetectionRepository = NoOpMediaDetectionRepository,
                navigation = SpyNavigationDispatcher(),
                savedStateHandle = restoredHandle,
            )
        composeRule.runOnIdle { activeViewModel = restoredViewModel }
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        val restoredWebView = requireWebView()
        assertNotSame(initialWebView, restoredWebView)
        assertEquals("https://example.com/a", shadowOf(restoredWebView).lastLoadedUrl)
        assertEquals(0L, restoredViewModel.consumedNavigationRequestId(1L))

        composeRule.runOnIdle { restoredViewModel.navigateCurrentTab("https://example.com/c") }
        composeRule.waitForIdle()

        assertEquals("https://example.com/c", shadowOf(restoredWebView).lastLoadedUrl)
        assertEquals("https://example.com/c", restoredWebView.url)
        assertEquals(1L, restoredViewModel.consumedNavigationRequestId(1L))
    }

    @Test
    fun `state restoration with surviving ViewModel does not reload already consumed navigation`() {
        val restorationTester = StateRestorationTester(composeRule)
        val viewModel =
            WebViewModel(
                mediaDetectionRepository = NoOpMediaDetectionRepository,
                navigation = SpyNavigationDispatcher(),
                savedStateHandle = SavedStateHandle(),
            )
        viewModel.navigateCurrentTab("https://example.com/a")

        restorationTester.setContent {
            DambomTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                WebScreen(uiState = uiState, callbacks = webViewModelCallbacks(viewModel))
            }
        }
        composeRule.waitForIdle()
        val initialWebView = requireWebView()
        assertEquals("https://example.com/a", shadowOf(initialWebView).lastLoadedUrl)

        simulateSpaNavigation(initialWebView, "https://example.com/a", "https://example.com/b")
        assertEquals("https://example.com/b", requireNotNull(viewModel.uiState.value.currentTab).url)

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        val recreatedWebView = requireWebView()
        assertNotSame(initialWebView, recreatedWebView)
        assertTrue(shadowOf(recreatedWebView).lastLoadedUrl != "https://example.com/a")
        assertEquals("https://example.com/b", requireNotNull(viewModel.uiState.value.currentTab).url)
    }

    private fun setWebScreenContent(
        uiState: MutableState<WebUiState>,
        onPageUpdated: (Long, String?) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            DambomTheme {
                WebScreen(
                    uiState = uiState.value,
                    callbacks =
                        WebScreenCallbacks(
                            onPageStarted = { tabId, url, _, _ ->
                                onPageUpdated(tabId, url)
                                if (url != null) uiState.value = uiState.value.withUpdatedUrl(tabId, url)
                            },
                            onPageChanged = { tabId, url, _ ->
                                onPageUpdated(tabId, url)
                                if (url != null) uiState.value = uiState.value.withUpdatedUrl(tabId, url)
                            },
                        ),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setRealViewModelContent(viewModel: WebViewModel) {
        composeRule.setContent {
            DambomTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                WebScreen(uiState = uiState, callbacks = webViewModelCallbacks(viewModel))
            }
        }
        composeRule.waitForIdle()
    }

    private fun webViewModelCallbacks(
        viewModel: WebViewModel,
        stateHost: WebViewStateHost = WebViewStateHost(),
    ): WebScreenCallbacks =
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
        )

    private fun simulateSpaNavigation(
        webView: WebView,
        initialUrl: String,
        nextUrl: String,
    ) {
        composeRule.runOnIdle {
            shadowOf(webView).pushEntryToHistory(initialUrl)
            shadowOf(webView).pushEntryToHistory(nextUrl)
            webView.webViewClient.doUpdateVisitedHistory(webView, nextUrl, false)
        }
        composeRule.waitForIdle()
    }

    @SuppressLint("VisibleForTests")
    private fun dispatchPredictiveBack(cancel: Boolean) {
        composeRule.runOnIdle {
            val dispatcher = composeRule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(40f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
            if (cancel) {
                dispatcher.dispatchOnBackCancelled()
            } else {
                dispatcher.onBackPressed()
            }
        }
        composeRule.waitForIdle()
    }

    private fun backButton() =
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.web_go_back),
        )

    private fun forwardButton() =
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.web_go_forward),
        )

    private fun requireWebView(): WebView =
        requireNotNull(findWebView(composeRule.activity.window.decorView)) { "Expected WebView in view hierarchy" }

    private fun findWebView(view: View): WebView? =
        when (view) {
            is WebView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
            else -> null
        }
}

private fun WebUiState.withUpdatedUrl(
    tabId: Long,
    url: String,
): WebUiState {
    val index = tabs.indexOfFirst { it.id == tabId }
    if (index < 0) return this
    return copy(tabs = tabs.mutate { it[index] = it[index].copy(url = url) })
}

private class RecordingPredictiveBackCallback : OnBackPressedCallback(true) {
    var startedCount = 0
        private set
    var progressedCount = 0
        private set
    var cancelledCount = 0
        private set
    var pressedCount = 0
        private set

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        startedCount += 1
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        progressedCount += 1
    }

    override fun handleOnBackCancelled() {
        cancelledCount += 1
    }

    override fun handleOnBackPressed() {
        pressedCount += 1
    }
}

private class GetResourceRequest(
    url: String,
) : WebResourceRequest {
    private val parsedUrl: Uri = Uri.parse(url)

    override fun getUrl(): Uri = parsedUrl

    override fun isForMainFrame(): Boolean = false

    override fun isRedirect(): Boolean = false

    override fun hasGesture(): Boolean = false

    override fun getMethod(): String = "GET"

    override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
}

private object NoOpMediaDetectionRepository : MediaDetectionRepository {
    override suspend fun detect(url: String): MediaDetectionResult = noMediaResult()

    override suspend fun detectRequest(
        url: String,
        headers: Map<String, String>,
    ): MediaDetectionResult = noMediaResult()

    private fun noMediaResult(): MediaDetectionResult = MediaDetectionResult.Unsupported(UnsupportedReason.NO_MEDIA)
}
